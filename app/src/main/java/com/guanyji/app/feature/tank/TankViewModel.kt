package com.guanyji.app.feature.tank

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.guanyji.app.data.local.*
import com.guanyji.app.data.repository.LocalRepository
import com.guanyji.app.core.time.dayBoundsFlow
import com.guanyji.app.data.repository.TankOrderKey
import com.guanyji.app.data.repository.encodeOrderIds
import com.guanyji.app.data.repository.preferredTemperatureUnit
import com.guanyji.app.data.repository.savedOrder
import com.guanyji.app.data.photo.ManagedPhotoFileStore
import com.guanyji.app.data.photo.PhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TankFishState {
    data object Loading : TankFishState
    data class Success(val fish: List<Fish>) : TankFishState
    data class Error(val message: String) : TankFishState
}

internal fun tankFishState(fish: List<Fish>? = null, error: Throwable? = null): TankFishState = when {
    error != null -> TankFishState.Error(error.message ?: "鱼只加载失败")
    fish == null -> TankFishState.Loading
    else -> TankFishState.Success(fish)
}

internal sealed interface TankCardsState {
    data object Loading : TankCardsState
    data class Success(val tanks: List<TankCardUiModel>) : TankCardsState
    data class Failed(val message: String) : TankCardsState
}

class TankViewModel(application: Application) : AndroidViewModel(application) {
    private val database = DatabaseFactory.create(application)
    private val repository = LocalRepository(database)
    val photoRepository = PhotoRepository(database, ManagedPhotoFileStore(application))

    /** 卡片加载失败后重新订阅用；每次自增都会重建整条卡片数据流。 */
    private val cardRetry = MutableStateFlow(0)

    /**
     * 鱼缸卡片数据。
     *
     * 日界来自 [dayBoundsFlow]：跨零点会自动换成新的自然日边界，
     * 不会像固定边界那样让「今日喂食」长期停留在前一天。
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    internal val cards = cardRetry.flatMapLatest {
        dayBoundsFlow()
            .flatMapLatest { (dayStart, dayEnd) ->
                combine(
                    repository.tanks(),
                    repository.latestWaterTestsByTank(),
                    repository.feedingCountsByTank(dayStart, dayEnd),
                    repository.settings().map(::preferredTemperatureUnit),
                ) { summaries, latestTests, feedingCounts, temperatureUnit ->
                    val testsByTank = latestTests.associateBy { it.test.tankId }
                    val feedingsByTank = feedingCounts.associate { it.tankId to it.feedingCount }
                    summaries.map { summary ->
                        tankCardModel(
                            summary = summary,
                            latestTest = testsByTank[summary.tank.id],
                            temperatureUnit = temperatureUnit,
                            todayFeedings = feedingsByTank[summary.tank.id] ?: 0,
                        )
                    }
                }
            }
            .map<List<TankCardUiModel>, TankCardsState> { TankCardsState.Success(it) }
            .onStart { emit(TankCardsState.Loading) }
            .catch { failure -> emit(TankCardsState.Failed(failure.message ?: "鱼缸卡片加载失败")) }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TankCardsState.Loading)

    /** 错误态的重试入口：重建卡片数据流，让用户不必退出页面。 */
    fun retryCards() {
        cardRetry.update { it + 1 }
    }

    /** 「自定义顺序」的已保存顺序；存 app_settings，规则见 ListOrderPreferences。 */
    val listOrder = repository.settings()
        .map { rows -> rows.savedOrder(TankOrderKey) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 拖拽松手或无障上移/下移后落盘；顺序保存失败只提示，不影响列表状态。 */
    fun saveListOrder(ids: List<String>, onError: (String) -> Unit = {}) = viewModelScope.launch {
        try {
            repository.saveSetting(AppSetting(TankOrderKey, encodeOrderIds(ids), System.currentTimeMillis()))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onError(error.message ?: "顺序保存失败")
        }
    }

    private val fishRetry = MutableStateFlow(0)

    fun save(tank: Tank, onSaved: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try { repository.saveTank(tank); CareNotificationScheduler.sync(getApplication()); onSaved() } catch (error: CancellationException) { throw error } catch (error: Exception) { onError(error.message ?: "保存失败") }
    }

    fun archive(id: String, confirmedWithFish: Boolean, onSaved: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try { repository.archiveTank(id, System.currentTimeMillis(), confirmedWithFish); CareNotificationScheduler.sync(getApplication()); onSaved() } catch (error: CancellationException) { throw error } catch (error: Exception) { onError(error.message ?: "归档失败") }
    }

    sealed interface SummaryState {
        data object Loading : SummaryState
        data class Found(val summary: TankSummary) : SummaryState
        data object Missing : SummaryState
        data class Failed(val message: String) : SummaryState
    }

    suspend fun tankOnce(id: String): Tank? = repository.tankOnce(id)

    fun summary(id: String?): kotlinx.coroutines.flow.StateFlow<SummaryState> =
        (id?.takeIf(String::isNotBlank)?.let(repository::tankSummary) ?: flowOf(null))
            .map<TankSummary?, SummaryState> { it?.let(SummaryState::Found) ?: SummaryState.Missing }
            .catch { emit(SummaryState.Failed(it.message ?: "鱼缸加载失败")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryState.Loading)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun fishState(id: String): kotlinx.coroutines.flow.StateFlow<TankFishState> = fishRetry
        .flatMapLatest {
            repository.fishInTank(id)
                .map<List<Fish>, TankFishState> { tankFishState(it) }
                .onStart { emit(TankFishState.Loading) }
                .catch { emit(tankFishState(error = it)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TankFishState.Loading)

    fun retryFish() { fishRetry.value += 1 }
    fun fish(id: String) = repository.fishInTank(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun tests(id: String) = repository.waterTestsForTank(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun changes(id: String) = repository.waterChangesForTank(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun feedings(id: String) = repository.feedingsForTank(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun growth(id: String) = repository.growthForTank(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun tanks() = repository.allTanks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun tasks() = repository.careTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun tasks(tankId: String) = repository.careTasksForTank(tankId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun photo(id: String?) = (id?.let(repository::photo) ?: flowOf(null)).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addFish(value: Fish, onSaved: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try { repository.saveFish(value); onSaved() } catch (error: CancellationException) { throw error } catch (error: Exception) { onError(error.message ?: "保存失败") }
    }

    fun addWaterTest(test: WaterTest, values: List<WaterTestValue>, onSaved: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try { repository.saveWaterTest(test, values); onSaved() } catch (error: CancellationException) { throw error } catch (error: Exception) { onError(error.message ?: "保存失败") }
    }

    /** 详情页一键喂食：默认当前时间、全缸、上一次饲料；返回事件 id 供撤销。 */
    fun recordQuickFeeding(tankId: String, foodType: String?, onSaved: (String) -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try {
            onSaved(repository.recordQuickFeeding(tankId, null, foodType))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onError(error.message ?: "喂食记录保存失败")
        }
    }

    fun undoFeeding(id: String, onDone: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try { repository.deleteFeeding(id); onDone() } catch (error: CancellationException) { throw error } catch (error: Exception) { onError(error.message ?: "撤销失败") }
    }

    fun addWaterChange(value: WaterChange, onSaved: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try { repository.saveWaterChange(value); onSaved() } catch (error: CancellationException) { throw error } catch (error: Exception) { onError(error.message ?: "保存失败") }
    }

    suspend fun waterChange(id: String) = repository.waterChange(id)

    fun addTask(value: CareTask, onSaved: () -> Unit, onError: (String) -> Unit) = viewModelScope.launch {
        try { repository.saveCareTask(value); onSaved() } catch (error: CancellationException) { throw error } catch (error: Exception) { onError(error.message ?: "保存失败") }
    }

    companion object {
        fun factory(application: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(TankViewModel::class.java))
                return TankViewModel(application) as T
            }
        }
    }
}
