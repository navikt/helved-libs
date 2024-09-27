package libs.postgres

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import libs.postgres.concurrency.connection
import libs.postgres.concurrency.transaction
import libs.utils.Resource
import libs.utils.appLog
import libs.utils.secureLog
import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext

class Migrator(location: File, ctx: CoroutineContext) {
    private lateinit var files: List<File>

    init {
        if (!location.isDirectory) {
            throw MigrationException(MigrationError.NO_DIR)
        }

        location.listFiles()?.let { listFiles ->
            files = listFiles
                .filter { f -> f.isFile }
                .filter { f -> f.extension == "sql" }
        }

        if (files.isEmpty()) {
            throw MigrationException(MigrationError.NO_FILES)
        }

        runBlocking {
            withContext(ctx) {
                transaction {
                    val sql = Resource.read("/migrations.sql")
                    coroutineContext.connection.prepareStatement(sql).execute()
                    appLog.debug(sql)
                }
            }
        }
    }

    suspend fun migrate() {
        val migrations = Migration.all().sortedBy { it.version }
        val candidates = files.map(MigrationWithFile::from).sortedBy { it.migration.version }
        val candidateWithMigration = candidates.associateWith { (candidate, _) ->
            migrations.find { it.version == candidate.version }
        }

        // Versions are sequenced
        val sequences = candidates.windowed(2, 1) { (a, b) -> isValidSequence(a.migration, b.migration) }
        if (sequences.any { !it }) {
            throw MigrationException(MigrationError.VERSION_SEQ)
        }

        // Checksum is not changed
        val checksums = candidateWithMigration.filterNot { validateChecksum(it.value, it.key) }
        if (checksums.any()) {
            throw MigrationException(MigrationError.CHECKSUM)
        }

        // All applied migrations scripts are found in location
        if (!migrations.all { migration -> migration.version in candidates.map { it.migration.version } }) {
            throw MigrationException(MigrationError.MISSING_SCRIPT)
        }

        // Register new candidates
        candidateWithMigration.forEach { (candidate, migration) ->
            if (migration == null) {
                Migration.insert(candidate.migration)
            }
        }

        // To be migrated
        candidateWithMigration
            .filterValues { migration -> migration == null || !migration.success }
            .mapNotNull { (candidate, migration) ->
                try {
                    transaction {
                        migrate(candidate.file)

                        when (migration) {
                            null -> Migration.update(candidate.migration.copy(success = true))
                            else -> Migration.update(migration.copy(success = true))
                        }

                        appLog.info("Migrated ${candidate.file.name} successfully")
                    }
                } catch (e: Exception) {
                    appLog.info("Migration of ${candidate.file.name} failed")
                    secureLog.error("migration of ${candidate.file.name} failed", e)
                }
            }
    }

    private fun validateChecksum(migration: Migration?, candidate: MigrationWithFile): Boolean {
        return migration?.let { migration.checksum == candidate.migration.checksum } ?: true
    }

    private suspend fun migrate(file: File) {
        transaction {
            val sql = file.readText(Charset.forName("UTF-8"))
            coroutineContext.connection.prepareStatement(sql).execute()
            appLog.debug(sql)
        }
    }

    private fun isValidSequence(left: Migration, right: Migration): Boolean {
        return left.version == right.version - 1
    }
}

enum class MigrationError(val msg: String) {
    CHECKSUM("Checksum differs from existing migration"),
    NO_DIR("Specified location is not a directory"),
    VERSION_SEQ("A version was not incremented by 1"),
    FILENAME("Version must be included in sql-filename"),
    NO_FILES("no sql files found in location"),
    MISSING_SCRIPT("migration script applied is missing in location"),
}

class MigrationException(error: MigrationError) : RuntimeException(error.msg)

internal data class MigrationWithFile(val migration: Migration, val file: File) {
    companion object {
        fun from(file: File) = MigrationWithFile(Migration.from(file), file)
    }
}

internal data class Migration(
    val version: Int,
    val filename: String,
    val checksum: String,
    val created_at: LocalDateTime,
    val success: Boolean,
) {
    companion object {
        fun from(file: File) = Migration(
            version = version(file.name),
            filename = file.name,
            checksum = checksum(file),
            created_at = LocalDateTime.now(),
            success = false,
        )

        fun from(rs: ResultSet) = Migration(
            version = rs.getInt("version"),
            filename = rs.getString("filename"),
            checksum = rs.getString("checksum"),
            created_at = rs.getTimestamp("created_at").toLocalDateTime(),
            success = rs.getBoolean("success"),
        )

        suspend fun all(): List<Migration> = transaction {
            coroutineContext.connection
                .prepareStatement("SELECT * FROM migrations")
                .use { stmt ->
                    secureLog.debug(stmt.toString())
                    stmt.executeQuery().map(::from)
                }
        }

        suspend fun insert(migration: Migration) = transaction {
            coroutineContext.connection
                .prepareStatement("INSERT INTO migrations (version, filename, checksum, created_at, success) VALUES (?, ?, ?, ?, ?)")
                .use { stmt ->
                    stmt.setInt(1, migration.version)
                    stmt.setString(2, migration.filename)
                    stmt.setString(3, migration.checksum)
                    stmt.setObject(4, migration.created_at)
                    stmt.setBoolean(5, migration.success)
                    secureLog.debug(stmt.toString())
                    stmt.executeUpdate()
                }
        }

        suspend fun update(migration: Migration) = transaction {
            coroutineContext.connection
                .prepareStatement("UPDATE migrations SET success = ? WHERE version = ?")
                .use { stmt ->
                    stmt.setBoolean(1, migration.success)
                    stmt.setInt(2, migration.version)
                    secureLog.debug(stmt.toString())
                    stmt.executeUpdate()
                }
        }
    }
}

private val DIGIT_PATTERN = Regex("\\d")

private fun version(name: String): Int {
    return DIGIT_PATTERN.findAll(name)
        .map { it.value.toInt() }
        .firstOrNull() // first digit is the version
        ?: throw MigrationException(MigrationError.FILENAME)
}

private fun checksum(file: File): String {
    val md = MessageDigest.getInstance("MD5")
    val buffer = ByteArray(1024)
    val input = file.inputStream()
    while (true) {
        when (val read = input.read(buffer)) {
            -1 -> break
            else -> md.update(buffer, 0, read)
        }
    }
    return buildString {
        md.digest().forEach {
            append(String.format("%02x", it))
        }
    }
}
