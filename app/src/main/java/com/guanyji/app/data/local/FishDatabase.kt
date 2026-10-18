package com.guanyji.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

class DatabaseConverters {
    @TypeConverter fun tankType(value: String?) = value?.let { TankType.valueOf(it) }
    @TypeConverter fun tankType(value: TankType?) = value?.name
    @TypeConverter fun tankStatus(value: String?) = value?.let { TankStatus.valueOf(it) }
    @TypeConverter fun tankStatus(value: TankStatus?) = value?.name
    @TypeConverter fun fishStatus(value: String?) = value?.let { FishStatus.valueOf(it) }
    @TypeConverter fun fishStatus(value: FishStatus?) = value?.name
    @TypeConverter fun fishEventType(value: String?) = value?.let { FishEventType.valueOf(it) }
    @TypeConverter fun fishEventType(value: FishEventType?) = value?.name
    @TypeConverter fun feedingTargetType(value: String?) = value?.let { FeedingTargetType.valueOf(it) }
    @TypeConverter fun feedingTargetType(value: FeedingTargetType?) = value?.name
    @TypeConverter fun careTaskType(value: String?) = value?.let { CareTaskType.valueOf(it) }
    @TypeConverter fun careTaskType(value: CareTaskType?) = value?.name
    @TypeConverter fun careTaskStatus(value: String?) = value?.let { CareTaskStatus.valueOf(it) }
    @TypeConverter fun careTaskStatus(value: CareTaskStatus?) = value?.name
    @TypeConverter fun careRecordType(value: String?) = value?.let { CareRecordType.valueOf(it) }
    @TypeConverter fun careRecordType(value: CareRecordType?) = value?.name
}

@Dao
interface TankDao {
    @Query("SELECT tanks.*, (SELECT COUNT(*) FROM fish WHERE fish.tankId = tanks.id AND fish.status IN ('active', 'quarantine')) AS activeFishCount, (SELECT MAX(completedAt) FROM water_changes WHERE water_changes.tankId = tanks.id) AS lastWaterChangeAt, (SELECT MAX(measuredAt) FROM water_tests WHERE water_tests.tankId = tanks.id) AS lastWaterTestAt FROM tanks ORDER BY tanks.createdAt DESC") fun observeAll(): Flow<List<TankSummary>>
    @Query("SELECT tanks.*, (SELECT COUNT(*) FROM fish WHERE fish.tankId = tanks.id AND fish.status IN ('active', 'quarantine')) AS activeFishCount, (SELECT MAX(completedAt) FROM water_changes WHERE water_changes.tankId = tanks.id) AS lastWaterChangeAt, (SELECT MAX(measuredAt) FROM water_tests WHERE water_tests.tankId = tanks.id) AS lastWaterTestAt FROM tanks WHERE tanks.id = :id") fun observeSummary(id: String): Flow<TankSummary?>
    @Query("SELECT * FROM tanks WHERE id = :id") suspend fun get(id: String): Tank?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: Tank)
    @androidx.room.Update suspend fun update(value: Tank)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAll(values: List<Tank>)
    @Query("DELETE FROM tanks WHERE id = :id") suspend fun delete(id: String)
}

data class TankSummary(
    @androidx.room.Embedded val tank: Tank,
    val activeFishCount: Int,
    val lastWaterChangeAt: Long?,
    val lastWaterTestAt: Long?,
)

@Dao
interface FishDao {
    @Query("SELECT fish.*, (SELECT MAX(measuredAt) FROM growth_records WHERE growth_records.fishId = fish.id AND lengthValue IS NOT NULL) AS lastMeasuredAt, (SELECT lengthValue FROM growth_records WHERE growth_records.fishId = fish.id AND lengthValue IS NOT NULL ORDER BY measuredAt DESC LIMIT 1) AS lastLength, (SELECT lengthUnit FROM growth_records WHERE growth_records.fishId = fish.id AND lengthValue IS NOT NULL ORDER BY measuredAt DESC LIMIT 1) AS lastLengthUnit, (SELECT COUNT(*) FROM feeding_events JOIN feeding_targets ON feeding_targets.feedingEventId = feeding_events.id WHERE feeding_events.fedAt >= :dayStart AND feeding_events.fedAt < :dayEnd AND (feeding_targets.fishId = fish.id OR (feeding_targets.targetType = 'tank' AND feeding_targets.tankId = fish.tankId))) AS todayFeedings FROM fish ORDER BY createdAt DESC") fun observeAll(dayStart: Long, dayEnd: Long): Flow<List<FishSummary>>
    @Query("SELECT fish.*, (SELECT MAX(measuredAt) FROM growth_records WHERE growth_records.fishId = fish.id AND lengthValue IS NOT NULL) AS lastMeasuredAt, (SELECT lengthValue FROM growth_records WHERE growth_records.fishId = fish.id AND lengthValue IS NOT NULL ORDER BY measuredAt DESC LIMIT 1) AS lastLength, (SELECT lengthUnit FROM growth_records WHERE growth_records.fishId = fish.id AND lengthValue IS NOT NULL ORDER BY measuredAt DESC LIMIT 1) AS lastLengthUnit, (SELECT COUNT(*) FROM feeding_events JOIN feeding_targets ON feeding_targets.feedingEventId = feeding_events.id WHERE feeding_events.fedAt >= :dayStart AND feeding_events.fedAt < :dayEnd AND (feeding_targets.fishId = fish.id OR (feeding_targets.targetType = 'tank' AND feeding_targets.tankId = fish.tankId))) AS todayFeedings FROM fish WHERE fish.id = :id") fun observeSummary(id: String, dayStart: Long, dayEnd: Long): Flow<FishSummary?>
    @Query("SELECT * FROM fish WHERE tankId = :tankId ORDER BY addedAt DESC") fun observeForTank(tankId: String): Flow<List<Fish>>
    @Query("SELECT COUNT(*) FROM fish WHERE tankId = :tankId AND status IN ('active', 'quarantine')") suspend fun countActiveForTank(tankId: String): Int
    @Query("SELECT * FROM fish WHERE tankId = :tankId AND status IN ('active', 'quarantine')") suspend fun activeForTank(tankId: String): List<Fish>
    @Query("SELECT * FROM fish WHERE id = :id") suspend fun get(id: String): Fish?
    @androidx.room.Update suspend fun update(value: Fish)
    @Query("SELECT * FROM fish ORDER BY createdAt DESC") fun observeAllRaw(): Flow<List<Fish>>
    @Query("SELECT * FROM fish ORDER BY createdAt DESC") fun observeAllRecords(): Flow<List<Fish>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertEvent(value: FishEvent)
    @Query("SELECT * FROM fish_events WHERE fishId = :fishId ORDER BY occurredAt DESC") fun observeEvents(fishId: String): Flow<List<FishEvent>>
    @Query("SELECT * FROM fish_events ORDER BY occurredAt") fun observeAllEvents(): Flow<List<FishEvent>>
    @Query("UPDATE fish SET tankId = :tankId, updatedAt = :updatedAt WHERE id = :fishId") suspend fun move(fishId: String, tankId: String, updatedAt: Long)
    @Query("UPDATE fish SET status = :status, statusChangedAt = :changedAt, deathDateKnown = CASE WHEN :status = 'deceased' THEN :dateKnown ELSE 1 END, deathDate = :deathDate, updatedAt = :updatedAt WHERE id = :fishId") suspend fun setStatus(fishId: String, status: FishStatus, changedAt: Long?, dateKnown: Boolean, deathDate: Long?, updatedAt: Long): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: Fish)
    @Query("DELETE FROM fish WHERE id = :id") suspend fun delete(id: String)
}

data class FishSummary(@androidx.room.Embedded val fish: Fish, val lastMeasuredAt: Long?, val lastLength: Double?, val lastLengthUnit: String?, val todayFeedings: Int)

@Dao
interface TankLookupDao {
    @Query("SELECT * FROM tanks ORDER BY createdAt DESC") fun observeAll(): Flow<List<Tank>>
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE id = :id") fun observe(id: String): Flow<Photo?>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: Photo)
    @Query("SELECT * FROM photos WHERE id = :id") suspend fun get(id: String): Photo?
    @Query("SELECT * FROM photos WHERE localPath = :path LIMIT 1") suspend fun getByPath(path: String): Photo?
    @Query("SELECT COUNT(*) FROM photos WHERE localPath = :path") suspend fun countByPath(path: String): Int
    @Query("SELECT (SELECT COUNT(*) FROM tanks WHERE photoId = :id) AS tankCovers, (SELECT COUNT(*) FROM fish WHERE coverPhotoId = :id) AS fishCovers, (SELECT COUNT(*) FROM growth_records WHERE photoId = :id) AS growthRecords") suspend fun countReferences(id: String): PhotoReferenceCounts
    @Query("DELETE FROM photos WHERE id = :id") suspend fun delete(id: String): Int
}

data class PhotoReferenceCounts(val tankCovers: Int, val fishCovers: Int, val growthRecords: Int) {
    val total: Int get() = tankCovers + fishCovers + growthRecords
}

@Dao
interface GrowthRecordDao {
    @Query("SELECT * FROM growth_records ORDER BY measuredAt") fun observeAll(): Flow<List<GrowthRecord>>
    @Query("SELECT * FROM growth_records WHERE fishId = :fishId ORDER BY measuredAt DESC") fun observeForFish(fishId: String): Flow<List<GrowthRecord>>
    @Query("SELECT growth_records.* FROM growth_records JOIN fish ON fish.id = growth_records.fishId WHERE fish.tankId = :tankId ORDER BY growth_records.measuredAt DESC") fun observeForTank(tankId: String): Flow<List<GrowthRecord>>
    @Query("SELECT * FROM growth_records WHERE id = :id") suspend fun get(id: String): GrowthRecord?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: GrowthRecord)
    @androidx.room.Update suspend fun update(value: GrowthRecord)
    @Query("DELETE FROM growth_records WHERE id = :id") suspend fun delete(id: String): Int
    @Query("SELECT * FROM growth_records WHERE fishId = :fishId AND lengthValue IS NOT NULL ORDER BY measuredAt DESC LIMIT 1") fun latestLength(fishId: String): Flow<GrowthRecord?>
    @Query("SELECT * FROM growth_records WHERE fishId = :fishId AND weightValue IS NOT NULL ORDER BY measuredAt DESC LIMIT 1") fun latestWeight(fishId: String): Flow<GrowthRecord?>
}

data class FeedingEventWithTargets(
    @androidx.room.Embedded val event: FeedingEvent,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "feedingEventId") val targets: List<FeedingTarget>,
)

data class TankFeedingCount(val tankId: String, val feedingCount: Int)

@Dao
interface FeedingDao {
    @Transaction
    @Query("SELECT * FROM feeding_events ORDER BY fedAt")
    fun observeAll(): Flow<List<FeedingEventWithTargets>>
    @Query("SELECT tankId AS tankId, COUNT(DISTINCT eventId) AS feedingCount FROM (SELECT id AS eventId, tankId AS tankId FROM feeding_events WHERE tankId IS NOT NULL AND fedAt >= :dayStart AND fedAt < :dayEnd UNION ALL SELECT feeding_events.id AS eventId, fish.tankId AS tankId FROM feeding_events JOIN feeding_targets ON feeding_targets.feedingEventId = feeding_events.id JOIN fish ON fish.id = feeding_targets.fishId WHERE feeding_events.tankId IS NULL AND feeding_events.fedAt >= :dayStart AND feeding_events.fedAt < :dayEnd) GROUP BY tankId")
    fun observeFeedingCountsByTank(dayStart: Long, dayEnd: Long): Flow<List<TankFeedingCount>>
    @Transaction
    @Query("SELECT * FROM feeding_events WHERE tankId = :tankId ORDER BY fedAt DESC")
    fun observeForTank(tankId: String): Flow<List<FeedingEventWithTargets>>
    @Transaction
    @Query("SELECT feeding_events.* FROM feeding_events WHERE EXISTS (SELECT 1 FROM feeding_targets WHERE feeding_targets.feedingEventId = feeding_events.id AND (feeding_targets.fishId = :fishId OR (feeding_targets.targetType = 'tank' AND feeding_targets.tankId = :tankId))) ORDER BY feeding_events.fedAt DESC")
    fun observeForFish(fishId: String, tankId: String): Flow<List<FeedingEvent>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertEvent(event: FeedingEvent)
    @androidx.room.Update suspend fun updateEvent(event: FeedingEvent)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTargets(targets: List<FeedingTarget>)
    @Query("DELETE FROM feeding_targets WHERE feedingEventId = :eventId") suspend fun deleteTargets(eventId: String)
    @Query("DELETE FROM feeding_events WHERE id = :id") suspend fun deleteEvent(id: String): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTarget(target: FeedingTarget)
    @Query("SELECT * FROM feeding_events WHERE id = :id") suspend fun getEvent(id: String): FeedingEvent?
    @Query("SELECT * FROM feeding_targets WHERE feedingEventId = :eventId") suspend fun getTargets(eventId: String): List<FeedingTarget>
    @Query("SELECT COUNT(*) FROM feeding_events WHERE tankId = :tankId") suspend fun countTankEvents(tankId: String): Int
}

@Dao
interface CommonFoodDao {
    @Query("SELECT * FROM common_foods ORDER BY name COLLATE NOCASE") fun observeAll(): Flow<List<CommonFood>>
    @Query("SELECT * FROM common_foods WHERE id = :id") suspend fun get(id: String): CommonFood?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(value: CommonFood): Long
}

data class WaterTestWithValues(
    @androidx.room.Embedded val test: WaterTest,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "waterTestId") val values: List<WaterTestValue>,
)

@Dao
interface WaterTestDao {
    @Transaction
    @Query("SELECT * FROM water_tests ORDER BY measuredAt")
    fun observeAll(): Flow<List<WaterTestWithValues>>
    @Transaction
    @Query("SELECT water_tests.* FROM water_tests WHERE water_tests.id = (SELECT latest.id FROM water_tests latest WHERE latest.tankId = water_tests.tankId ORDER BY latest.measuredAt DESC, latest.id DESC LIMIT 1) ORDER BY water_tests.measuredAt DESC")
    fun observeLatestByTank(): Flow<List<WaterTestWithValues>>
    @Transaction
    @Query("SELECT * FROM water_tests WHERE tankId = :tankId ORDER BY measuredAt DESC")
    fun observeForTank(tankId: String): Flow<List<WaterTestWithValues>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTest(test: WaterTest)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertValues(values: List<WaterTestValue>)
}

@Dao
interface WaterChangeDao {
    @Query("SELECT * FROM water_changes ORDER BY completedAt") fun observeAll(): Flow<List<WaterChange>>
    @Query("SELECT * FROM water_changes WHERE tankId = :tankId ORDER BY completedAt DESC") fun observeForTank(tankId: String): Flow<List<WaterChange>>
    @Query("SELECT * FROM water_changes WHERE id = :id") suspend fun get(id: String): WaterChange?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: WaterChange)
    @androidx.room.Update suspend fun update(value: WaterChange)
}

@Dao
interface CareDao {
    @Query("SELECT * FROM care_tasks ORDER BY plannedAt") fun observeTasks(): Flow<List<CareTask>>
    @Query("SELECT * FROM care_tasks ORDER BY plannedAt") fun observeAllTasks(): Flow<List<CareTask>>
    @Query("SELECT * FROM care_tasks") suspend fun allTasks(): List<CareTask>
    @Query("SELECT * FROM care_tasks WHERE id = :id") suspend fun getTask(id: String): CareTask?
    @Query("SELECT * FROM care_tasks WHERE plannedAt >= :from AND plannedAt < :to ORDER BY plannedAt") fun observeTasksBetween(from: Long, to: Long): Flow<List<CareTask>>
    @Query("SELECT * FROM care_records ORDER BY completedAt DESC") fun observeAllRecords(): Flow<List<CareRecord>>
    @Query("SELECT * FROM care_tasks WHERE tankId = :tankId ORDER BY plannedAt") fun observeTasksForTank(tankId: String): Flow<List<CareTask>>
    @Query("UPDATE care_tasks SET status = 'cancelled', updatedAt = :updatedAt WHERE status IN ('pending', 'overdue') AND (tankId = :tankId OR fishId IN (SELECT id FROM fish WHERE tankId = :tankId))") suspend fun cancelPendingForTank(tankId: String, updatedAt: Long)
    @Query("UPDATE care_tasks SET status = 'cancelled', updatedAt = :updatedAt WHERE status IN ('pending', 'overdue') AND fishId = :fishId") suspend fun cancelPendingForFish(fishId: String, updatedAt: Long)
    @Query("UPDATE care_tasks SET status = 'cancelled', updatedAt = :updatedAt WHERE status IN ('pending', 'overdue') AND type = 'salinity_test' AND (tankId = :tankId OR fishId IN (SELECT id FROM fish WHERE tankId = :tankId))") suspend fun cancelSalinityTasks(tankId: String, updatedAt: Long)
    @Query("UPDATE care_rules SET enabled = 0, updatedAt = :updatedAt WHERE enabled = 1 AND (tankId = :tankId OR fishId IN (SELECT id FROM fish WHERE tankId = :tankId))") suspend fun disableRulesForTank(tankId: String, updatedAt: Long)
    @Query("SELECT * FROM care_records WHERE tankId = :tankId ORDER BY completedAt DESC") fun observeRecords(tankId: String): Flow<List<CareRecord>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTask(value: CareTask)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRule(value: CareRule)
    @androidx.room.Update suspend fun updateRule(value: CareRule)
    @Query("SELECT * FROM care_rules WHERE id = :id") suspend fun getRule(id: String): CareRule?
    @Query("SELECT * FROM care_rules WHERE enabled = 1") suspend fun enabledRules(): List<CareRule>
    @Query("SELECT * FROM care_rules") suspend fun allRules(): List<CareRule>
    @Query("SELECT * FROM care_tasks WHERE ruleId = :ruleId AND status IN ('pending','overdue') ORDER BY plannedAt") suspend fun openTasksForRule(ruleId: String): List<CareTask>
    @Query("SELECT * FROM care_tasks WHERE ruleId = :ruleId AND instanceKey = :key LIMIT 1") suspend fun getRuleInstance(ruleId: String, key: String): CareTask?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRecord(value: CareRecord)
    @Query("UPDATE care_tasks SET status = :status, updatedAt = :updatedAt WHERE id = :id AND status IN ('pending', 'overdue')") suspend fun setOpenTaskStatus(id: String, status: CareTaskStatus, updatedAt: Long): Int
    @Query("SELECT * FROM care_tasks WHERE notes = :marker ORDER BY createdAt DESC LIMIT 1") suspend fun getTaskForWaterChangeMarker(marker: String): CareTask?
    @androidx.room.Update suspend fun updateTask(value: CareTask)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM app_settings ORDER BY key") fun observeAll(): Flow<List<AppSetting>>
    @Query("SELECT * FROM app_settings WHERE `key` = :key") suspend fun get(key: String): AppSetting?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: AppSetting)
}

@Database(
    entities = [Tank::class, Fish::class, FishEvent::class, Photo::class, GrowthRecord::class, FeedingEvent::class, FeedingTarget::class, CommonFood::class, WaterTest::class, WaterTestValue::class, WaterChange::class, CareTask::class, CareRule::class, CareRecord::class, AppSetting::class],
    version = 8,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class FishDatabase : RoomDatabase() {
    abstract fun tanks(): TankDao
    abstract fun fish(): FishDao
    abstract fun tankLookup(): TankLookupDao
    abstract fun photos(): PhotoDao
    abstract fun growthRecords(): GrowthRecordDao
    abstract fun feeding(): FeedingDao
    abstract fun commonFoods(): CommonFoodDao
    abstract fun waterTests(): WaterTestDao
    abstract fun waterChanges(): WaterChangeDao
    abstract fun care(): CareDao
    abstract fun settings(): SettingDao

    companion object {
        const val VERSION = 8
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE care_tasks ADD COLUMN ruleId TEXT")
                db.execSQL("ALTER TABLE care_tasks ADD COLUMN instanceKey TEXT")
                db.execSQL("ALTER TABLE care_tasks ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE care_tasks ADD COLUMN postponedFrom INTEGER")
                db.execSQL("ALTER TABLE care_tasks ADD COLUMN lastPostponedAt INTEGER")
                db.execSQL("ALTER TABLE care_tasks ADD COLUMN customTitle TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_care_tasks_ruleId_instanceKey ON care_tasks(ruleId, instanceKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS care_rules (id TEXT NOT NULL, tankId TEXT, fishId TEXT, type TEXT NOT NULL, intervalDays INTEGER NOT NULL, localHour INTEGER NOT NULL, localMinute INTEGER NOT NULL, timeZoneId TEXT NOT NULL, notes TEXT, reminderEnabled INTEGER NOT NULL, enabled INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, customTitle TEXT, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_care_rules_tankId ON care_rules(tankId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_care_rules_fishId ON care_rules(fishId)")
                db.execSQL("ALTER TABLE tanks ADD COLUMN salinityManagementEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE water_changes ADD COLUMN beforeTemperature REAL")
                db.execSQL("ALTER TABLE water_changes ADD COLUMN afterTemperature REAL")
                db.execSQL("ALTER TABLE water_changes ADD COLUMN temperatureUnit TEXT")
                db.execSQL("ALTER TABLE water_changes ADD COLUMN beforeSalinity REAL")
                db.execSQL("ALTER TABLE water_changes ADD COLUMN afterSalinity REAL")
                db.execSQL("ALTER TABLE water_changes ADD COLUMN salinityUnit TEXT")
                db.execSQL("ALTER TABLE water_changes ADD COLUMN supplies TEXT")
            }
        }
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE water_test_values ADD COLUMN measuredAt INTEGER")
                db.execSQL("ALTER TABLE water_test_values ADD COLUMN method TEXT")
                db.execSQL("ALTER TABLE water_test_values ADD COLUMN notes TEXT")
                db.execSQL("ALTER TABLE water_test_values ADD COLUMN targetSource TEXT")
            }
        }
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS common_foods (id TEXT NOT NULL, name TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_common_foods_name ON common_foods(name)")
            }
        }
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fish ADD COLUMN sex TEXT")
                db.execSQL("ALTER TABLE fish ADD COLUMN source TEXT")
                db.execSQL("ALTER TABLE fish ADD COLUMN deathDateKnown INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE fish ADD COLUMN deathDate INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS fish_events (id TEXT NOT NULL, fishId TEXT NOT NULL, type TEXT NOT NULL, occurredAt INTEGER NOT NULL, tankId TEXT, targetTankId TEXT, status TEXT, dateKnown INTEGER NOT NULL, notes TEXT, PRIMARY KEY(id), FOREIGN KEY(fishId) REFERENCES fish(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(tankId) REFERENCES tanks(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_fish_events_fishId_occurredAt ON fish_events(fishId, occurredAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_fish_events_tankId ON fish_events(tankId)")
            }
        }
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_localPath` ON `photos` (`localPath`)")
            }
        }
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tanks ADD COLUMN lengthCm REAL")
                db.execSQL("ALTER TABLE tanks ADD COLUMN widthCm REAL")
                db.execSQL("ALTER TABLE tanks ADD COLUMN heightCm REAL")
                db.execSQL("ALTER TABLE tanks ADD COLUMN capacityIsEstimated INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tanks ADD COLUMN equipment TEXT")
            }
        }
    }
}
