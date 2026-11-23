package com.guilherme.heimdall

import android.content.Context
import java.io.File

/** Guarda os ajustes e a lista de concursos já vistos. */
class Armazem(ctx: Context) {
    private val p = ctx.getSharedPreferences("heimdall", Context.MODE_PRIVATE)
    private val arq = File(ctx.filesDir, "concursos.json")

    var estado: String?
        get() = p.getString("estado", null)
        set(v) = p.edit().putString("estado", v).apply()

    var ufs: Set<String>
        get() = p.getStringSet("ufs", emptySet())!!.toSet()
        set(v) = p.edit().putStringSet("ufs", v).apply()

    /** estados que já foram lidos ao menos uma vez (os concursos deles na 1ª leitura não notificam) */
    var ufsConhecidas: Set<String>
        get() = p.getStringSet("ufsConhecidas", emptySet())!!.toSet()
        set(v) = p.edit().putStringSet("ufsConhecidas", v).apply()

    var nacional: Boolean
        get() = p.getBoolean("nacional", true)
        set(v) = p.edit().putBoolean("nacional", v).apply()

    /** "juridico" ou "todos" */
    var modo: String
        get() = p.getString("modo", "juridico")!!
        set(v) = p.edit().putString("modo", v).apply()

    var horas: Int
        get() = p.getInt("horas", 6)
        set(v) = p.edit().putInt("horas", v).apply()

    var palavras: String
        get() = p.getString("palavras", "")!!
        set(v) = p.edit().putString("palavras", v).apply()

    var ultimaVerificacao: Long
        get() = p.getLong("ultima", 0)
        set(v) = p.edit().putLong("ultima", v).apply()

    var ultimoErro: String
        get() = p.getString("erro", "")!!
        set(v) = p.edit().putString("erro", v).apply()

    @Synchronized fun concursos(): MutableMap<String, Concurso> =
        (if (arq.exists()) Concurso.lista(arq.readText()) else emptyList())
            .associateBy { it.url }.toMutableMap()

    @Synchronized fun salvar(m: Map<String, Concurso>) {
        val tmp = File(arq.parentFile, "concursos.tmp")
        tmp.writeText(Concurso.texto(m.values))
        tmp.renameTo(arq)
    }

    /** O concurso interessa ao usuário? Retorna o motivo, ou null. */
    fun motivoInteresse(c: Concurso): String? {
        palavrasProprias(c)?.let { return it }
        return if (c.juridico == true) c.motivo.ifBlank { "Jurídico" } else null
    }

    fun palavrasProprias(c: Concurso): String? = Juridico.palavraPropria(c.textoDaLista(), palavras)

    fun estaNaRegiao(c: Concurso) = c.uf in ufs || (c.uf == Estados.NACIONAL && nacional)
}
