package com.guilherme.heimdall

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/** Busca as listas, descobre o que é novo e avisa. */
object Verificador {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36"
    private const val CANAL = "novos"

    data class Resultado(val novos: Int, val erro: String?)

    /** @param emPrimeiroPlano true quando o usuário mandou atualizar (pode abrir mais notícias de uma vez) */
    suspend fun verificar(ctx: Context, emPrimeiroPlano: Boolean, progresso: (String) -> Unit = {}): Resultado =
        withContext(Dispatchers.IO) {
            val a = Armazem(ctx)
            val ufs = a.ufs
            if (ufs.isEmpty() && !a.nacional) return@withContext Resultado(0, null)
            val regioes = ufs.mapNotNull { Estados.regiaoDe(it) }.toMutableSet()
            if (a.nacional) regioes += "nacional"

            val agora = System.currentTimeMillis()
            val mapa = a.concursos()
            val conhecidas = a.ufsConhecidas
            val lidosAgora = HashSet<String>()
            val ufsLidas = HashSet<String>()
            val erros = ArrayList<String>()

            for (r in regioes) {
                progresso("Lendo ${if (r == "nacional") "concursos nacionais" else "região ${nomeRegiao(r)}"}…")
                try {
                    val doc = Jsoup.connect(Estados.urlRegiao(r)).userAgent(UA).timeout(30_000).get()
                    val lista = Leitor.lerRegiao(doc, nacional = r == "nacional")
                    if (lista.isEmpty()) { erros += "a página ${nomeRegiao(r)} veio vazia"; continue }
                    if (r == "nacional") ufsLidas += Estados.NACIONAL
                    else ufsLidas += Estados.nomes.keys.filter { Estados.regiaoDe(it) == r }
                    for (c in lista) {
                        lidosAgora += c.url
                        val velho = mapa[c.url]
                        if (velho == null) {
                            val inicial = c.uf !in conhecidas
                            mapa[c.url] = c.copy(vistoEm = agora, inicial = inicial, notificado = inicial)
                        } else {
                            // atualiza o texto (prazo prorrogado etc.) mantendo o histórico
                            mapa[c.url] = c.copy(
                                vistoEm = velho.vistoEm, juridico = velho.juridico, motivo = velho.motivo,
                                notificado = velho.notificado, inicial = velho.inicial, ativo = true,
                            )
                        }
                    }
                } catch (e: Exception) {
                    erros += "não consegui abrir ${nomeRegiao(r)} (${e.javaClass.simpleName})"
                }
            }

            // o que sumiu das listas lidas encerrou as inscrições
            for (c in mapa.values) if (c.uf in ufsLidas && c.url !in lidosAgora) c.ativo = false
            mapa.values.removeAll { !it.ativo && agora - it.vistoEm > 90L * 86_400_000 }

            // classificação jurídica: primeiro pelo texto da lista, depois abrindo a notícia
            val pendentes = ArrayList<Concurso>()
            for (c in mapa.values) {
                if (!c.ativo || c.juridico != null || !a.estaNaRegiao(c)) continue
                val m = Juridico.classificar(c.textoDaLista())
                if (m != null) { c.juridico = true; c.motivo = m } else pendentes += c
            }
            // novos primeiro, para notificar logo
            pendentes.sortWith(compareBy<Concurso> { it.notificado }.thenByDescending { it.vistoEm })
            val limite = if (emPrimeiroPlano) 400 else 60
            val fila = pendentes.take(limite)
            if (fila.isNotEmpty()) {
                var feitos = 0
                val sem = Semaphore(4)
                coroutineScope {
                    fila.map { c ->
                        async {
                            sem.withPermit {
                                try {
                                    val html = Jsoup.connect(c.url).userAgent(UA).timeout(25_000).execute().body()
                                    val m = Juridico.classificar(Leitor.textoNoticia(html))
                                    c.juridico = m != null; c.motivo = m ?: ""
                                } catch (_: Exception) { /* tenta de novo na próxima */ }
                                synchronized(this@Verificador) { feitos++ }
                                progresso("Conferindo cargos: $feitos de ${fila.size}")
                            }
                        }
                    }.awaitAll()
                }
            }

            // avisos
            val avisar = ArrayList<Pair<Concurso, String>>()
            for (c in mapa.values) {
                if (c.notificado || !c.ativo || !a.estaNaRegiao(c)) continue
                if (a.modo == "todos") {
                    avisar += c to (a.motivoInteresse(c) ?: ""); c.notificado = true
                } else {
                    val m = a.motivoInteresse(c)
                    if (m != null) { avisar += c to m; c.notificado = true }
                    else if (c.juridico == false) c.notificado = true // não interessa; não precisa olhar de novo
                }
            }

            a.salvar(mapa)
            a.ufsConhecidas = conhecidas + ufsLidas
            a.ultimaVerificacao = agora
            a.ultimoErro = erros.joinToString("; ")
            if (avisar.isNotEmpty()) notificar(ctx, avisar)
            Resultado(avisar.size, erros.firstOrNull())
        }

    fun nomeRegiao(r: String) = when (r) {
        "norte" -> "Norte"; "nordeste" -> "Nordeste"; "centrooeste" -> "Centro-Oeste"
        "sudeste" -> "Sudeste"; "sul" -> "Sul"; else -> "Nacional"
    }

    fun criarCanal(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CANAL, "Concursos novos", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Aviso quando aparece um concurso novo na sua região" }
            )
        }
    }

    private fun notificar(ctx: Context, lista: List<Pair<Concurso, String>>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        criarCanal(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val abrirApp = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags
        )
        try {
            if (lista.size > 4) {
                val linhas = NotificationCompat.InboxStyle()
                lista.take(7).forEach { (c, _) -> linhas.addLine("${c.orgao} (${c.uf})") }
                if (lista.size > 7) linhas.setSummaryText("e mais ${lista.size - 7}")
                nm.notify(
                    1, NotificationCompat.Builder(ctx, CANAL)
                        .setSmallIcon(R.drawable.ic_notif)
                        .setContentTitle("${lista.size} concursos novos")
                        .setContentText(lista.take(3).joinToString(", ") { it.first.orgao })
                        .setStyle(linhas).setContentIntent(abrirApp).setAutoCancel(true).build()
                )
            } else {
                for ((c, motivo) in lista) {
                    val abrir = PendingIntent.getActivity(
                        ctx, c.url.hashCode(), Intent(Intent.ACTION_VIEW, Uri.parse(c.url)), flags
                    )
                    val detalhe = listOf(c.vagas, c.cargos, prazoLegivel(c.prazo)).filter { it.isNotBlank() }.joinToString(" · ")
                    nm.notify(
                        c.url.hashCode(), NotificationCompat.Builder(ctx, CANAL)
                            .setSmallIcon(R.drawable.ic_notif)
                            .setContentTitle("Concurso novo: ${c.orgao} (${c.uf})")
                            .setContentText(if (motivo.isNotBlank()) "$motivo · $detalhe" else detalhe)
                            .setStyle(NotificationCompat.BigTextStyle().bigText(
                                listOf(motivo, c.titulo, detalhe).filter { it.isNotBlank() }.joinToString("\n")))
                            .setContentIntent(abrir).setAutoCancel(true).build()
                    )
                }
            }
        } catch (_: SecurityException) { }
    }

    fun prazoLegivel(p: String): String {
        if (p.isBlank()) return ""
        return if (Regex("\\d{2}/\\d{2}/\\d{4}").containsMatchIn(p)) "inscrições até $p" else p
    }
}
