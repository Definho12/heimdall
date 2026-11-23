package com.guilherme.heimdall

/** Os 27 estados, a região de cada um (como o PCI Concursos organiza as páginas) e os vizinhos. */
object Estados {
    val nomes = linkedMapOf(
        "AC" to "Acre", "AL" to "Alagoas", "AP" to "Amapá", "AM" to "Amazonas", "BA" to "Bahia",
        "CE" to "Ceará", "DF" to "Distrito Federal", "ES" to "Espírito Santo", "GO" to "Goiás",
        "MA" to "Maranhão", "MT" to "Mato Grosso", "MS" to "Mato Grosso do Sul", "MG" to "Minas Gerais",
        "PA" to "Pará", "PB" to "Paraíba", "PR" to "Paraná", "PE" to "Pernambuco", "PI" to "Piauí",
        "RJ" to "Rio de Janeiro", "RN" to "Rio Grande do Norte", "RS" to "Rio Grande do Sul",
        "RO" to "Rondônia", "RR" to "Roraima", "SC" to "Santa Catarina", "SP" to "São Paulo",
        "SE" to "Sergipe", "TO" to "Tocantins",
    )

    /** UF -> página da região no PCI Concursos */
    private val regiao = mapOf(
        "norte" to listOf("AC", "AP", "AM", "PA", "RO", "RR", "TO"),
        "nordeste" to listOf("AL", "BA", "CE", "MA", "PB", "PE", "PI", "RN", "SE"),
        "centrooeste" to listOf("DF", "GO", "MT", "MS"),
        "sudeste" to listOf("ES", "MG", "RJ", "SP"),
        "sul" to listOf("PR", "RS", "SC"),
    ).flatMap { (r, ufs) -> ufs.map { it to r } }.toMap()

    fun regiaoDe(uf: String): String? = regiao[uf]

    val vizinhos: Map<String, List<String>> = mapOf(
        "AC" to listOf("AM", "RO"),
        "AL" to listOf("PE", "SE", "BA"),
        "AP" to listOf("PA"),
        "AM" to listOf("AC", "RO", "MT", "PA", "RR"),
        "BA" to listOf("SE", "AL", "PE", "PI", "TO", "GO", "MG", "ES"),
        "CE" to listOf("RN", "PB", "PE", "PI"),
        "DF" to listOf("GO", "MG"),
        "ES" to listOf("BA", "MG", "RJ"),
        "GO" to listOf("DF", "MG", "BA", "TO", "MT", "MS"),
        "MA" to listOf("PI", "TO", "PA"),
        "MT" to listOf("RO", "AM", "PA", "TO", "GO", "MS"),
        "MS" to listOf("MT", "GO", "MG", "SP", "PR"),
        "MG" to listOf("BA", "GO", "DF", "MS", "SP", "RJ", "ES"),
        "PA" to listOf("AP", "AM", "RR", "MT", "TO", "MA"),
        "PB" to listOf("RN", "CE", "PE"),
        "PR" to listOf("SP", "MS", "SC"),
        "PE" to listOf("PB", "CE", "PI", "BA", "AL"),
        "PI" to listOf("MA", "CE", "PE", "BA", "TO"),
        "RJ" to listOf("MG", "ES", "SP"),
        "RN" to listOf("CE", "PB"),
        "RS" to listOf("SC"),
        "RO" to listOf("AC", "AM", "MT"),
        "RR" to listOf("AM", "PA"),
        "SC" to listOf("PR", "RS"),
        "SP" to listOf("MG", "RJ", "PR", "MS"),
        "SE" to listOf("AL", "BA"),
        "TO" to listOf("MA", "PI", "BA", "GO", "MT", "PA"),
    )

    const val NACIONAL = "BR"

    fun urlRegiao(r: String) = "https://www.pciconcursos.com.br/concursos/$r/"
}
