import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class Task(val id: Int, val title: String, val description: String?)

@Serializable
data class CreateTaskRequest(val title: String, val description: String?)

@Serializable
data class ResponseMessage(val message: String)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class RegisterRequest(val username: String, val password: String)

@Serializable
data class AuthResponse(val token: String, val user: UserResponse)

fun Application.configureRouting() {
    val taskDAO = TaskDAO()
    val userDAO = UserDAO()

    routing {
        get("/") {
            call.respondText("Hello World!")
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val user = userDAO.getUserByUsername(request.username)

            if (user != null && userDAO.verifyPassword(request.password, user.password)) {
                val token = JwtConfig.makeToken(request.username, user.role.name) // ОБНОВЛЕНО: добавлена роль в токен
                call.respond(AuthResponse(token, UserResponse(user.id.value, user.username, user.role))) // ОБНОВЛЕНО: добавлена роль в ответ
            } else {
                call.respond(HttpStatusCode.Unauthorized, ResponseMessage("Invalid credentials"))
            }
        }

        // Защищенные маршруты (требуют JWT)
        authenticate("auth-jwt") {
            // Задачи (доступны всем авторизованным пользователям)
            get("/tasks") {
                val titleFilter = call.request.queryParameters["title"]
                val tasks = taskDAO.getAllTasks(titleFilter)
                call.respond(tasks)
            }

            get("/tasks/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ResponseMessage("Invalid id"))
                    return@get
                }
                val task = taskDAO.getTaskById(id)
                if (task == null) {
                    call.respond(HttpStatusCode.NotFound, ResponseMessage("Task not found"))
                } else {
                    call.respond(task)
                }
            }

            post("/tasks") {
                val request = call.receive<CreateTaskRequest>()

                if (request.title.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ResponseMessage("Title cannot be empty"))
                    return@post
                }

                val newTask = taskDAO.createTask(request.title, request.description)
                call.respond(HttpStatusCode.Created, newTask)
            }

            delete("/tasks/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ResponseMessage("Invalid id"))
                    return@delete
                }
                val removed = taskDAO.deleteTask(id)
                if (removed) {
                    call.respond(ResponseMessage("Task deleted"))
                } else {
                    call.respond(HttpStatusCode.NotFound, ResponseMessage("Task not found"))
                }
            }

            put("/tasks/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ResponseMessage("Invalid id"))
                    return@put
                }

                val request = call.receive<CreateTaskRequest>()
                val updated = taskDAO.updateTask(id, request.title, request.description)

                if (updated) {
                    call.respond(ResponseMessage("Task updated"))
                } else {
                    call.respond(HttpStatusCode.NotFound, ResponseMessage("Task not found"))
                }
            }

            // Профиль пользователя
            get("/profile") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal?.payload?.getClaim("username")?.asString()
                val user = username?.let { userDAO.getUserResponseByUsername(it) }

                if (user != null) {
                    call.respond(user)
                } else {
                    call.respond(HttpStatusCode.NotFound, ResponseMessage("User not found"))
                }
            }

            // Получить информацию о текущем пользователе
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal?.payload?.getClaim("username")?.asString()
                val user = username?.let { userDAO.getUserResponseByUsername(it) }

                if (user != null) {
                    call.respond(user)
                } else {
                    call.respond(HttpStatusCode.NotFound, ResponseMessage("User not found"))
                }
            }

            // Получить всех пользователей (только для админов)
            get("/users") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal?.payload?.getClaim("username")?.asString()
                val user = username?.let { userDAO.getUserResponseByUsername(it) }

                if (user?.role == UserRole.ADMIN) {
                    val users = userDAO.getAllUsers()
                    call.respond(users)
                } else {
                    call.respond(HttpStatusCode.Forbidden, ResponseMessage("Only administrators can access this resource"))
                }
            }

            // Получить пользователей по роли (только для админов)
            get("/users/role/{role}") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal?.payload?.getClaim("username")?.asString()
                val user = username?.let { userDAO.getUserResponseByUsername(it) }

                if (user?.role == UserRole.ADMIN) {
                    val roleParam = call.parameters["role"]?.uppercase()
                    val role = try {
                        UserRole.valueOf(roleParam ?: "")
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, ResponseMessage("Invalid role. Use ADMIN or USER"))
                        return@get
                    }

                    val users = userDAO.getUsersByRole(role)
                    call.respond(users)
                } else {
                    call.respond(HttpStatusCode.Forbidden, ResponseMessage("Only administrators can access this resource"))
                }
            }

            // Удалить пользователя (только для админов)
            delete("/users/{username}") {
                val principal = call.principal<JWTPrincipal>()
                val currentUsername = principal?.payload?.getClaim("username")?.asString()
                val currentUser = currentUsername?.let { userDAO.getUserResponseByUsername(it) }

                if (currentUser?.role == UserRole.ADMIN) {
                    val targetUsername = call.parameters["username"]
                    if (targetUsername == null) {
                        call.respond(HttpStatusCode.BadRequest, ResponseMessage("Username parameter is required"))
                        return@delete
                    }

                    // Не позволяем удалить самого себя
                    if (targetUsername == currentUsername) {
                        call.respond(HttpStatusCode.BadRequest, ResponseMessage("Cannot delete your own account"))
                        return@delete
                    }

                    val targetUser = userDAO.getUserByUsername(targetUsername)
                    if (targetUser != null) {
                        val deleted = userDAO.deleteUser(targetUser.id.value)
                        if (deleted) {
                            call.respond(ResponseMessage("User $targetUsername deleted successfully"))
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, ResponseMessage("Failed to delete user"))
                        }
                    } else {
                        call.respond(HttpStatusCode.NotFound, ResponseMessage("User not found"))
                    }
                } else {
                    call.respond(HttpStatusCode.Forbidden, ResponseMessage("Only administrators can access this resource"))
                }
            }
        }

        // Публичный эндпоинт для просмотра предопределенных пользователей (для тестирования)
        get("/predefined-users") {
            val users = listOf(
                mapOf("username" to "admin", "password" to "admin123", "role" to "ADMIN"),
                mapOf("username" to "user1", "password" to "user1123", "role" to "USER"),
                mapOf("username" to "user2", "password" to "user2123", "role" to "USER"),
                mapOf("username" to "alice", "password" to "alice123", "role" to "ADMIN"),
                mapOf("username" to "bob", "password" to "bob12345", "role" to "USER")
            )
            call.respond(users)
        }
    }
}