package net.malusic.magickpvp

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File
import java.util.UUID

interface KitStorage {
    fun load(uuid: UUID): Set<String>
    fun add(uuid: UUID, kitId: String)
    fun remove(uuid: UUID, kitId: String)
    fun addAll(entries: Map<UUID, Set<String>>)
    fun close()
}

class SqlKitStorage private constructor(
    private val source: HikariDataSource,
    type: StorageType
) : KitStorage {

    private val insertSql = when (type) {
        StorageType.SQLITE -> "INSERT OR IGNORE INTO $TABLE (uuid, kit_id) VALUES (?, ?)"
        StorageType.MYSQL -> "INSERT IGNORE INTO $TABLE (uuid, kit_id) VALUES (?, ?)"
    }

    override fun load(uuid: UUID): Set<String> = source.connection.use { connection ->
        connection.prepareStatement("SELECT kit_id FROM $TABLE WHERE uuid = ?").use { statement ->
            statement.setString(1, uuid.toString())
            statement.executeQuery().use { rows ->
                val result = HashSet<String>()
                while (rows.next()) result += rows.getString(1)
                result
            }
        }
    }

    override fun add(uuid: UUID, kitId: String) {
        source.connection.use { connection ->
            connection.prepareStatement(insertSql).use { statement ->
                statement.setString(1, uuid.toString())
                statement.setString(2, kitId)
                statement.executeUpdate()
            }
        }
    }

    override fun remove(uuid: UUID, kitId: String) {
        source.connection.use { connection ->
            connection.prepareStatement("DELETE FROM $TABLE WHERE uuid = ? AND kit_id = ?").use { statement ->
                statement.setString(1, uuid.toString())
                statement.setString(2, kitId)
                statement.executeUpdate()
            }
        }
    }

    override fun addAll(entries: Map<UUID, Set<String>>) {
        if (entries.isEmpty()) return
        source.connection.use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                connection.prepareStatement(insertSql).use { statement ->
                    for ((uuid, kits) in entries) {
                        for (kitId in kits) {
                            statement.setString(1, uuid.toString())
                            statement.setString(2, kitId)
                            statement.addBatch()
                        }
                    }
                    statement.executeBatch()
                }
                connection.commit()
            } catch (failure: Exception) {
                connection.rollback()
                throw failure
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    override fun close() {
        source.close()
    }

    companion object {
        private const val TABLE = "magickpvp_kits"

        private const val CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE (
                uuid VARCHAR(36) NOT NULL,
                kit_id VARCHAR(128) NOT NULL,
                PRIMARY KEY (uuid, kit_id)
            )
        """

        fun open(plugin: PluginManager, settings: StorageSettings): SqlKitStorage {
            val config = HikariConfig()
            config.poolName = "MagicKPvP"
            config.connectionTimeout = 10_000L

            when (settings.type) {
                StorageType.SQLITE -> {
                    plugin.dataFolder.mkdirs()
                    val database = File(plugin.dataFolder, settings.sqliteFile)
                    config.driverClassName = "org.sqlite.JDBC"
                    config.jdbcUrl = "jdbc:sqlite:${database.absolutePath}"
                    // SQLite allows a single writer, so one connection avoids "database is locked" errors.
                    config.maximumPoolSize = 1
                }
                StorageType.MYSQL -> {
                    val sslMode = if (settings.useSsl) "REQUIRED" else "DISABLED"
                    config.driverClassName = "com.mysql.cj.jdbc.Driver"
                    config.jdbcUrl = "jdbc:mysql://${settings.host}:${settings.port}/${settings.database}" +
                            "?sslMode=$sslMode&allowPublicKeyRetrieval=true&characterEncoding=utf8"
                    config.username = settings.username
                    config.password = settings.password
                    config.maximumPoolSize = settings.poolSize
                    config.minimumIdle = 1
                }
            }

            val source = HikariDataSource(config)
            try {
                source.connection.use { connection ->
                    connection.createStatement().use { it.execute(CREATE_TABLE) }
                }
            } catch (failure: Exception) {
                source.close()
                throw failure
            }
            return SqlKitStorage(source, settings.type)
        }
    }
}