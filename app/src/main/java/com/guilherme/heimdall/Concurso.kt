package com.guilherme.heimdall

import org.json.JSONArray
import org.json.JSONObject

data class Concurso(
    val url: String,
    val orgao: String,
    val uf: String,
    val titulo: String = "",
    val vagas: String = "",
    val cargos: String = "",
    val escolaridade: String = "",
    val prazo: String = "",
    var vistoEm: Long = 0,
    /** null = ainda não sabemos (precisa abrir a notícia) */
    var juridico: Boolean? = null,
    var motivo: String = "",
    var notificado: Boolean = false,
    var ativo: Boolean = true,
    /** veio na primeira leitura de um estado (não é "novo" de verdade) */
    var inicial: Boolean = false,
) {
    fun textoDaLista() = "$orgao $titulo $cargos"

    fun json(): JSONObject = JSONObject()
        .put("url", url).put("orgao", orgao).put("uf", uf).put("titulo", titulo)
        .put("vagas", vagas).put("cargos", cargos).put("esc", escolaridade).put("prazo", prazo)
        .put("visto", vistoEm).put("jur", juridico ?: JSONObject.NULL).put("motivo", motivo)
        .put("notif", notificado).put("ativo", ativo).put("ini", inicial)

    companion object {
        fun de(o: JSONObject) = Concurso(
            url = o.getString("url"), orgao = o.optString("orgao"), uf = o.optString("uf"),
            titulo = o.optString("titulo"), vagas = o.optString("vagas"), cargos = o.optString("cargos"),
            escolaridade = o.optString("esc"), prazo = o.optString("prazo"),
            vistoEm = o.optLong("visto"),
            juridico = if (o.isNull("jur")) null else o.optBoolean("jur"),
            motivo = o.optString("motivo"), notificado = o.optBoolean("notif"), ativo = o.optBoolean("ativo", true),
            inicial = o.optBoolean("ini"),
        )

        fun lista(s: String): List<Concurso> = try {
            val a = JSONArray(s); (0 until a.length()).map { de(a.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }

        fun texto(l: Collection<Concurso>): String = JSONArray().also { a -> l.forEach { a.put(it.json()) } }.toString()
    }
}
