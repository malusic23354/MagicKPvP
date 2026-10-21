package net.malusic.magickpvp

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID

data class StatsData(val kills: Long = 0L, val deaths: Long = 0L, val bestStreak: Long = 0L, val streak: Long = 0L)

data class PlayerData(
    val kits: Set<String>,
    val xp: Long,
    val stats: StatsData,
    val advancements: Map<String, AdvancementProgress> = emptyMap()
)

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

    private val addKillSql = when (type) {
        StorageType.SQLITE ->
            "INSERT INTO $STATS (uuid, kills, deaths, best_streak) VALUES (?, 1, 0, 0) " +
                "ON CONFLICT(uuid) DO UPDATE SET kills = kills + 1"
        StorageType.MYSQL ->
            "INSERT INTO $STATS (uuid, kills, deaths, best_streak) VALUES (?, 1, 0, 0) " +
                "ON DUPLICATE KEY UPDATE kills = kills + 1"
    }

    private val addDeathSql = when (type) {
        StorageType.SQLITE ->
            "INSERT INTO $STATS (uuid, kills, deaths, best_streak) VALUES (?, 0, 1, 0) " +
                "ON CONFLICT(uuid) DO UPDATE SET deaths = deaths + 1"
        StorageType.MYSQL ->
            "INSERT INTO $STATS (uuid, kills, deaths, best_streak) VALUES (?, 0, 1, 0) " +
                "ON DUPLICATE KEY UPDATE deaths = deaths + 1"
    }

    private val setBestStreakSql = when (type) {
        StorageType.SQLITE ->
            "INSERT INTO $STATS (uuid, kills, deaths, best_streak) VALUES (?, 0, 0, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET best_streak = excluded.best_streak"
        StorageType.MYSQL ->
            "INSERT INTO $STATS (uuid, kills, deaths, best_streak) VALUES (?, 0, 0, ?) " +
                "ON DUPLICATE KEY UPDATE best_streak = VALUES(best_streak)"
    }

    private val setStreakSql = when (type) {
        StorageType.SQLITE ->
            "INSERT INTO $STATS (uuid, kills, deaths, best_streak, streak) VALUES (?, 0, 0, 0, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET streak = excluded.streak"
        StorageType.MYSQL ->
            "INSERT INTO $STATS (uuid, kills, deaths, best_streak, streak) VALUES (?, 0, 0, 0, ?) " +
                "ON DUPLICATE KEY UPDATE streak = VALUES(streak)"
    }

    private val addKitPurchaseSql = when (type) {
        StorageType.SQLITE ->
            "INSERT INTO $USAGE (kit_id, purchases) VALUES (?, 1) " +
                "ON CONFLICT(kit_id) DO UPDATE SET purchases = purchases + 1"
        StorageType.MYSQL ->
            "INSERT INTO $USAGE (kit_id, purchases) VALUES (?, 1) " +
                "ON DUPLICATE KEY UPDATE purchases = purchases + 1"
    }

    private val saveAdvancementSql = when (type) {
        StorageType.SQLITE ->
            "INSERT INTO $ADVANCEMENTS (uuid, advancement_id, progress, completed) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(uuid, advancement_id) DO UPDATE SET progress = excluded.progress, completed = excluded.completed"
        StorageType.MYSQL ->
            "INSERT INTO $ADVANCEMENTS (uuid, advancement_id, progress, completed) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE progress = VALUES(progress), completed = VALUES(completed)"
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
        val stats = select("SELECT kills, deaths, best_streak, streak FROM $STATS WHERE uuid = ?", uuid) { rows ->
            if (rows.next()) {
                StatsData(rows.getLong(1), rows.getLong(2), rows.getLong(3), rows.getLong(4))
            } else {
                StatsData()
            }
        }
        val advancements = select("SELECT advancement_id, progress, completed FROM $ADVANCEMENTS WHERE uuid = ?", uuid) { rows ->
            val found = HashMap<String, AdvancementProgress>()
            while (rows.next()) found[rows.getString(1)] = AdvancementProgress(rows.getLong(2), rows.getInt(3) != 0)
            found
        }
        return PlayerData(kits, xp, stats, advancements)
    }

    fun top(stat: String, limit: Int): List<Pair<UUID, Long>> {
        val (table, column) = when (stat) {
            "xp" -> PLAYERS to "xp"
            "kills" -> STATS to "kills"
            "deaths" -> STATS to "deaths"
            "streak" -> STATS to "best_streak"
            else -> return emptyList()
        }
        return prepared("SELECT uuid, $column FROM $table WHERE $column > 0 ORDER BY $column DESC LIMIT ?") { statement ->
            statement.setInt(1, limit)
            statement.executeQuery().use { rows ->
                val found = ArrayList<Pair<UUID, Long>>()
                while (rows.next()) {
                    val uuid = runCatching { UUID.fromString(rows.getString(1)) }.getOrNull() ?: continue
                    found += uuid to rows.getLong(2)
                }
                found
            }
        }
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

    fun addKitPurchase(kitId: String) = update(addKitPurchaseSql, kitId)

    fun kitUsage(): Map<String, Long> =
        prepared("SELECT kit_id, purchases FROM $USAGE") { statement ->
            statement.executeQuery().use { rows ->
                val found = HashMap<String, Long>()
                while (rows.next()) found[rows.getString(1)] = rows.getLong(2)
                found
            }
        }

    fun saveAdvancement(uuid: UUID, id: String, progress: Long, completed: Boolean) =
        update(saveAdvancementSql, uuid.toString(), id, progress, if (completed) 1 else 0)

    fun addXp(uuid: UUID, amount: Long) = update(addXpSql, uuid.toString(), amount)

    fun setXp(uuid: UUID, amount: Long) = update(setXpSql, uuid.toString(), amount)

    fun addKill(uuid: UUID) = update(addKillSql, uuid.toString())

    fun addDeath(uuid: UUID) = update(addDeathSql, uuid.toString())

    fun setBestStreak(uuid: UUID, amount: Long) = update(setBestStreakSql, uuid.toString(), amount)

    fun setStreak(uuid: UUID, amount: Long) = update(setStreakSql, uuid.toString(), amount)

    fun close() {
        source.close()
    }

    companion object {
        private const val KITS = "magickpvp_kits"
        private const val PLAYERS = "magickpvp_players"
        private const val STATS = "magickpvp_stats"
        private const val USAGE = "magickpvp_kit_usage"
        private const val ADVANCEMENTS = "magickpvp_advancements"

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

        private const val CREATE_STATS = """
            CREATE TABLE IF NOT EXISTS $STATS (
                uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                kills BIGINT NOT NULL DEFAULT 0,
                deaths BIGINT NOT NULL DEFAULT 0,
                best_streak BIGINT NOT NULL DEFAULT 0,
                streak BIGINT NOT NULL DEFAULT 0
            )
        """

        private const val CREATE_USAGE = """
            CREATE TABLE IF NOT EXISTS $USAGE (
                kit_id VARCHAR(128) NOT NULL PRIMARY KEY,
                purchases BIGINT NOT NULL DEFAULT 0
            )
        """

        private const val CREATE_ADVANCEMENTS = """
            CREATE TABLE IF NOT EXISTS $ADVANCEMENTS (
                uuid VARCHAR(36) NOT NULL,
                advancement_id VARCHAR(128) NOT NULL,
                progress BIGINT NOT NULL DEFAULT 0,
                completed INT NOT NULL DEFAULT 0,
                PRIMARY KEY (uuid, advancement_id)
            )
        """

        private const val STREAK_COLUMN = "streak"

        private fun migrateStatsStreak(connection: java.sql.Connection, type: StorageType) {
            val exists = when (type) {
                StorageType.SQLITE ->
                    connection.prepareStatement("SELECT 1 FROM pragma_table_info(?) WHERE name = ?").use { statement ->
                        statement.setString(1, STATS)
                        statement.setString(2, STREAK_COLUMN)
                        statement.executeQuery().use { it.next() }
                    }
                StorageType.MYSQL ->
                    connection.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?"
                    ).use { statement ->
                        statement.setString(1, STATS)
                        statement.setString(2, STREAK_COLUMN)
                        statement.executeQuery().use { rows -> rows.next() && rows.getLong(1) > 0L }
                    }
            }
            if (!exists) {
                connection.createStatement().use {
                    it.execute("ALTER TABLE $STATS ADD COLUMN $STREAK_COLUMN BIGINT NOT NULL DEFAULT 0")
                }
            }
        }

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
                        it.execute(CREATE_STATS)
                        it.execute(CREATE_USAGE)
                        it.execute(CREATE_ADVANCEMENTS)
                    }
                    migrateStatsStreak(connection, settings.type)
                }
            } catch (failure: Exception) {
                source.close()
                throw failure
            }
            return SqlPlayerStorage(source, settings.type)
        }
    }
}
