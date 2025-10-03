import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.serialization.kotlinx.json.*

fun Application.module() {
    // Инициализация базы данных
    DatabaseFactory.init()

    // JWT аутентификация
    install(Authentication) {
        jwt("auth-jwt") {
            JwtConfig.configureJwtAuth(this)
        }
    }

    // JSON сериализация
    install(ContentNegotiation) {
        json()
    }

    // Настройка маршрутов
    configureRouting()
}

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}