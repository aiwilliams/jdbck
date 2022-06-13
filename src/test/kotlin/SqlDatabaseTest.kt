import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.PrintWriter
import java.util.*
import kotlin.test.assertEquals

private fun loadHikariProperties() = Properties().apply {
    setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource")
    setProperty("dataSource.databaseName", "jdbck")
    setProperty("dataSource.serverName", "localhost")
    setProperty("dataSource.portNumber", "5431")
    setProperty("dataSource.user", "test")
    setProperty("dataSource.password", "password")
    this["dataSource.logWriter"] = PrintWriter(System.out)
}

internal class SqlDatabaseTest {
    private val db = SqlDatabase(loadHikariProperties(), JdbcQueryParameterAdapter())

    @AfterEach
    fun closeDb() {
        db.close()
    }

    @Test
    fun transaction() {
        val contextId = UUID.randomUUID()
        val id = UUID.randomUUID()

        val results = ArrayList<List<Any>>()
        db.transaction {
            update("""INSERT INTO entities (contextId, id, textColumn, textArrayColumn, jsonbColumn) VALUES ('$contextId', '$id', 'Some Text', '{"A", "Text", "Array"}', '{"longValue": 123}')""")

            val transactionQueryResult = query("""SELECT * FROM entities WHERE contextId = '$contextId'""")
            transactionQueryResult.close()

            query("""SELECT * FROM entities WHERE contextId = '$contextId'""") {
                // process ResultSet with auto close
            }

            preparedStatement("SELECT contextId, id, textColumn, textArrayColumn, jsonbColumn FROM entities WHERE contextId = ? AND jsonbColumn -> 'longValue' = to_jsonb(?)") {
                query(listOf(contextId, 123L)) {
                    while (next()) {
                        results.add(
                            listOf(
                                getString(1),
                                getString(2),
                                getString(3),
                                getStringList(4),
                                getObject(5)
                            )
                        )
                    }
                }
                val preparedStatementQueryResult = query(listOf(contextId, 123L))
                preparedStatementQueryResult.close()
            }
        }
        assertEquals(1, results.size)
        assertEquals(
            listOf(contextId, id, "Some Text", listOf("A", "Text", "Array"), """{"longValue": 123}"""),
            results.first()
        )
    }
}
