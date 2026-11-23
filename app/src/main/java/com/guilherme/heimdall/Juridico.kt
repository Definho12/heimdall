package com.guilherme.heimdall

import java.text.Normalizer

/** Decide se um concurso é da área jurídica, olhando nomes de órgãos e cargos. */
object Juridico {
    private val termos: List<Pair<Regex, String>> = listOf(
        "\\bjuiz(a|es)?\\b|magistratura" to "Magistratura",
        "\\bpromotor" to "Promotor",
        "ministerio publico|\\bmp[\\s/-]?(ac|al|ap|am|ba|ce|df|dft|es|go|ma|mt|ms|mg|pa|pb|pr|pe|pi|rj|rn|rs|ro|rr|sc|sp|se|to|u|f|t|m|e|c)\\b|\\bmpdft\\b|\\bmpf\\b|\\bmpt\\b|\\bmpm\\b" to "Ministério Público",
        "defensor|defensoria" to "Defensoria",
        "procurador|procuradoria|\\bpge\\b|\\bpgm\\b|\\bagu\\b|\\bpgfn\\b" to "Procuradoria",
        "delegado" to "Delegado",
        "advogad" to "Advogado",
        "analista judiciari|tecnico judiciari|oficial de justica|escrevente|escrivao|oficial judiciari" to "Servidor do Judiciário",
        "tribunal|\\btj[\\s/-]?[a-z]{2}\\b|\\btjdft\\b|\\btrf[\\s-]?\\d?\\b|\\btrt[\\s-]?\\d{0,2}\\b|\\btre([\\s/-][a-z]{2})?\\b|\\btre(sp|rj|mg|pr|rs|sc|ba|pe|ce|go|df|es|pa|am|ma|pb|rn|al|se|pi|mt|ms|ro|ac|ap|rr|to)\\b|\\bstj\\b|\\bstf\\b|\\btst\\b|\\bstm\\b|\\btse\\b|\\btc[emu][\\s/-]?[a-z]{0,2}\\b|\\bcnj\\b|\\bcnmp\\b" to "Tribunal",
        "juridic" to "Jurídico",
        "cartorio|notari|registrador|serventias? extrajudicia|outorga de delegac" to "Cartório",
        "bacharel(ado)? em direito|graduacao em direito|formacao em direito|curso de direito|superior em direito|ciencias juridicas|bacharel em ciencias juridicas" to "Formação em Direito",
        "conciliador|mediador judicial|juiz leigo" to "Conciliador / Juiz leigo",
        "policia civil|\\bpc[\\s/-]?(ac|al|ap|am|ba|ce|df|es|go|ma|mt|ms|mg|pa|pb|pr|pe|pi|rj|rn|rs|ro|rr|sc|sp|se|to)\\b" to "Polícia Civil",
        "policia federal|\\bpf\\b" to "Polícia Federal",
    ).map { (p, n) -> Regex(p) to n }

    fun normalizar(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** Retorna o motivo (ex.: "Advogado") ou null se não achou nada jurídico. */
    fun classificar(texto: String): String? {
        val t = normalizar(texto)
        return termos.firstOrNull { it.first.containsMatchIn(t) }?.second
    }

    /** Palavras-chave próprias do usuário (separadas por vírgula). */
    fun palavraPropria(texto: String, palavras: String): String? {
        val t = normalizar(texto)
        return palavras.split(",").map { it.trim() }.filter { it.length >= 3 }
            .firstOrNull { t.contains(normalizar(it)) }
    }
}
