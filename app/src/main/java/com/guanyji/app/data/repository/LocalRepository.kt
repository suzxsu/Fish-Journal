package com.guanyji.app.data.repository

import androidx.room.withTransaction
import com.guanyji.app.data.local.*
import com.guanyji.app.feature.tank.calculateWaterChangeRatioPercent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LocalRepository(
    private val database: FishDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun allFish(dayStart: Long, dayEnd: Long): Flow<List<FishSummary>> = database.fish().observeAll(dayStart, dayEnd)
    fun allTanks(): Flow<List<Tank>> = database.tankLookup().observeAll()
    fun fishSummary(id: String, dayStart: Long, dayEnd: Long): Flow<FishSummary?> = database.fish().observeSummary(id, dayStart, dayEnd)
    fun fishEvents(id: String): Flow<List<FishEvent>> = database.fish().observeEvents(id)
    fun growthRecords(id: String): Flow<List<GrowthRecord>> = database.growthRecords().observeForFish(id)
    fun latestLength(id: String): Flow<GrowthRecord?> = database.growthRecords().latestLength(id)
    fun latestWeight(id: String): Flow<GrowthRecord?> = database.growthRecords().latestWeight(id)
    fun feedingsForFish(id: String, tankId: String): Flow<List<FeedingEvent>> = database.feeding().observeForFish(id, tankId)
    fun tanks(): Flow<List<TankSummary>> = database.tanks().observeAll()
    fun photo(id: String): Flow<Photo?> = database.photos().observe(id)
    fun tankSummary(id: String): Flow<TankSummary?> = database.tanks().observeSummary(id)
    suspend fun tankOnce(id: String): Tank? = withContext(ioDispatcher) { database.tanks().get(id) }
    fun fishInTank(tankId: String): Flow<List<Fish>> = database.fish().observeForTank(tankId)
    fun growthForFish(fishId: String): Flow<List<GrowthRecord>> = database.growthRecords().observeForFish(fishId)
    fun growthForTank(tankId: String): Flow<List<GrowthRecord>> = database.growthRecords().observeForTank(tankId)
    fun feedingsForTank(tankId: String): Flow<List<FeedingEventWithTargets>> = database.feeding().observeForTank(tankId)
    fun feedingCountsByTank(dayStart: Long, dayEnd: Long): Flow<List<TankFeedingCount>> = database.feeding().observeFeedingCountsByTank(dayStart, dayEnd)
    fun commonFoods(): Flow<List<CommonFood>> = database.commonFoods().observeAll()
    fun waterTestsForTank(tankId: String): Flow<List<WaterTestWithValues>> = database.waterTests().observeForTank(tankId)
    fun waterChangesForTank(tankId: String): Flow<List<WaterChange>> = database.waterChanges().observeForTank(tankId)
    suspend fun waterChange(id: String): WaterChange? = withContext(ioDispatcher) { database.waterChanges().get(id) }
    fun careTasks(): Flow<List<CareTask>> = database.care().observeTasks()
    fun careTasksForTank(tankId: String): Flow<List<CareTask>> = database.care().observeTasksForTank(tankId)
    fun careTasksBetween(from: Long, to: Long): Flow<List<CareTask>> = database.care().observeTasksBetween(from, to)
    fun careRecords(): Flow<List<CareRecord>> = database.care().observeAllRecords()
    fun settings(): Flow<List<AppSetting>> = database.settings().observeAll()
    fun statisticsFish(): Flow<List<Fish>> = database.fish().observeAllRecords()
    fun statisticsFishEvents(): Flow<List<FishEvent>> = database.fish().observeAllEvents()
    fun statisticsGrowth(): Flow<List<GrowthRecord>> = database.growthRecords().observeAll()
    fun statisticsFeedings(): Flow<List<FeedingEventWithTargets>> = database.feeding().observeAll()
    fun statisticsWaterTests(): Flow<List<WaterTestWithValues>> = database.waterTests().observeAll()
    fun latestWaterTestsByTank(): Flow<List<WaterTestWithValues>> = database.waterTests().observeLatestByTank()
    fun statisticsWaterChanges(): Flow<List<WaterChange>> = database.waterChanges().observeAll()
    fun statisticsCareTasks(): Flow<List<CareTask>> = database.care().observeAllTasks()
    fun statisticsCareRecords(): Flow<List<CareRecord>> = database.care().observeAllRecords()

    suspend fun saveTank(value: Tank) = withContext(ioDispatcher) {
        require(value.name.isNotBlank()) { "Tank name is required" }
        require(value.capacityValue == null || (value.capacityValue > 0 && value.capacityValue.isFinite())) { "Tank capacity must be positive and finite" }
        require((value.capacityValue == null) == (value.capacityUnit == null)) { "Capacity and unit must both be present or absent" }
        require(value.capacityUnit == null || value.capacityUnit.isNotBlank()) { "Capacity unit cannot be blank" }
        val normalized = value.copy(name = value.name.trim())
        require(listOfNotNull(normalized.lengthCm, normalized.widthCm, normalized.heightCm).all { it > 0 && it.isFinite() }) { "Tank dimensions must be positive" }
        require(normalized.capacityValue == null || normalized.capacityValue.isFinite())
        database.withTransaction {
            val previous = database.tanks().get(value.id)
            if (previous == null) database.tanks().insert(normalized) else {
                database.tanks().update(normalized)
                if (previous.salinityManagementEnabled && !normalized.salinityManagementEnabled) database.care().cancelSalinityTasks(value.id, System.currentTimeMillis())
            }
        }
    }

    suspend fun archiveTank(id: String, updatedAt: Long, confirmedWithActiveFish: Boolean) = withContext(ioDispatcher) {
        database.withTransaction {
            val tank = database.tanks().get(id) ?: error("Tank does not exist")
            require(tank.status == TankStatus.active) { "Tank is already archived" }
            val currentFish = database.fish().activeForTank(id)
            require(currentFish.isEmpty() || confirmedWithActiveFish) { "Confirm the archive plan for active fish" }
            database.tanks().update(tank.copy(status = TankStatus.archived, updatedAt = updatedAt))
            currentFish.forEach { fish ->
                val changed = database.fish().setStatus(fish.id, FishStatus.archived, updatedAt, true, null, updatedAt)
                check(changed == 1) { "Fish status changed during tank archive" }
                database.fish().insertEvent(FishEvent(fishId = fish.id, type = FishEventType.archived, occurredAt = updatedAt, tankId = id, targetTankId = null, status = fish.status))
            }
            database.care().cancelPendingForTank(id, updatedAt)
            database.care().disableRulesForTank(id, updatedAt)
        }
    }

    suspend fun saveFish(value: Fish, initialGrowth: GrowthRecord? = null) = withContext(ioDispatcher) {
        require(value.name.isNotBlank()) { "Fish name is required" }
        require(value.addedAt <= System.currentTimeMillis()) { "Added date cannot be in the future" }
        require(value.birthDate == null || value.birthDate <= value.addedAt) { "Birth date cannot be after added date" }
        require(initialGrowth == null || initialGrowth.fishId == value.id) { "Growth record must belong to fish" }
        database.withTransaction {
            val previous = database.fish().get(value.id)
            if (previous != null) {
                if (previous.tankId != value.tankId) {
                    require(database.tanks().get(value.tankId)?.status == TankStatus.active) { "Target tank is unavailable" }
                    database.care().cancelPendingForFish(value.id, System.currentTimeMillis())
                    database.fish().insertEvent(FishEvent(fishId = value.id, type = FishEventType.moved, occurredAt = System.currentTimeMillis(), tankId = previous.tankId, targetTankId = value.tankId, status = value.status, notes = "通过编辑档案转缸"))
                }
                database.fish().update(value.copy(name = value.name.trim()))
            } else {
                database.fish().insert(value.copy(name = value.name.trim()))
                database.fish().insertEvent(FishEvent(fishId = value.id, type = FishEventType.created, occurredAt = value.createdAt, tankId = value.tankId, targetTankId = null, status = value.status))
                initialGrowth?.let { database.growthRecords().insert(it) }
            }
        }
    }

    suspend fun changeFishStatus(id: String, status: FishStatus, occurredAt: Long?, dateKnown: Boolean) = withContext(ioDispatcher) {
        require(status != FishStatus.archived) { "Use archiveFish" }
        require(status != FishStatus.deceased || (dateKnown && occurredAt != null) || (!dateKnown && occurredAt == null)) { "Death date must be known or explicitly unknown" }
        database.withTransaction {
            val fish = database.fish().get(id) ?: error("Fish does not exist")
            require(fish.status != FishStatus.archived) { "Archived fish cannot change status" }
            val now = System.currentTimeMillis()
            require(status != FishStatus.deceased || !dateKnown || (occurredAt != null && occurredAt <= now)) { "Death date cannot be in the future" }
            require(status != FishStatus.transferred || occurredAt == null || occurredAt <= now) { "Transfer date cannot be in the future" }
            val changedAt = if (dateKnown) occurredAt else now
            database.fish().setStatus(id, status, changedAt, dateKnown, if (status == FishStatus.deceased && dateKnown) occurredAt else null, now)
            if (status == FishStatus.transferred || status == FishStatus.deceased || status == FishStatus.archived) database.care().cancelPendingForFish(id, now)
            database.fish().insertEvent(FishEvent(fishId = id, type = FishEventType.statusChanged, occurredAt = changedAt ?: now, tankId = fish.tankId, targetTankId = null, status = status, dateKnown = dateKnown))
        }
    }

    suspend fun moveFish(id: String, tankId: String, occurredAt: Long) = withContext(ioDispatcher) {
        require(occurredAt <= System.currentTimeMillis()) { "Transfer time cannot be in the future" }
        database.withTransaction {
            val fish = database.fish().get(id) ?: error("Fish does not exist")
            require(fish.status == FishStatus.active || fish.status == FishStatus.quarantine) { "Only active fish can be moved" }
            require(database.tanks().get(tankId)?.status == TankStatus.active) { "Target tank is unavailable" }
            database.fish().move(id, tankId, System.currentTimeMillis())
            database.care().cancelPendingForFish(id, System.currentTimeMillis())
            database.fish().insertEvent(FishEvent(fishId = id, type = FishEventType.moved, occurredAt = occurredAt, tankId = fish.tankId, targetTankId = tankId, status = fish.status))
        }
    }

    suspend fun archiveFish(id: String) = withContext(ioDispatcher) {
        database.withTransaction {
            val fish = database.fish().get(id) ?: error("Fish does not exist")
            require(fish.status != FishStatus.archived) { "Fish is already archived" }
            val now = System.currentTimeMillis()
            database.fish().setStatus(id, FishStatus.archived, now, true, null, now)
            database.care().cancelPendingForFish(id, now)
            database.fish().insertEvent(FishEvent(fishId = id, type = FishEventType.archived, occurredAt = now, tankId = fish.tankId, targetTankId = null, status = FishStatus.archived))
        }
    }

    suspend fun saveGrowthRecord(value: GrowthRecord) = withContext(ioDispatcher) {
        require(value.lengthValue != null || value.weightValue != null) { "At least one measurement is required" }
        require(value.lengthValue == null || (value.lengthValue > 0 && value.lengthValue.isFinite())) { "Length must be positive and finite" }
        require(value.weightValue == null || (value.weightValue > 0 && value.weightValue.isFinite())) { "Weight must be positive and finite" }
        require((value.lengthValue == null) == (value.lengthUnit == null)) { "Length and unit must both be present or absent" }
        require((value.weightValue == null) == (value.weightUnit == null)) { "Weight and unit must both be present or absent" }
        require(value.lengthUnit == null || value.lengthUnit.isNotBlank()) { "Length unit cannot be blank" }
        require(value.weightUnit == null || value.weightUnit.isNotBlank()) { "Weight unit cannot be blank" }
        require(value.measuredAt <= System.currentTimeMillis()) { "Measurement time cannot be in the future" }
        if (database.growthRecords().get(value.id) == null) database.growthRecords().insert(value) else {
            require(database.growthRecords().get(value.id)?.fishId == value.fishId) { "Growth record cannot be moved to another fish" }
            database.growthRecords().update(value)
        }
    }

    suspend fun deleteGrowthRecord(id: String) = withContext(ioDispatcher) {
        check(database.growthRecords().delete(id) == 1) { "Growth record no longer exists" }
    }

    suspend fun saveFeeding(event: FeedingEvent, targets: List<FeedingTarget>, foodName: String? = event.foodType) = withContext(ioDispatcher) {
        require(targets.isNotEmpty()) { "At least one feeding target is required" }
        require(targets.all { it.feedingEventId == event.id }) { "Targets must belong to the event" }
        require(targets.isNotEmpty()) { "At least one feeding target is required" }
        require(targets.all { (it.targetType == FeedingTargetType.tank && it.tankId != null && it.fishId == null) || (it.targetType == FeedingTargetType.fish && it.fishId != null && it.tankId == null) }) { "Target must reference exactly one matching target" }
        require(targets.map { it.fishId ?: "tank:${it.tankId}" }.distinct().size == targets.size) { "Duplicate feeding targets are not allowed" }
        require(targets.map { it.targetType }.distinct().size == 1) { "Tank-wide and individual targets cannot be mixed" }
        require(event.fedAt <= System.currentTimeMillis()) { "Feeding time cannot be in the future" }
        require(event.amountValue == null || (event.amountValue > 0 && event.amountValue.isFinite())) { "Amount must be positive" }
        require((event.amountValue == null) == (event.amountUnit == null)) { "Amount and unit must both be present or absent" }
        require(event.amountUnit == null || event.amountUnit.isNotBlank()) { "Amount unit cannot be blank" }
        database.withTransaction {
            if (event.tankId != null) require(database.tanks().get(event.tankId) != null) { "Tank does not exist" }
            targets.forEach { target ->
                if (target.targetType == FeedingTargetType.tank) require(target.tankId == event.tankId) { "Tank target must match event tank" }
                else {
                    val fish = database.fish().get(target.fishId!!)
                    require(fish != null) { "Fish does not exist" }
                    require(event.tankId == null || fish.tankId == event.tankId) { "Fish must belong to event tank" }
                }
            }
            val existing = database.feeding().getEvent(event.id)
            if (existing != null) {
                require(database.feeding().getTargets(event.id).all { old -> old.targetType == FeedingTargetType.tank || database.fish().get(old.fishId!!)?.tankId == event.tankId }) { "Feeding history cannot be moved to another tank" }
            }
            if (existing == null) database.feeding().insertEvent(event) else database.feeding().updateEvent(event)
            database.feeding().deleteTargets(event.id)
            database.feeding().insertTargets(targets)
            foodName?.trim()?.takeIf { it.isNotBlank() }?.let { name ->
                database.commonFoods().insert(CommonFood(name = name, createdAt = System.currentTimeMillis()))
            }
        }
    }

    /**
     * 一键喂食：只写一条真实喂食事件与一个关联对象，时间取当前时间。
     * 与完整表单共用 [saveFeeding] 的全部校验，不做任何统计上的“补默认值”。
     * 返回事件 id，供界面做 3 秒撤销。
     */
    suspend fun recordQuickFeeding(tankId: String, fishId: String?, foodType: String?): String {
        val now = System.currentTimeMillis()
        val cleanFood = foodType?.trim()?.takeIf { it.isNotBlank() }
        val event = FeedingEvent(
            tankId = tankId,
            fedAt = now,
            foodType = cleanFood,
            amountValue = null,
            amountUnit = null,
            result = null,
            notes = null,
        )
        val target = if (fishId == null) {
            FeedingTarget(feedingEventId = event.id, targetType = FeedingTargetType.tank, tankId = tankId, fishId = null, result = null)
        } else {
            FeedingTarget(feedingEventId = event.id, targetType = FeedingTargetType.fish, tankId = null, fishId = fishId, result = null)
        }
        saveFeeding(event, listOf(target), foodName = cleanFood)
        return event.id
    }

    /** 撤销一次误触的一键喂食；只删除这一条事件及其关联对象，不动其它记录。 */
    suspend fun deleteFeeding(id: String) = withContext(ioDispatcher) {
        database.withTransaction {
            database.feeding().deleteTargets(id)
            check(database.feeding().deleteEvent(id) == 1) { "Feeding record no longer exists" }
        }
    }

    suspend fun saveWaterTest(test: WaterTest, values: List<WaterTestValue>) = withContext(ioDispatcher) {
        require(values.isNotEmpty()) { "At least one water parameter is required" }
        require(values.all { it.waterTestId == test.id }) { "Values must belong to the test" }
        require(values.all { it.parameterCode.isNotBlank() && it.unit.isNotBlank() }) { "Parameter and unit are required" }
        require(values.map { it.parameterCode }.distinct().size == values.size) { "Water parameters must be unique per test" }
        require(values.all { it.value == null || it.value.isFinite() }) { "Water values must be finite" }
        require(values.all { it.targetMin == null || it.targetMax == null || it.targetMin <= it.targetMax }) { "Target range is invalid" }
        database.withTransaction {
            database.waterTests().insertTest(test)
            database.waterTests().insertValues(values)
        }
    }

    suspend fun saveWaterChange(value: WaterChange) = withContext(ioDispatcher) {
        require(value.volumeValue == null || (value.volumeValue > 0 && value.volumeValue.isFinite())) { "Volume must be positive" }
        require((value.volumeValue == null) == (value.volumeUnit == null)) { "Volume and unit must both be present or absent" }
        require(value.ratio == null || (value.ratio.isFinite() && value.ratio in 0.0..100.0)) { "Ratio must be between 0 and 100" }
        require(listOfNotNull(value.beforeTemperature, value.afterTemperature, value.beforeSalinity, value.afterSalinity).all { it.isFinite() && it > 0 }) { "Water measurements must be positive and finite" }
        require((value.beforeTemperature == null && value.afterTemperature == null) || !value.temperatureUnit.isNullOrBlank()) { "Temperature unit is required" }
        require((value.beforeSalinity == null && value.afterSalinity == null) || !value.salinityUnit.isNullOrBlank()) { "Salinity unit is required" }
        database.withTransaction {
            val tank = database.tanks().get(value.tankId) ?: error("Tank does not exist")
            val previous = database.waterChanges().get(value.id)
            require(previous == null || previous.tankId == value.tankId) { "Water change history cannot be moved to another tank" }
            val ratio = value.volumeValue?.let { volume -> calculateWaterChangeRatioPercent(volume, value.volumeUnit!!, tank.capacityValue, tank.capacityUnit) }
            val normalized = value.copy(ratio = ratio, notes = value.notes?.trim()?.takeIf { it.isNotEmpty() }, supplies = value.supplies?.trim()?.takeIf { it.isNotEmpty() })
            if (previous == null) database.waterChanges().insert(normalized) else database.waterChanges().update(normalized)

            val existingTask = database.care().getTaskForWaterChangeMarker(waterChangeTaskMarker(value.id))
            val now = System.currentTimeMillis()
            if (value.nextPlannedAt == null || tank.status == TankStatus.archived) {
                existingTask?.takeIf { it.status == CareTaskStatus.pending || it.status == CareTaskStatus.overdue }?.let {
                    database.care().setOpenTaskStatus(it.id, CareTaskStatus.cancelled, now)
                }
            } else if (existingTask == null) {
                database.care().insertTask(CareTask(tankId = value.tankId, fishId = null, type = CareTaskType.water_change, plannedAt = value.nextPlannedAt, recurrenceRule = null, notes = waterChangeTaskMarker(value.id), createdAt = now, updatedAt = now))
            } else if (existingTask.status == CareTaskStatus.pending || existingTask.status == CareTaskStatus.overdue) {
                database.care().updateTask(existingTask.copy(plannedAt = value.nextPlannedAt, updatedAt = now, notes = waterChangeTaskMarker(value.id)))
            }
        }
    }

    suspend fun saveCareTask(value: CareTask, intervalDays: Int? = null, reminderEnabled: Boolean = false) = withContext(ioDispatcher) {
        require(value.plannedAt > 0) { "计划时间无效" }
        require(intervalDays == null || intervalDays in 1..365) { "重复间隔需为 1 到 365 天" }
        require(value.type != CareTaskType.custom || !value.customTitle.isNullOrBlank()) { "请输入自定义任务名称" }
        database.withTransaction {
            val taskTank = value.tankId?.let { database.tanks().get(it) }
            require(value.tankId == null || taskTank != null) { "鱼缸不存在" }
            require(taskTank == null || taskTank.status == TankStatus.active) { "已归档鱼缸不能安排未来任务" }
            val fish = value.fishId?.let { database.fish().get(it) }
            require(value.fishId == null || fish != null) { "鱼只不存在" }
            require(fish == null || fish.status == FishStatus.active || fish.status == FishStatus.quarantine) { "已故或转出的鱼只不能安排未来任务" }
            if (fish != null && value.tankId != null) require(fish.tankId == value.tankId) { "鱼只不属于所选鱼缸" }
            if (value.type == CareTaskType.salinity_test) {
                val targetTank = value.tankId?.let { database.tanks().get(it) } ?: fish?.let { database.tanks().get(it.tankId) }
                require(targetTank?.let { it.type == TankType.saltwater && it.salinityManagementEnabled } == true) { "盐度提醒需要已启用盐度管理的海水缸" }
            }
            val now = System.currentTimeMillis()
            if (intervalDays == null) {
                val existing = database.care().getTask(value.id)
                if (existing == null) database.care().insertTask(value.copy(reminderEnabled = reminderEnabled, notes = value.notes?.trim()?.takeIf(String::isNotBlank), customTitle = value.customTitle?.trim()?.takeIf(String::isNotBlank)))
                else {
                    existing.ruleId?.let { ruleId ->
                        database.care().getRule(ruleId)?.let { database.care().updateRule(it.copy(enabled = false, updatedAt = now)) }
                        database.care().openTasksForRule(ruleId).filter { it.id != existing.id && it.plannedAt >= now }.forEach { database.care().setOpenTaskStatus(it.id, CareTaskStatus.cancelled, now) }
                    }
                    database.care().updateTask(value.copy(ruleId = null, instanceKey = null, recurrenceRule = null, reminderEnabled = reminderEnabled, updatedAt = now, notes = value.notes?.trim()?.takeIf(String::isNotBlank), customTitle = value.customTitle?.trim()?.takeIf(String::isNotBlank)))
                }
            } else {
                val zone = java.time.ZoneId.systemDefault()
                val local = java.time.Instant.ofEpochMilli(value.plannedAt).atZone(zone)
                val rule = CareRule(id = value.ruleId ?: value.id, tankId = value.tankId, fishId = value.fishId, type = value.type, intervalDays = intervalDays, localHour = local.hour, localMinute = local.minute, timeZoneId = zone.id, notes = value.notes, reminderEnabled = reminderEnabled, createdAt = now, updatedAt = now, customTitle = value.customTitle?.trim()?.takeIf(String::isNotBlank))
                val previousRule = database.care().getRule(rule.id)
                if (previousRule == null) database.care().insertRule(rule) else database.care().updateRule(rule)
                database.care().openTasksForRule(rule.id).forEach { old ->
                    if (old.plannedAt >= now && old.id != value.id) database.care().setOpenTaskStatus(old.id, CareTaskStatus.cancelled, now)
                }
                val key = taskOccurrenceKey(value.plannedAt, zone)
                val instance = value.copy(id = value.id, recurrenceRule = "daily-v1:$intervalDays", ruleId = rule.id, instanceKey = key, reminderEnabled = reminderEnabled, notes = rule.notes, customTitle = rule.customTitle, updatedAt = now)
                val byId = database.care().getTask(value.id)
                val existing = database.care().getRuleInstance(rule.id, key)
                if (byId != null) database.care().updateTask(instance)
                else if (existing == null) database.care().insertTask(instance)
                else database.care().updateTask(existing.copy(plannedAt = value.plannedAt, notes = value.notes, updatedAt = now, status = CareTaskStatus.pending))
            }
        }
    }

    suspend fun changeTaskStatus(taskId: String, status: CareTaskStatus, at: Long = System.currentTimeMillis()) = withContext(ioDispatcher) {
        require(status in setOf(CareTaskStatus.skipped, CareTaskStatus.cancelled))
        database.withTransaction {
            val task = database.care().getTask(taskId) ?: error("任务不存在")
            check(database.care().setOpenTaskStatus(taskId, status, at) == 1) { "任务已处理" }
            if (status == CareTaskStatus.skipped) scheduleNextOccurrence(task, at)
        }
    }

    suspend fun postponeTask(taskId: String, newTime: Long, at: Long = System.currentTimeMillis()) = withContext(ioDispatcher) {
        require(newTime > at) { "延期时间必须晚于现在" }
        database.withTransaction {
            val task = database.care().getTask(taskId) ?: error("任务不存在")
            check(task.status == CareTaskStatus.pending || task.status == CareTaskStatus.overdue) { "任务已处理" }
            database.care().updateTask(task.copy(plannedAt = newTime, status = CareTaskStatus.pending, updatedAt = at, instanceKey = task.ruleId?.let { taskOccurrenceKey(newTime, java.time.ZoneId.systemDefault()) }, postponedFrom = task.plannedAt, lastPostponedAt = at))
        }
    }

    suspend fun completeCareTask(taskId: String, record: CareRecord, updatedAt: Long) = withContext(ioDispatcher) {
        require(record.taskId == taskId) { "Record must reference the task" }
        database.withTransaction {
            val task = database.care().getTask(taskId) ?: error("任务不存在")
            require(record.tankId == task.tankId && record.fishId == task.fishId && record.type == task.type.toRecordType()) { "实际记录与任务对象不匹配" }
            check(database.care().setOpenTaskStatus(taskId, CareTaskStatus.completed, updatedAt) == 1) { "任务已处理" }
            database.care().insertRecord(record)
            scheduleNextOccurrence(task, updatedAt)
        }
    }

    suspend fun refreshOverdue(now: Long = System.currentTimeMillis()) = withContext(ioDispatcher) {
        database.care().allTasks().filter { it.status == CareTaskStatus.pending && it.plannedAt < now }.forEach { task -> database.care().updateTask(task.copy(status = CareTaskStatus.overdue, updatedAt = now)) }
    }

    private suspend fun scheduleNextOccurrence(task: CareTask, now: Long) {
        val rule = task.ruleId?.let { database.care().getRule(it) } ?: return
        if (!rule.enabled) return
        var next = nextRuleTime(task.plannedAt, rule.intervalDays, rule.localHour, rule.localMinute, java.time.ZoneId.of(rule.timeZoneId))
        while (next <= now) next = nextRuleTime(next, rule.intervalDays, rule.localHour, rule.localMinute, java.time.ZoneId.of(rule.timeZoneId))
        val key = taskOccurrenceKey(next, java.time.ZoneId.of(rule.timeZoneId))
        if (database.care().getRuleInstance(rule.id, key) == null) database.care().insertTask(CareTask(tankId = rule.tankId, fishId = rule.fishId, type = rule.type, plannedAt = next, recurrenceRule = "daily-v1:${rule.intervalDays}", notes = rule.notes, createdAt = now, updatedAt = now, ruleId = rule.id, instanceKey = key, reminderEnabled = rule.reminderEnabled, customTitle = rule.customTitle))
    }

    suspend fun savePhoto(value: Photo) = withContext(ioDispatcher) {
        require(value.localPath.isNotBlank() && !value.localPath.startsWith("/")) { "Photo path must be app-relative" }
        require(value.width == null || value.width > 0)
        require(value.height == null || value.height > 0)
        database.photos().insert(value)
    }

    suspend fun saveSetting(value: AppSetting) = withContext(ioDispatcher) {
        require(value.key.isNotBlank())
        database.settings().put(value)
    }
}

internal fun waterChangeTaskMarker(changeId: String) = "water-change:$changeId"

private fun CareTaskType.toRecordType() = CareRecordType.valueOf(name)
internal fun taskOccurrenceKey(time: Long, zone: java.time.ZoneId): String = java.time.Instant.ofEpochMilli(time).atZone(zone).toLocalDateTime().toString()
internal fun nextRuleTime(previous: Long, days: Int, hour: Int, minute: Int, zone: java.time.ZoneId): Long = java.time.Instant.ofEpochMilli(previous).atZone(zone).plusDays(days.toLong()).withHour(hour).withMinute(minute).withSecond(0).withNano(0).toInstant().toEpochMilli()
