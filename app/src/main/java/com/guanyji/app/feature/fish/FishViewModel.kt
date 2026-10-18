package com.guanyji.app.feature.fish

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.guanyji.app.data.local.*
import com.guanyji.app.data.photo.ManagedPhotoFileStore
import com.guanyji.app.data.photo.PhotoRepository
import com.guanyji.app.data.repository.DefaultLengthUnit
import com.guanyji.app.data.repository.FishOrderKey
import com.guanyji.app.data.repository.FishSortKey
import com.guanyji.app.data.repository.LocalRepository
import com.guanyji.app.data.repository.encodeOrderIds
import com.guanyji.app.data.repository.preferredLengthUnit
import com.guanyji.app.data.repository.savedOrder
import com.guanyji.app.data.repository.savedSortName
import com.guanyji.app.core.time.dayBoundsFlow
import com.guanyji.app.core.time.deviceDayBounds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface FishLoadState<out T> {
    data object Loading : FishLoadState<Nothing>
    data class Success<T>(val value: T) : FishLoadState<T>
    data object NotFound : FishLoadState<Nothing>
    data class Error(val message: String) : FishLoadState<Nothing>
}

internal fun <T : Any> fishLoadState(value: T?, error: Throwable? = null): FishLoadState<T> = when {
    error != null -> FishLoadState.Error(error.message ?: "读取失败")
    value == null -> FishLoadState.NotFound
    else -> FishLoadState.Success(value)
}

private sealed interface FlowLoadState<out T> {
    data object Loading : FlowLoadState<Nothing>
    data class Success<T>(val value: T) : FlowLoadState<T>
    data object NotFound : FlowLoadState<Nothing>
    data class Error(val message: String) : FlowLoadState<Nothing>
}

sealed interface FishRecordsState {
    data object Loading : FishRecordsState
    data object NotFound : FishRecordsState
    data class Error(val message: String) : FishRecordsState
    data class Success(
        val fish: Fish,
        val events: List<FishEvent>,
        val growth: List<GrowthRecord>,
        val feedings: List<FeedingEvent>,
    ) : FishRecordsState
}

class FishViewModel(application: Application) : AndroidViewModel(application) {
    private val database = DatabaseFactory.create(application)
    private val repository = LocalRepository(database)
    val photoRepository = PhotoRepository(database, ManagedPhotoFileStore(application))
    private fun dayBounds(): Pair<Long, Long> = deviceDayBounds()

    /** 鱼只列表的「今日喂食」取自然日统计，跨零点后需要跟着换边界。 */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val fish = dayBoundsFlow()
        .flatMapLatest { (start, end) -> repository.allFish(start, end) }
        .map<List<FishSummary>, List<FishSummary>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val tanks = repository.allTanks().map<List<Tank>, List<Tank>?> { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lengthUnit = repository.settings()
        .map(::preferredLengthUnit)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DefaultLengthUnit)

    /** 「自定义顺序」的已保存顺序；存 app_settings，规则见 ListOrderPreferences。 */
    val listOrder = repository.settings()
        .map { rows -> rows.savedOrder(FishOrderKey) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 拖拽松手或无障上移/下移后落盘；顺序保存失败只提示，不影响列表状态。 */
    fun saveListOrder(ids: List<String>, onError: (String) -> Unit = {}) = run({}, onError) {
        repository.saveSetting(AppSetting(FishOrderKey, encodeOrderIds(ids), System.currentTimeMillis()))
    }

    /** 上次选中的排序方式枚举名；为空时页面用默认排序。 */
    val listSort = repository.settings()
        .map { rows -> rows.savedSortName(FishSortKey) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveListSort(name: String) = run({}, {}) {
        repository.saveSetting(AppSetting(FishSortKey, name, System.currentTimeMillis()))
    }
    fun summary(id: String) = dayBounds().let { (start, end) -> repository.fishSummary(id, start, end) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun detailState(id: String) = dayBounds().let { (start, end) -> repository.fishSummary(id, start, end) }
        .map<FishSummary?, FishLoadState<FishSummary>> { fishLoadState(it) }
        .onStart { emit(FishLoadState.Loading) }
        .catch { emit(FishLoadState.Error(it.message ?: "鱼只加载失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FishLoadState.Loading)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun recordsState(id: String): kotlinx.coroutines.flow.StateFlow<FishRecordsState> {
        val (start, end) = dayBounds()
        val fishFlow = repository.fishSummary(id, start, end)
            .map<FishSummary?, FlowLoadState<FishSummary>> { it?.let { summary -> FlowLoadState.Success(summary) } ?: FlowLoadState.NotFound }
            .onStart { emit(FlowLoadState.Loading) }
            .catch { emit(FlowLoadState.Error(it.message ?: "鱼只加载失败")) }
        fun <T> observe(flow: Flow<T>): Flow<FlowLoadState<T>> = flow
            .map<T, FlowLoadState<T>> { FlowLoadState.Success(it) }
            .onStart { emit(FlowLoadState.Loading) }
            .catch { emit(FlowLoadState.Error(it.message ?: "记录加载失败")) }

        val eventsFlow = observe(repository.fishEvents(id))
        val growthFlow = observe(repository.growthRecords(id))
        val feedingsFlow = fishFlow.flatMapLatest { state ->
            val fish = (state as? FlowLoadState.Success)?.value?.fish
            if (fish == null) flowOf(FlowLoadState.Loading)
            else observe(repository.feedingsForFish(id, fish.tankId))
        }
        return combine(fishFlow, eventsFlow, growthFlow, feedingsFlow) { fish, events, growth, feedings ->
            when {
                fish is FlowLoadState.NotFound -> FishRecordsState.NotFound
                fish is FlowLoadState.Error -> FishRecordsState.Error(fish.message)
                events is FlowLoadState.Error -> FishRecordsState.Error(events.message)
                growth is FlowLoadState.Error -> FishRecordsState.Error(growth.message)
                feedings is FlowLoadState.Error -> FishRecordsState.Error(feedings.message)
                fish !is FlowLoadState.Success || events !is FlowLoadState.Success || growth !is FlowLoadState.Success || feedings !is FlowLoadState.Success -> FishRecordsState.Loading
                else -> FishRecordsState.Success(fish.value.fish, events.value, growth.value, feedings.value)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FishRecordsState.Loading)
    }
    fun events(id: String) = repository.fishEvents(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun growth(id: String) = repository.growthRecords(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun latestLength(id: String) = repository.latestLength(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun latestWeight(id: String) = repository.latestWeight(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun saveGrowth(value: GrowthRecord, onSaved: () -> Unit, onError: (String) -> Unit) = run(onSaved, onError) { repository.saveGrowthRecord(value) }
    fun deleteGrowth(id: String, onSaved: () -> Unit, onError: (String) -> Unit) = run(onSaved, onError) { repository.deleteGrowthRecord(id) }

    /** 详情页一键喂食：默认当前时间、当前鱼、上一次饲料；返回事件 id 供撤销。 */
    fun recordQuickFeeding(fish: Fish, foodType: String?, onSaved: (String) -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try {
            onSaved(repository.recordQuickFeeding(fish.tankId, fish.id, foodType))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onError(error.message ?: "喂食记录保存失败")
        }
    }

    fun undoFeeding(id: String, onDone: () -> Unit, onError: (String) -> Unit) = run(onDone, onError) {
        repository.deleteFeeding(id)
    }
    fun feedings(id: String, tankId: String) = repository.feedingsForFish(id, tankId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun waterTests(tankId: String) = repository.waterTestsForTank(tankId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun photo(id: String?) = (id?.let(repository::photo) ?: flowOf(null)).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun save(value: Fish, length: Double?, weight: Double?, onSaved: () -> Unit, onError: (String) -> Unit) = run(onSaved, onError) {
        val growth = if (length != null || weight != null) GrowthRecord(fishId = value.id, measuredAt = value.addedAt, lengthValue = length, lengthUnit = length?.let { "cm" }, weightValue = weight, weightUnit = weight?.let { "g" }, method = null, photoId = null, notes = null) else null
        repository.saveFish(value, growth)
    }
    fun move(id: String, tankId: String, date: Long, onSaved: () -> Unit, onError: (String) -> Unit) = run(onSaved, onError) { repository.moveFish(id, tankId, date); CareNotificationScheduler.sync(getApplication()) }
    fun setStatus(id: String, status: FishStatus, date: Long?, dateKnown: Boolean = true, onSaved: () -> Unit = {}, onError: (String) -> Unit = {}) = run(onSaved, onError) { repository.changeFishStatus(id, status, date, dateKnown); CareNotificationScheduler.sync(getApplication()) }
    fun archive(id: String, onSaved: () -> Unit, onError: (String) -> Unit) = run(onSaved, onError) { repository.archiveFish(id); CareNotificationScheduler.sync(getApplication()) }
    private fun run(onSaved: () -> Unit, onError: (String) -> Unit, action: suspend () -> Unit) = viewModelScope.launch {
        try { action(); onSaved() } catch (error: CancellationException) { throw error } catch (error: Exception) { onError(error.message ?: "操作失败") }
    }
    companion object {
        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T { require(modelClass.isAssignableFrom(FishViewModel::class.java)); return FishViewModel(application) as T }
        }
    }
}
