package net.malusic.magickpvp

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File
import java.util.UUID

data class PlayerData(val kits: Set<String>, val xp: Long)

interface PlayerStorage {
    fun load(uuid: UUID): PlayerData
    fun addKit(uuid: UUID, kitId: String)
    fun removeKit(uuid: UUID, kitId: String)
    fun addKits(entries: Map<UUID, Set<String>>)

    fun addXp(uuid: UUID, amount: Long)
    fun setXp(uuid: UUID, amount: Long)
    fun close()
}

class SqlPlayerStorage private constructor(
    private val source: HikariDataSource,
    type: StorageType
) : PlayerStorage {

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

    override fun load(uuid: UUID): PlayerData = source.connection.use { connection ->
        val kits = HashSet<String>()
        connection.prepareStatement("SELECT kit_id FROM $KITS WHERE uuid = ?").use { statement ->
            statement.setString(1, uuid.toString())
            statement.executeQuery().use { rows ->
                while (rows.next()) kits += rows.getString(1)
            }
        }
        val xp = connection.prepareStatement("SELECT xp FROM $PLAYERS WHERE uuid = ?").use { statement ->
            statement.setString(1, uuid.toString())
            statement.executeQuery().use { rows -> if (rows.next()) rows.getLong(1) else 0L }
        }
        PlayerData(kits, xp)
    }

    override fun addKit(uuid: UUID, kitId: String) {
        source.connection.use { connection ->
            connection.prepareStatement(insertKitSql).use { statement ->
                statement.setString(1, uuid.toString())
                statement.setString(2, kitId)
                statement.executeUpdate()
            }
        }
    }

    override fun removeKit(uuid: UUID, kitId: String) {
        source.connection.use { connection ->
            connection.prepareStatement("DELETE FROM $KITS WHERE uuid = ? AND kit_id = ?").use { statement ->
                statement.setString(1, uuid.toString())
                statement.setString(2, kitId)
                statement.executeUpdate()
            }
        }
    }

    override fun addKits(entries: Map<UUID, Set<String>>) {
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

    override fun addXp(uuid: UUID, amount: Long) = writeXp(addXpSql, uuid, amount)

    override fun setXp(uuid: UUID, amount: Long) = writeXp(setXpSql, uuid, amount)

    private fun writeXp(sql: String, uuid: UUID, amount: Long) {
        source.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, uuid.toString())
                statement.setLong(2, amount)
                statement.executeUpdate()
            }
        }
    }

    override fun close() {
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
