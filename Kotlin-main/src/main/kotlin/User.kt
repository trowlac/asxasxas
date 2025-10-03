import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    ADMIN,
    USER
}

object Users : IntIdTable() {
    val username = varchar("username", 50).uniqueIndex()
    val password = varchar("password", 60)
    val role = varchar("role", 20).default(UserRole.USER.name)
}

class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)

    var username by Users.username
    var password by Users.password
    var role by Users.role.transform({ it.name }, { UserRole.valueOf(it) })
}

@Serializable
data class UserResponse(val id: Int, val username: String, val role: UserRole)