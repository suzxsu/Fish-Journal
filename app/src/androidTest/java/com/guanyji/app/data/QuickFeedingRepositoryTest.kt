package com.guanyji.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guanyji.app.data.local.*
import com.guanyji.app.data.repository.LocalRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 详情页一键喂食与撤销的数据层测试。
 *
 * 与完整喂食表单共用 saveFeeding 的校验；撤销只删除这一条事件与关联对象。
 * 该测试使用内存数据库，不会接触真机数据。
 */
@RunWith(AndroidJUnit4::class)
class QuickFeedingRepositoryTest {
    private lateinit var db: FishDatabase
    private lateinit var repository: LocalRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FishDatabase::class.java).build()
        repository = LocalRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun quickFeedingForOneFishCreatesOneEventAndUndoRemovesIt() = runBlocking {
        repository.saveTank(tank("tank-a"))
        repository.saveFish(fish("fish-a", "tank-a"))

        val eventId = repository.recordQuickFeeding("tank-a", "fish-a", "颗粒饲料")

        val afterCreate = repository.feedingsForFish("fish-a", "tank-a").first()
        assertEquals(1, afterCreate.size)
        assertEquals(eventId, afterCreate.single().id)
        assertEquals("颗粒饲料", afterCreate.single().foodType)
        assertEquals(1, db.feeding().getTargets(eventId).size)

        repository.deleteFeeding(eventId)

        assertTrue(repository.feedingsForFish("fish-a", "tank-a").first().isEmpty())
        assertTrue(db.feeding().getTargets(eventId).isEmpty())
    }

    @Test
    fun quickFeedingForTankCreatesTankTargetAndKeepsOtherRecords() = runBlocking {
        repository.saveTank(tank("tank-a"))
        repository.saveFish(fish("fish-a", "tank-a"))
        val kept = repository.recordQuickFeeding("tank-a", "fish-a", null)
        val removed = repository.recordQuickFeeding("tank-a", null, "薄片")

        val afterCreate = repository.feedingsForTank("tank-a").first()
        assertEquals(2, afterCreate.size)
        val tankTarget = afterCreate.first { it.event.id == removed }.targets.single()
        assertEquals(FeedingTargetType.tank, tankTarget.targetType)
        assertEquals("tank-a", tankTarget.tankId)

        repository.deleteFeeding(removed)
        val remaining = repository.feedingsForTank("tank-a").first()
        assertEquals(1, remaining.size)
        assertEquals(kept, remaining.single().event.id)
    }

    private fun tank(id: String) = Tank(
        id = id,
        name = id,
        type = TankType.saltwater,
        photoId = null,
        capacityValue = 144.0,
        capacityUnit = "L",
        startedAt = 0L,
        notes = null,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun fish(id: String, tankId: String) = Fish(
        id = id,
        tankId = tankId,
        name = id,
        species = "小丑鱼",
        coverPhotoId = null,
        addedAt = 0L,
        birthDate = null,
        status = FishStatus.active,
        statusChangedAt = null,
        notes = null,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
