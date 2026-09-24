package li.songe.gkd.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import li.songe.gkd.app
import li.songe.gkd.data.ActionLog
import li.songe.gkd.data.AppConfig
import li.songe.gkd.data.CategoryConfig
import li.songe.gkd.data.SubsConfig
import li.songe.gkd.data.SubsItem
import li.songe.gkd.util.dbFolder
import li.songe.gkd.util.json

@Database(
    version = 15,
    entities = [
        SubsItem::class,
        SubsConfig::class,
        CategoryConfig::class,
        ActionLog::class,
        AppConfig::class,
    ],
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = Migration7To8Spec::class),
        AutoMigration(from = 8, to = 9, spec = ActionLog.ActionLogSpec::class),
        AutoMigration(from = 9, to = 10, spec = Migration9To10Spec::class),
        AutoMigration(from = 10, to = 11, spec = Migration10To11Spec::class),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15, spec = Migration14To15Spec::class),
    ]
)
@TypeConverters(DbConverters::class)
abstract class AppDb : RoomDatabase() {
    abstract fun subsItemDao(): SubsItem.SubsItemDao
    abstract fun subsConfigDao(): SubsConfig.SubsConfigDao
    abstract fun appConfigDao(): AppConfig.AppConfigDao
    abstract fun categoryConfigDao(): CategoryConfig.CategoryConfigDao
    abstract fun actionLogDao(): ActionLog.ActionLogDao
}

@RenameColumn(
    tableName = "subs_config",
    fromColumnName = "subs_item_id",
    toColumnName = "subs_id"
)
@RenameColumn(
    tableName = "category_config",
    fromColumnName = "subs_item_id",
    toColumnName = "subs_id"
)
class Migration9To10Spec : AutoMigrationSpec

@DeleteColumn(
    tableName = "snapshot",
    columnName = "app_name"
)
@DeleteColumn(
    tableName = "snapshot",
    columnName = "app_version_code"
)
@DeleteColumn(
    tableName = "snapshot",
    columnName = "app_version_name"
)
class Migration10To11Spec : AutoMigrationSpec

@DeleteTable(tableName = "activity_log")
class Migration7To8Spec : AutoMigrationSpec

@DeleteTable(tableName = "snapshot")
@DeleteTable(tableName = "a11y_event_log")
@DeleteTable(tableName = "activity_log_v2")
@DeleteTable(tableName = "app_visit_log")
class Migration14To15Spec : AutoMigrationSpec

@Suppress("unused")
class DbConverters {
    @TypeConverter
    fun fromListStringToString(list: List<String>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    fun fromStringToList(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        return try {
            json.decodeFromString(value)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

object DbSet {
    private val db by lazy {
        Room.databaseBuilder(
            app,
            AppDb::class.java,
            dbFolder.resolve("gkd.db").absolutePath
        ).fallbackToDestructiveMigration(false).build()
    }
    val subsItemDao get() = db.subsItemDao()
    val subsConfigDao get() = db.subsConfigDao()
    val actionLogDao get() = db.actionLogDao()
    val categoryConfigDao get() = db.categoryConfigDao()
    val appConfigDao get() = db.appConfigDao()

    suspend fun <T> withTransaction(block: suspend () -> T): T = db.withTransaction(block)
}
