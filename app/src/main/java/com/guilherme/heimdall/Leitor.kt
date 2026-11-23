package com.guilherme.heimdall

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Lê as páginas de concursos do PCI Concursos. */
object Leitor {
    private val reData = Regex("\\d{2}/\\d{2}/\\d{4}")
    private val reUf = Regex("^[A-Z]{2}$")

    fun lerRegiao(html: String, nacional: Boolean = false): List<Concurso> =
        lerRegiao(Jsoup.parse(html, "https://www.pciconcursos.com.br/"), nacional)

    fun lerRegiao(doc: Document, nacional: Boolean = false): List<Concurso> {
        val vistos = HashSet<String>()
        val saida = ArrayList<Concurso>()
        for (a in doc.select("a[href*=/noticias/]")) {
            val url = a.absUrl("href").substringBefore("#").substringBefore("?")
            if (url.isBlank() || !vistos.add(url)) continue
            val bloco = blocoDe(a) ?: continue
            val partes = bloco.children().map { it.text().trim() }.filter { it.isNotEmpty() }
            val uf = partes.firstOrNull { reUf.matches(it) && it in Estados.nomes }
                ?: if (nacional) Estados.NACIONAL else continue
            val info = bloco.selectFirst(".cd")
            val linhasInfo = if (info != null) {
                info.html().split(Regex("(?i)<br\\s*/?>")).map { Jsoup.parse(it).text().trim() }.filter { it.isNotEmpty() }
            } else partes.filter { it != a.text().trim() && it != uf && !reData.containsMatchIn(it) }
            val prazoTxt = bloco.selectFirst(".ce")?.text()?.trim()
                ?: partes.lastOrNull { reData.containsMatchIn(it) } ?: ""
            saida += Concurso(
                url = url,
                orgao = a.text().trim(),
                uf = uf,
                titulo = a.attr("title").trim(),
                vagas = linhasInfo.getOrElse(0) { "" },
                cargos = linhasInfo.getOrElse(1) { "" },
                escolaridade = linhasInfo.getOrElse(2) { "" },
                prazo = prazoTxt,
            )
        }
        return saida
    }

    /** Sobe a partir do link até o bloco do concurso (o maior pai que só tem esse link de notícia). */
    private fun blocoDe(a: Element): Element? {
        var atual: Element = a
        while (true) {
            val pai = atual.parent() ?: return null
            if (pai.select("a[href*=/noticias/]").size > 1) break
            atual = pai
            if (atual.tagName() == "body") return null
        }
        return if (atual === a) null else atual
    }

    /** Texto principal de uma notícia (para descobrir cargos quando a lista diz só "Vários cargos"). */
    fun textoNoticia(html: String): String {
        val doc = Jsoup.parse(html)
        val corpo = doc.selectFirst("article, #noticia, .noticia, [itemprop=articleBody], #conteudo, main") ?: doc.body()
        return corpo.text()
    }
}
