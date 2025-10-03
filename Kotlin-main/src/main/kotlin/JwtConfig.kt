import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.auth.jwt.*
import java.util.*

object JwtConfig {
    private const val secret = "your-super-secret-key-change-in-production"
    private const val issuer = "your-issuer"
    private const val audience = "your-audience"
    private const val realm = "ktor app"
    private val algorithm = Algorithm.HMAC256(secret)

    fun makeToken(username: String, role: String = "USER"): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("username", username)
            .withClaim("role", role)
            .withExpiresAt(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000)) // 24 hours
            .sign(algorithm)
    }

    fun configureJwtAuth(auth: JWTAuthenticationProvider.Config) {
        auth.verifier(
            JWT.require(algorithm)
                .withAudience(audience)
                .withIssuer(issuer)
                .build()
        )
        auth.validate { credential ->
            if (credential.payload.audience.contains(audience)) {
                JWTPrincipal(credential.payload)
            } else {
                null
            }
        }
        auth.realm = realm
    }
}