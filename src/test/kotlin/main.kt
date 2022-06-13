import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.PrintWriter
import java.util.*

fun main() = runBlocking {
    val db = SqlDatabase(loadHikariProperties(), JdbcQueryParameterAdapter())

    val contextId = UUID.randomUUID()

    val entityA = Entity(contextId, UUID.randomUUID(), "A", listOf("A"), JsonString("{}"))
    val entityB = Entity(contextId, UUID.randomUUID(), "B", listOf("B"), JsonString("{}"))

    val insertEntitySql = """INSERT INTO entities (contextId, id, textColumn, textArrayColumn, jsonbColumn) VALUES (?, ?, ?, ?, ?)"""
    val insertEntityValues = fun(e: Entity) = listOf(contextId, e.id, e.textColumn, e.textArrayColumn.toTypedArray(), e.jsonbColumn)

    val job1 = launch {
        try {
            db.transaction {
                preparedStatement(insertEntitySql) {
                    update(insertEntityValues(entityA))
                    update(insertEntityValues(entityB))
                }
            }
        } catch (error: Exception) {
            println("job1: $error")
        }
    }

    val job2 = launch {
        try {
            db.transaction {
                preparedStatement(insertEntitySql) {
                    update(insertEntityValues(entityA))
                    update(insertEntityValues(entityB))
                }
            }
        } catch (error: Exception) {
            println("job2: $error")
        }
    }

    try {
        job1.join()
    } catch (err: Exception) {
        println(err)
    }

    try {
        job2.join()
    } catch (err: Exception) {
        println(err)
    }

    println("Here")
}

private fun loadHikariProperties() = Properties().apply {
    setProperty("dataSourceClassName", "org.postgresql.ds.PGSimpleDataSource")
    setProperty("dataSource.databaseName", "jdbck")
    setProperty("dataSource.serverName", "localhost")
    setProperty("dataSource.portNumber", "5431")
    setProperty("dataSource.user", "test")
    setProperty("dataSource.password", "password")
    this["dataSource.logWriter"] = PrintWriter(System.out)
}