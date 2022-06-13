import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

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

internal class DollarReferenceQueryParameterAdapterTest {
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