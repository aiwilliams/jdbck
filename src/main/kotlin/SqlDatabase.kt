import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types
import java.util.*

@JvmInline
value class JsonString(val json: String)

class SqlDatabase<T>(hikariProperties: Properties, private val queryParameterAdapter: SqlQueryParameterAdapter<T>) :
    AutoCloseable {
    private val hikariConfig = HikariConfig(hikariProperties)

    private val hikariDataSource: HikariDataSource by lazy {
        HikariDataSource(hikariConfig)
    }

    fun transaction(work: SqlTransaction<T>.() -> Unit) {
        hikariDataSource.connection.use { conn ->
            SqlTransaction(conn, queryParameterAdapter).use(work)
        }
    }

    override fun close() {
        if (!hikariDataSource.isClosed) hikariDataSource.close()
    }
}

class SqlTransaction<T>(private val conn: Connection, private val parameterAdapter: SqlQueryParameterAdapter<T>) :
    AutoCloseable {
    private var _statement: Statement? = null

    private val statement: Statement
        get() {
            if (_statement == null) _statement = conn.createStatement()
            return _statement ?: throw AssertionError("Set to null by another thread")
        }

    init {
        conn.autoCommit = true
    }

    fun preparedStatement(sql: String, work: SqlPreparedStatement<T>.() -> Unit) {
        SqlPreparedStatement(conn, parameterAdapter, sql).use(work)
    }

    fun query(sql: String): ResultSet {
        return statement.executeQuery(sql)
    }

    fun query(sql: String, processResults: ResultSet.() -> Unit) {
        statement.executeQuery(sql).use(processResults)
    }

    fun update(sql: String): Int {
        return statement.executeUpdate(sql)
    }

    override fun close() {
        _statement?.close()
        conn.autoCommit = false
    }
}

class SqlPreparedStatement<T>(
    private val conn: Connection,
    private val parameterAdapter: SqlQueryParameterAdapter<T>,
    sql: String,
) : AutoCloseable {
    private val adaptedSql: String

    private val parameterReferenceList: SqlQueryParameterReferenceList
    private var _preparedStatement: PreparedStatement? = null

    private val preparedStatement: PreparedStatement
        get() {
            if (_preparedStatement == null) _preparedStatement = conn.prepareStatement(adaptedSql)
            return _preparedStatement ?: throw AssertionError("Set to null by another thread")
        }

    init {
        val (adaptedSql, referenceList) = parameterAdapter.adaptSql(sql)
        this.adaptedSql = adaptedSql
        this.parameterReferenceList = referenceList
    }

    fun query(params: T): ResultSet {
        val adaptedParams = parameterAdapter.adaptParameters(parameterReferenceList, params)
        adaptedParams.forEachIndexed { index, any ->
            preparedStatement.setObject(index + 1, any)
        }
        return preparedStatement.executeQuery()
    }

    fun query(params: T, processResults: ResultSet.() -> Unit) {
        val adaptedParams = parameterAdapter.adaptParameters(parameterReferenceList, params)
        adaptedParams.forEachIndexed { index, any ->
            preparedStatement.setObject(index + 1, any)
        }
        preparedStatement.executeQuery().use(processResults)
    }

    fun update(params: T) {
        val adaptedParams = parameterAdapter.adaptParameters(parameterReferenceList, params)
        adaptedParams.forEachIndexed { index, any ->
            when (any) {
                is JsonString -> preparedStatement.setObject(index + 1, any.json, Types.OTHER)
                else -> preparedStatement.setObject(index + 1, any)
            }
        }
        preparedStatement.executeUpdate()
    }

    override fun close() {
        _preparedStatement?.close()
    }
}

/**
 * Reads the column as an ARRAY of Strings (i.e. varchar[] or text[] in Postgres).
 *
 * - https://docs.oracle.com/javase/tutorial/jdbc/basics/array.html
 * - https://jdbc.postgresql.org/documentation/head/arrays.html
 */
fun ResultSet.getStringList(columnIndex: Int): List<String> {
    if (metaData.getColumnType(columnIndex) != Types.ARRAY)
        throw RuntimeException("Column $columnIndex is not an ARRAY.")
    val list = (getArray(columnIndex).array as Array<*>).asList()
    if (list.isNotEmpty() && list.first() !is String)
        throw RuntimeException("Column $columnIndex is not an ARRAY of String.")
    @Suppress("UNCHECKED_CAST")
    return list as List<String>
}