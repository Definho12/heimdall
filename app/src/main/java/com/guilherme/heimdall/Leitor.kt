package com.guilherme.heimdall

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Lê as páginas de concursos do PCI Concursos. */
object Leitor {

    fun lerRegiao(html: String, nacional: Boolean = false): List<Concurso> =
        lerRegiao(Jsoup.parse(html, "https://www.pciconcursos.com.br/"), nacional)

    fun lerRegiao(doc: Document, nacional: Boolean = false): List<Concurso> {
        val vistos = HashSet<String>()
        val saida = ArrayList<Concurso>()
        for (bloco in doc.select("div[data-url]")) {
            val link = bloco.selectFirst(".ca a[href]") ?: bloco.selectFirst("a[href*=/noticias/]") ?: continue
            val url = (bloco.absUrl("data-url").ifBlank { link.absUrl("href") }).substringBefore("#").substringBefore("?")
            if (url.isBlank() || !vistos.add(url)) continue
            val ufTxt = bloco.selectFirst(".cc")?.text()?.trim()?.uppercase().orEmpty()
            val uf = when {
                ufTxt in Estados.nomes -> ufTxt
                nacional -> Estados.NACIONAL
                else -> continue
            }
            val info = bloco.selectFirst(".cd")
            val linhas = info?.html()?.split(Regex("(?i)<br\\s*/?>"))
                ?.map { Jsoup.parse(it).text().trim() }?.filter { it.isNotEmpty() }.orEmpty()
            saida += Concurso(
                url = url,
                orgao = link.text().trim(),
                uf = uf,
                titulo = link.attr("title").trim(),
                vagas = linhas.getOrElse(0) { "" },
                cargos = linhas.getOrElse(1) { "" },
                escolaridade = linhas.drop(2).joinToString(" "),
                prazo = bloco.selectFirst(".ce")?.text()?.trim().orEmpty()
                    .replace(Regex("(?<=[A-Za-zà-ú])(?=\\d)"), " ").replace(Regex("\\s+"), " "),
            )
        }
        return saida
    }

    /** Texto principal de uma notícia (para descobrir cargos quando a lista diz só "Vários cargos"). */
    fun textoNoticia(html: String): String {
        val doc = Jsoup.parse(html)
        val corpo = doc.selectFirst("article, #noticia, .noticia, [itemprop=articleBody], #conteudo, main") ?: doc.body()
        return corpo.text()
    }
}
