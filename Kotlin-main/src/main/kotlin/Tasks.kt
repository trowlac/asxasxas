import org.jetbrains.exposed.dao.id.IntIdTable

object Tasks : IntIdTable() {
    val title = varchar("title", 255)
    val description = text("description").nullable()
}