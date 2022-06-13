import java.util.UUID

data class Entity(
    val contextId: UUID,
    val id: UUID,
    val textColumn: String,
    val textArrayColumn: List<String>,
    val jsonbColumn: JsonString
)