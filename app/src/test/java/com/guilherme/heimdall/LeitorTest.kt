package com.guilherme.heimdall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LeitorTest {
    private fun amostra(nome: String): String? =
        javaClass.classLoader?.getResource(nome)?.let { org.jsoup.Jsoup.parse(File(it.toURI()), null).outerHtml() }

    @Test fun leRegioesReais() {
        val relatorio = StringBuilder()
        for (r in listOf("sudeste", "sul", "nordeste", "norte", "centrooeste", "nacional")) {
            val html = amostra("$r.html") ?: continue
            val l = Leitor.lerRegiao(html, nacional = r == "nacional")
            relatorio.append("== $r: ${l.size}\n")
            l.take(4).forEach { relatorio.append("  $it\n") }
            relatorio.append("  jurídicos: ${l.count { Juridico.classificar(it.textoDaLista()) != null }} -> ")
            relatorio.append(l.mapNotNull { c -> Juridico.classificar(c.textoDaLista())?.let { "${c.orgao} [$it]" } }.take(15).joinToString("; "))
            relatorio.append("\n")
            if (r != "nacional") assertTrue("poucos concursos em $r: ${l.size}", l.size >= 5)
            l.forEach { assertTrue("prazo vazio: $it", it.prazo.isNotBlank() || r == "nacional") }
        }
        File("build/leitor-relatorio.txt").writeText(relatorio.toString())
        println(relatorio)
    }

    @Test fun leNoticia() {
        val html = amostra("noticia1.html") ?: return
        val t = Leitor.textoNoticia(html)
        File("build/noticia-texto.txt").writeText(t)
        assertTrue(t.length > 200)
    }

    @Test fun classificaJuridico() {
        assertEquals("Advogado", Juridico.classificar("Prefeitura de X abre vaga para Advogado"))
        assertEquals("Tribunal", Juridico.classificar("TJ-SP"))
        assertEquals("Ministério Público", Juridico.classificar("MP-MG abre concurso"))
        assertEquals("Defensoria", Juridico.classificar("Defensoria Pública do Estado"))
        assertNull(Juridico.classificar("Prefeitura de Treinópolis contrata Motorista e Gari"))
        assertNull(Juridico.classificar("Câmara de Vereadores: Auxiliar de Serviços Gerais"))
    }
}
