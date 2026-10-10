package net.malusic.magickpvp

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID

data class PlayerData(val kits: Set<String>, val xp: Long)

class SqlPlayerStorage private constructor(
    private val source: HikariDataSource,
    type: StorageType
) {

    private val insertKitSql = when (type) {
        StorageType.SQLITE -> "INSERT OR IGNORE INTO $KITS (uuid, kit_id) VALUES (?, ?)"
        StorageType.MYSQL -> "INSERT IGNORE INTO $KITS (uuid, kit_id) VALUES (?, ?)"
    }

    private val addXpSql = when (type) {
        StorageType.SQLITE ->
            "INSERT INTO $PLAYERS (uuid, xp) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET xp = xp + excluded.xp"
        StorageType.MYSQL ->
            "INSERT INTO $PLAYERS (uuid, xp) VALUES (?, ?) ON DUPLICATE KEY UPDATE xp = xp + VALUES(xp)"
    }

    private val setXpSql = when (type) {
        StorageType.SQLITE ->
            "INSERT INTO $PLAYERS (uuid, xp) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET xp = excluded.xp"
        StorageType.MYSQL ->
            "INSERT INTO $PLAYERS (uuid, xp) VALUES (?, ?) ON DUPLICATE KEY UPDATE xp = VALUES(xp)"
    }

    private fun <T> prepared(sql: String, action: (PreparedStatement) -> T): T =
        source.connection.use { connection -> connection.prepareStatement(sql).use(action) }

    private fun <T> select(sql: String, uuid: UUID, read: (ResultSet) -> T): T =
        prepared(sql) { statement ->
            statement.setString(1, uuid.toString())
            statement.executeQuery().use(read)
        }

    private fun update(sql: String, vararg values: Any) {
        prepared(sql) { statement ->
            values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }

    fun load(uuid: UUID): PlayerData {
        val kits = select("SELECT kit_id FROM $KITS WHERE uuid = ?", uuid) { rows ->
            val found = HashSet<String>()
            while (rows.next()) found += rows.getString(1)
            found
        }
        val xp = select("SELECT xp FROM $PLAYERS WHERE uuid = ?", uuid) { rows ->
            if (rows.next()) rows.getLong(1) else 0L
        }
        return PlayerData(kits, xp)
    }

    fun addKit(uuid: UUID, kitId: String) = update(insertKitSql, uuid.toString(), kitId)

    fun removeKit(uuid: UUID, kitId: String) =
        update("DELETE FROM $KITS WHERE uuid = ? AND kit_id = ?", uuid.toString(), kitId)

    fun addKits(entries: Map<UUID, Set<String>>) {
        if (entries.isEmpty()) return
        source.connection.use { connection ->
            val previousAutoCommit = connection.autoCommit
            connection.autoCommit = false
            try {
                connection.prepareStatement(insertKitSql).use { statement ->
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

    fun addXp(uuid: UUID, amount: Long) = update(addXpSql, uuid.toString(), amount)

    fun setXp(uuid: UUID, amount: Long) = update(setXpSql, uuid.toString(), amount)

    fun close() {
        source.close()
    }

    companion object {
        private const val KITS = "magickpvp_kits"
        private const val PLAYERS = "magickpvp_players"

        private const val CREATE_KITS = """
            CREATE TABLE IF NOT EXISTS $KITS (
                uuid VARCHAR(36) NOT NULL,
                kit_id VARCHAR(128) NOT NULL,
                PRIMARY KEY (uuid, kit_id)
            )
        """

        private const val CREATE_PLAYERS = """
            CREATE TABLE IF NOT EXISTS $PLAYERS (
                uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                xp BIGINT NOT NULL DEFAULT 0
            )
        """

        fun open(plugin: PluginManager, settings: StorageSettings): SqlPlayerStorage {
            val config = HikariConfig()
            config.poolName = "MagicKPvP"
            config.connectionTimeout = 10_000L

            when (settings.type) {
                StorageType.SQLITE -> {
                    plugin.dataFolder.mkdirs()
                    val database = File(plugin.dataFolder, settings.sqliteFile)
                    config.driverClassName = "org.sqlite.JDBC"
                    config.jdbcUrl = "jdbc:sqlite:${database.absolutePath}"
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
                    connection.createStatement().use {
                        it.execute(CREATE_KITS)
                        it.execute(CREATE_PLAYERS)
                    }
                }
            } catch (failure: Exception) {
                source.close()
                throw failure
            }
            return SqlPlayerStorage(source, settings.type)
        }
    }
}
