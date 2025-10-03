import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Table

object DatabaseFactory {
    fun init() {
        Database.connect(
            url = "jdbc:sqlite:my_database.db",
            driver = "org.sqlite.JDBC"
        )

        transaction {
            // Удаляем старые таблицы и создаем заново
            SchemaUtils.drop(Users, Tasks)
            SchemaUtils.create(Users, Tasks)

            // Создаем предопределенных пользователей
            val userDAO = UserDAO()
            userDAO.createPredefinedUsers()
        }
    }
}