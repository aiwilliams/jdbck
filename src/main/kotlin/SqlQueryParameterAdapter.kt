typealias SqlQueryParameterReferenceList = List<String>

interface SqlQueryParameterAdapter<in T> {
    fun adaptSql(sql: String): Pair<String, SqlQueryParameterReferenceList>
    fun adaptParameters(references: SqlQueryParameterReferenceList, params: T): List<Any>
}

class JdbcQueryParameterAdapter : SqlQueryParameterAdapter<List<Any>> {
    private val referenceRegex = """\?""".toRegex()

    override fun adaptSql(sql: String) = Pair(sql, mutableListOf<String>()).apply {
        second.addAll(referenceRegex.findAll(sql).map { it.value })
    }

    override fun adaptParameters(references: SqlQueryParameterReferenceList, params: List<Any>) = params.also {
        if (references.size != params.size)
            throw IllegalArgumentException("Query parameter reference count does not match parameter count.")
    }
}

class DollarReferenceQueryParameterAdapter : SqlQueryParameterAdapter<List<Any>> {
    private val referenceRegex = """\$\d+""".toRegex()

    override fun adaptSql(sql: String): Pair<String, List<String>> {
        val references = mutableListOf<String>()
        return Pair(referenceRegex.replace(sql) {
            references.add(it.value)
            "?"
        }, references)
    }

    override fun adaptParameters(references: SqlQueryParameterReferenceList, params: List<Any>): List<Any> {
        return references.map {
            val parameterIndex = it.substring(1).toInt() - 1
            if (parameterIndex < 0 || parameterIndex >= params.size) throw IndexOutOfBoundsException("Query parameter reference not found in parameters: $it.")
            params[parameterIndex]
        }
    }
}