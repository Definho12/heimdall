package com.guilherme.heimdall

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

val Fundo = Color(0xFF0E1013)
val Cartao = Color(0xFF171A1F)
val Linha = Color(0xFF262A31)
val Ouro = Color(0xFFD4A84B)
val Texto = Color(0xFFE8E6E1)
val Apagado = Color(0xFF8C9099)
val Alerta = Color(0xFFE07A5F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Verificador.criarCanal(this)
        if (Armazem(this).estado != null) Trabalho.agendar(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Ouro, onPrimary = Fundo, background = Fundo, surface = Cartao,
                    onBackground = Texto, onSurface = Texto, surfaceVariant = Cartao, outline = Linha,
                    secondaryContainer = Ouro.copy(alpha = .18f), onSecondaryContainer = Ouro,
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = Fundo) { App() }
            }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val a = remember { Armazem(ctx) }
    var tela by remember { mutableStateOf(if (a.estado == null) "inicio" else "lista") }
    var versao by remember { mutableIntStateOf(0) }
    val permissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun pedirPermissao() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permissao.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        when (tela) {
            "inicio" -> TelaInicio { uf ->
                a.estado = uf
                a.ufs = setOf(uf) + Estados.vizinhos[uf].orEmpty()
                Trabalho.agendar(ctx, mudou = true)
                pedirPermissao()
                tela = "lista"; versao++
            }
            "ajustes" -> {
                BackHandler { tela = "lista"; versao++ }
                TelaAjustes(a, onVoltar = { tela = "lista"; versao++ }, onPermissao = { pedirPermissao() })
            }
            else -> TelaLista(a, versao, primeiraVez = a.ultimaVerificacao == 0L) { tela = "ajustes" }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaInicio(onEscolher: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(32.dp))
        Text("Heimdall", color = Ouro, fontSize = 34.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Text("Eu fico de olho nos concursos abertos e te aviso quando aparecer um novo.", color = Texto, fontSize = 16.sp)
        Spacer(Modifier.height(28.dp))
        Text("Qual é o seu estado?", color = Apagado, fontSize = 14.sp)
        Text("Os estados vizinhos entram junto. Dá para mudar depois.", color = Apagado, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Estados.nomes.forEach { (uf, nome) ->
                OutlinedButton(
                    onClick = { onEscolher(uf) }, shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Linha), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) { Text(nome, color = Texto, fontSize = 14.sp) }
            }
        }
    }
}

@Composable
fun TelaLista(a: Armazem, versao: Int, primeiraVez: Boolean, onAjustes: () -> Unit) {
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    var todos by remember(versao) { mutableStateOf(a.concursos().values.toList()) }
    var filtro by remember { mutableStateOf(if (a.modo == "todos") "todos" else "juridicos") }
    var busca by remember { mutableStateOf("") }
    var buscando by remember { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }
    var progresso by remember { mutableStateOf("") }
    var aviso by remember(versao) { mutableStateOf(a.ultimoErro) }
    var ultima by remember(versao) { mutableLongStateOf(a.ultimaVerificacao) }

    fun atualizar() {
        if (ocupado) return
        ocupado = true
        escopo.launch {
            val r = Verificador.verificar(ctx, emPrimeiroPlano = true) { progresso = it }
            todos = a.concursos().values.toList()
            aviso = r.erro ?: ""
            ultima = a.ultimaVerificacao
            ocupado = false; progresso = ""
        }
    }
    LaunchedEffect(versao) {
        if (primeiraVez || System.currentTimeMillis() - a.ultimaVerificacao > TimeUnit.HOURS.toMillis(1)) atualizar()
    }

    val visiveis = remember(todos, filtro, busca) {
        val b = Juridico.normalizar(busca.trim())
        todos.filter { it.ativo && a.estaNaRegiao(it) }
            .filter { filtro == "todos" || a.motivoInteresse(it) != null }
            .filter { b.isEmpty() || Juridico.normalizar(it.textoDaLista() + " " + it.uf).contains(b) }
            .sortedWith(compareByDescending<Concurso> { ehNovo(it) }.thenBy { diasRestantes(it.prazo) ?: 9999 })
    }
    val semClassificar = todos.count { it.ativo && a.estaNaRegiao(it) && it.juridico == null }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Heimdall", color = Ouro, fontSize = 26.sp, fontWeight = FontWeight.Light)
                Text(
                    when {
                        ocupado -> progresso.ifBlank { "Verificando…" }
                        ultima == 0L -> "Ainda não verifiquei"
                        else -> "Verificado ${quando(ultima)} · ${a.ufs.sorted().joinToString(" ")}${if (a.nacional) " + nacionais" else ""}"
                    }, color = Apagado, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { buscando = !buscando; if (!buscando) busca = "" }) { Icon(Icons.Default.Search, "Buscar", tint = Apagado) }
            IconButton(onClick = { atualizar() }, enabled = !ocupado) {
                if (ocupado) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Ouro)
                else Icon(Icons.Default.Refresh, "Atualizar", tint = Apagado)
            }
            IconButton(onClick = onAjustes) { Icon(Icons.Default.Settings, "Ajustes", tint = Apagado) }
        }
        if (buscando) {
            OutlinedTextField(
                value = busca, onValueChange = { busca = it }, singleLine = true,
                placeholder = { Text("Buscar órgão, cargo ou estado") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), shape = RoundedCornerShape(12.dp),
            )
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filtro == "juridicos", { filtro = "juridicos" }, { Text("Jurídicos") })
            FilterChip(filtro == "todos", { filtro = "todos" }, { Text("Todos") })
            Spacer(Modifier.weight(1f))
            Text("${visiveis.size}", color = Apagado, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
        }
        if (aviso.isNotBlank() && !ocupado) {
            Text("Aviso: $aviso", color = Alerta, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        }
        if (filtro == "juridicos" && semClassificar > 0 && !ocupado) {
            Text(
                "Ainda estou conferindo os cargos de $semClassificar concursos. Toque em atualizar para terminar.",
                color = Apagado, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
        if (visiveis.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (ocupado) "Buscando os concursos da sua região…" else "Nada por aqui por enquanto.",
                    color = Apagado, fontSize = 15.sp,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visiveis, key = { it.url }) { c ->
                    CartaoConcurso(c, a.motivoInteresse(c)) {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.url)))
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CartaoConcurso(c: Concurso, motivo: String?, onClick: () -> Unit) {
    val dias = diasRestantes(c.prazo)
    Column(
        Modifier.fillMaxWidth().background(Cartao, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.orgao, color = Texto, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Etiqueta(if (c.uf == Estados.NACIONAL) "Nacional" else c.uf, Apagado)
        }
        if (c.vagas.isNotBlank()) Text(c.vagas, color = Texto.copy(alpha = .85f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        if (c.cargos.isNotBlank()) Text(c.cargos, color = Apagado, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (c.escolaridade.isNotBlank()) Text(c.escolaridade, color = Apagado, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (ehNovo(c)) Etiqueta("NOVO", Ouro, cheio = true)
            if (motivo != null) Etiqueta(motivo, Ouro)
            val prazoTxt = when {
                dias == null -> c.prazo
                dias < 0 -> "até ${c.prazo}"
                dias == 0 -> "Inscrições terminam hoje"
                dias == 1 -> "Último dia amanhã"
                else -> "Até ${c.prazo.take(5)} · faltam $dias dias"
            }
            if (prazoTxt.isNotBlank()) Etiqueta(prazoTxt, if (dias != null && dias <= 3) Alerta else Apagado)
        }
    }
}

@Composable
fun Etiqueta(t: String, cor: Color, cheio: Boolean = false) {
    Text(
        t, color = if (cheio) Fundo else cor, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(if (cheio) cor else cor.copy(alpha = .12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaAjustes(a: Armazem, onVoltar: () -> Unit, onPermissao: () -> Unit) {
    val ctx = LocalContext.current
    var estado by remember { mutableStateOf(a.estado ?: "") }
    var ufs by remember { mutableStateOf(a.ufs) }
    var nacional by remember { mutableStateOf(a.nacional) }
    var modo by remember { mutableStateOf(a.modo) }
    var horas by remember { mutableIntStateOf(a.horas) }
    var palavras by remember { mutableStateOf(a.palavras) }
    var escolhendoEstado by remember { mutableStateOf(false) }

    fun salvar() {
        val mudouHoras = horas != a.horas
        a.estado = estado; a.ufs = ufs; a.nacional = nacional; a.modo = modo; a.horas = horas; a.palavras = palavras.trim()
        // concursos que passam a interessar por causa das palavras novas não devem virar uma enxurrada de avisos
        Trabalho.agendar(ctx, mudou = mudouHoras)
    }
    DisposableEffect(Unit) { onDispose { salvar() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Texto) }
            Text("Ajustes", color = Texto, fontSize = 20.sp)
        }
        Secao("Seu estado") {
            OutlinedButton(onClick = { escolhendoEstado = !escolhendoEstado }, border = BorderStroke(1.dp, Linha)) {
                Text(Estados.nomes[estado] ?: "Escolher", color = Texto)
            }
            if (escolhendoEstado) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Estados.nomes.keys.forEach { uf ->
                        FilterChip(uf == estado, {
                            estado = uf; ufs = setOf(uf) + Estados.vizinhos[uf].orEmpty(); escolhendoEstado = false
                        }, { Text(uf) })
                    }
                }
            }
        }
        Secao("Estados acompanhados", "O seu e os vizinhos já vêm marcados. Toque para incluir ou tirar.") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val ordem = listOf(estado) + Estados.vizinhos[estado].orEmpty()
                val todos = ordem.filter { it.isNotBlank() } + Estados.nomes.keys.filter { it !in ordem }
                todos.forEach { uf ->
                    FilterChip(uf in ufs, { ufs = if (uf in ufs) ufs - uf else ufs + uf }, { Text(uf) })
                }
            }
            Spacer(Modifier.height(6.dp))
            LinhaSwitch("Concursos nacionais (federais)", nacional) { nacional = it }
        }
        Secao("Me avisar de") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(modo == "juridico", { modo = "juridico" }, { Text("Só jurídicos") })
                FilterChip(modo == "todos", { modo = "todos" }, { Text("Todos os concursos") })
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Jurídico = tribunais, MP, Defensoria, Procuradorias, Polícia Civil/Federal, cartórios e cargos como advogado, " +
                    "procurador, analista judiciário ou que pedem formação em Direito.", color = Apagado, fontSize = 12.sp,
            )
        }
        Secao("Palavras extras", "Também aviso quando aparecer alguma destas palavras (separe por vírgula).") {
            OutlinedTextField(
                palavras, { palavras = it }, Modifier.fillMaxWidth(),
                placeholder = { Text("ex.: Receita Federal, auditor, INSS") }, shape = RoundedCornerShape(12.dp),
            )
        }
        Secao("Verificar a cada") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 6, 12, 24).forEach { h -> FilterChip(horas == h, { horas = h }, { Text("$h h") }) }
            }
        }
        Secao("Notificações") {
            OutlinedButton(onClick = onPermissao, border = BorderStroke(1.dp, Linha)) { Text("Permitir notificações", color = Texto) }
        }
        Text(
            "Fonte: listas do PCI Concursos. Toque num concurso para abrir a notícia com o edital.",
            color = Apagado, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun Secao(titulo: String, sub: String? = null, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(titulo, color = Ouro, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (sub != null) Text(sub, color = Apagado, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        conteudo()
    }
}

@Composable
fun LinhaSwitch(t: String, v: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!v) }, verticalAlignment = Alignment.CenterVertically) {
        Text(t, color = Texto, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(v, onChange)
    }
}

fun ehNovo(c: Concurso) = !c.inicial && System.currentTimeMillis() - c.vistoEm < TimeUnit.DAYS.toMillis(3)

fun diasRestantes(prazo: String): Int? {
    val m = Regex("(\\d{2})/(\\d{2})/(\\d{4})").findAll(prazo).lastOrNull() ?: return null
    return try {
        val fim = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).parse(m.value)!!
        val hoje = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        TimeUnit.MILLISECONDS.toDays(fim.time - hoje.timeInMillis).toInt()
    } catch (_: Exception) { null }
}

fun quando(t: Long): String {
    val min = (System.currentTimeMillis() - t) / 60_000
    return when {
        min < 1 -> "agora"
        min < 60 -> "há $min min"
        min < 24 * 60 -> "às " + SimpleDateFormat("HH:mm", Locale.ROOT).format(t)
        else -> "em " + SimpleDateFormat("dd/MM HH:mm", Locale.ROOT).format(t)
    }
}
