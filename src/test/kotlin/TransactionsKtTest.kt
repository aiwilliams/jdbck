import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.PrintWriter
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class JdbcQueryParameterAdapterTest {
    private val adapter = JdbcQueryParameterAdapter()

    @Test
    fun adaptSql() {
        val (sql, references) = adapter.adaptSql("SELECT * FROM table WHERE ? = ?")
        assertEquals("SELECT * FROM table WHERE ? = ?", sql)
        assertEquals(listOf("?", "?"), references)
    }

    @Test
    fun adaptParameters() {
        val (_, references) = adapter.adaptSql("SELECT * FROM table WHERE ? = ?")
        assertEquals(listOf("columnName", "value"), adapter.adaptParameters(references, listOf("columnName", "value")))
    }

    @Test
    fun `adaptParameters throws error when reference count does not equal param count`() {
        val (_, references) = adapter.adaptSql("SELECT * FROM table WHERE ? = ?")
        assertThrows<IllegalArgumentException>("Query parameter reference count does not match parameter count.") {
            adapter.adaptParameters(references, listOf())
        }
    }
}

internal class DollarReferenceQueryParameterModeTest {
    private val adapter = DollarReferenceQueryParameterAdapter()

    @Test
    fun adaptSql() {
        val (sql, references) = adapter.adaptSql("SELECT * FROM table WHERE $1 = $1")
        assertEquals("SELECT * FROM table WHERE ? = ?", sql)
        assertEquals(listOf("$1", "$1"), references)
    }

    @Test
    fun adaptParameters() {
        val (_, references) = adapter.adaptSql("SELECT * FROM table WHERE $1 = $1")
        assertEquals(listOf(true, true), adapter.adaptParameters(references, listOf(true)))
    }

    @Test
    fun `adaptParameters throws error when reference count does not equal param count`() {
        val (_, references) = adapter.adaptSql("SELECT * FROM table WHERE $1 = $1")
        assertThrows<IndexOutOfBoundsException>("Query parameter reference not found in parameters: $1.") {
            adapter.adaptParameters(references, listOf())
        }
    }
}

internal class TransactionsKtTest {
    private fun loadHikariProperties() = Properties().apply {
        setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource")
        setProperty("dataSource.databaseName", "jdbck")
        setProperty("dataSource.serverName", "localhost")
        setProperty("dataSource.portNumber", "5431")
        setProperty("dataSource.user", "test")
        setProperty("dataSource.password", "password")
        this["dataSource.logWriter"] = PrintWriter(System.out)
    }

    private val db = SqlDatabase(loadHikariProperties(), JdbcQueryParameterAdapter())

    @AfterEach
    fun closeDb() {
        db.close()
    }

    @Test
    fun rawTransaction() {
        val results = ArrayList<List<Any>>()
        db.transaction {
            preparedQuery("SELECT * FROM entities WHERE jsonbColumn -> 'longValue' = to_jsonb(?)") {
                execute(listOf(123L)) {
                    while (next()) {
                        val obj = ArrayList<Any>()
                        for (i in 1..metaData.columnCount) {
                            obj.add(getObject(i))
                        }
                        results.add(obj)
                    }
                }
            }
        }
        assertTrue(results.size > 0)
    }
}
