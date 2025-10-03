import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class TaskDAO {

    fun getAllTasks(titleFilter: String? = null): List<Task> {
        return transaction {
            val query = when (titleFilter) {
                null -> Tasks.selectAll()
                else -> Tasks.select { Tasks.title like "%$titleFilter%" }
            }

            query.map {
                Task(
                    id = it[Tasks.id].value,
                    title = it[Tasks.title],
                    description = it[Tasks.description]
                )
            }
        }
    }

    fun getTaskById(id: Int): Task? {
        return transaction {
            Tasks.select { Tasks.id eq id }
                .map {
                    Task(
                        id = it[Tasks.id].value,
                        title = it[Tasks.title],
                        description = it[Tasks.description]
                    )
                }
                .singleOrNull()
        }
    }

    fun createTask(title: String, description: String?): Task {
        return transaction {
            val id = Tasks.insertAndGetId {
                it[Tasks.title] = title
                it[Tasks.description] = description
            }

            Task(
                id = id.value,
                title = title,
                description = description
            )
        }
    }

    fun updateTask(id: Int, title: String, description: String?): Boolean {
        return transaction {
            Tasks.update({ Tasks.id eq id }) {
                it[Tasks.title] = title
                it[Tasks.description] = description
            } > 0
        }
    }

    fun deleteTask(id: Int): Boolean {
        return transaction {
            Tasks.deleteWhere { Tasks.id eq id } > 0
        }
    }
}