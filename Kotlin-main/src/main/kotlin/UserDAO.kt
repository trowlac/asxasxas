import org.jetbrains.exposed.sql.transactions.transaction
import at.favre.lib.crypto.bcrypt.BCrypt
import org.jetbrains.exposed.sql.and

class UserDAO {

    fun createUser(username: String, password: String, role: UserRole = UserRole.USER): UserResponse? {
        return transaction {
            val existingUser = User.find { Users.username eq username }.firstOrNull()
            if (existingUser == null) {
                val hashedPassword = BCrypt.withDefaults().hashToString(12, password.toCharArray())

                val user = User.new {
                    this.username = username
                    this.password = hashedPassword
                    this.role = role
                }

                UserResponse(user.id.value, user.username, user.role)
            } else {
                null
            }
        }
    }

    fun createPredefinedUsers() {
        transaction {
            // Предопределенные пользователи
            val predefinedUsers = listOf(
                Triple("admin", "admin123", UserRole.ADMIN),
                Triple("user1", "user1123", UserRole.USER),
                Triple("user2", "user2123", UserRole.USER),
                Triple("alice", "alice123", UserRole.ADMIN),
                Triple("bob", "bob12345", UserRole.USER)
            )

            predefinedUsers.forEach { (username, password, role) ->
                val existingUser = User.find { Users.username eq username }.firstOrNull()
                if (existingUser == null) {
                    val hashedPassword = BCrypt.withDefaults().hashToString(12, password.toCharArray())
                    User.new {
                        this.username = username
                        this.password = hashedPassword
                        this.role = role
                    }
                    println("Created predefined user: $username with role: $role")
                }
            }
        }
    }

    fun getUserById(id: Int): UserResponse? {
        return transaction {
            User.findById(id)?.let { user ->
                UserResponse(user.id.value, user.username, user.role)
            }
        }
    }

    fun getUserByUsername(username: String): User? {
        return transaction {
            User.find { Users.username eq username }.firstOrNull()
        }
    }

    fun getUserResponseByUsername(username: String): UserResponse? {
        return transaction {
            User.find { Users.username eq username }.firstOrNull()?.let { user ->
                UserResponse(user.id.value, user.username, user.role)
            }
        }
    }

    fun updateUser(id: Int, username: String? = null, password: String? = null): Boolean {
        return transaction {
            User.findById(id)?.let { user ->
                username?.let {
                    val existingUser = User.find { (Users.username eq it) and (Users.id neq id) }.firstOrNull()
                    if (existingUser == null) {
                        user.username = it
                    } else {
                        return@transaction false
                    }
                }
                password?.let {
                    user.password = BCrypt.withDefaults().hashToString(12, it.toCharArray())
                }
                true
            } ?: false
        }
    }

    fun deleteUser(id: Int): Boolean {
        return transaction {
            User.findById(id)?.delete() != null
        }
    }

    fun verifyPassword(password: String, hashedPassword: String): Boolean {
        return BCrypt.verifyer().verify(password.toCharArray(), hashedPassword).verified
    }

    fun getAllUsers(): List<UserResponse> {
        return transaction {
            User.all().map { UserResponse(it.id.value, it.username, it.role) }
        }
    }

    fun getUsersByRole(role: UserRole): List<UserResponse> {
        return transaction {
            User.find { Users.role eq role.name }.map {
                UserResponse(it.id.value, it.username, it.role)
            }
        }
    }
}