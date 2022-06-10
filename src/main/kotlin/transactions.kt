import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*

class SqlTransaction<T>(private val conn: Connection, private val parameterAdapter: SqlQueryParameterAdapter<T>) {
    fun preparedQuery(sql: String, work: SqlPreparedQuery<T>.() -> Unit) {
        val (adaptedSql, references) = parameterAdapter.adaptSql(sql)
        conn.prepareStatement(adaptedSql).use {
            SqlPreparedQuery(it, parameterAdapter, references).work()
        }
    }
}

class SqlPreparedQuery<T>(
    private val statement: PreparedStatement,
    private val parameterAdapter: SqlQueryParameterAdapter<T>,
    private val parameterReferenceList: SqlQueryParameterReferenceList
) {
    fun execute(params: T, processResults: ResultSet.() -> Unit) = statement.use {
        val adaptedParams = parameterAdapter.adaptParameters(parameterReferenceList, params)
        adaptedParams.forEachIndexed { index, any ->
            statement.setObject(index + 1, any)
        }
        statement.executeQuery().use {
            it.processResults()
        }
    }
}

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
            if (parameterIndex < 0 || parameterIndex >= params.size)
                throw IndexOutOfBoundsException("Query parameter reference not found in parameters: $it.")
            params[parameterIndex]
        }
    }
}

class SqlDatabase<T>(hikariProperties: Properties, private val queryParameterAdapter: SqlQueryParameterAdapter<T>) {
    private val hikariConfig = HikariConfig(hikariProperties)

    private val hikariDataSource: HikariDataSource by lazy {
        HikariDataSource(hikariConfig)
    }

    fun transaction(work: SqlTransaction<T>.() -> Unit) {
        hikariDataSource.connection.use { conn ->
            conn.autoCommit = false
            SqlTransaction(conn, queryParameterAdapter).work()
            conn.commit()
        }
    }

    fun close() {
        if (!hikariDataSource.isClosed)
            hikariDataSource.close()
    }
}