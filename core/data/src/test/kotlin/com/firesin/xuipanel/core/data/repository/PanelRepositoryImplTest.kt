package com.firesin.xuipanel.core.data.repository

import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.db.dao.PanelDao
import com.firesin.xuipanel.core.data.db.entity.PanelEntity
import com.firesin.xuipanel.core.data.model.PanelDraft
import com.firesin.xuipanel.core.xui.XuiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PanelRepositoryImplTest {

    private lateinit var dao: PanelDao
    private lateinit var xuiClient: XuiClient
    private lateinit var repository: PanelRepositoryImpl

    private val draft = PanelDraft(
        name = "Test Panel",
        baseUrl = "https://panel.example.com:2053",
        login = "admin",
        password = "secret",
        trustSelfSigned = false,
    )

    @BeforeEach
    fun setUp() {
        dao = mockk(relaxed = true)
        xuiClient = mockk()
        repository = PanelRepositoryImpl(dao, xuiClient)
    }

    @Test
    fun `add successful when probe OK and no panels exist sets isActive true`() = runTest {
        coEvery { xuiClient.probeLogin(any()) } returns Result.Success(Unit)
        coEvery { dao.getAll() } returns emptyList()

        val result = repository.add(draft)

        assertInstanceOf(Result.Success::class.java, result)
        val panel = (result as Result.Success).data
        assertTrue(panel.isActive)
        assertEquals(draft.name, panel.name)
        assertNotNull(panel.lastLoginAt)
        coVerify(exactly = 1) { dao.insert(match { it.isActive == 1 }) }
    }

    @Test
    fun `add successful when panels already exist sets isActive false`() = runTest {
        coEvery { xuiClient.probeLogin(any()) } returns Result.Success(Unit)
        val existing = listOf(fakePanelEntity("existing-id", isActive = 1))
        coEvery { dao.getAll() } returns existing

        val result = repository.add(draft)

        assertInstanceOf(Result.Success::class.java, result)
        val panel = (result as Result.Success).data
        assertTrue(!panel.isActive)
        coVerify(exactly = 1) { dao.insert(match { it.isActive == 0 }) }
    }

    @Test
    fun `add returns InvalidCredentials when probe fails with 401 and does not insert`() = runTest {
        coEvery { xuiClient.probeLogin(any()) } returns Result.Failure(DomainError.InvalidCredentials)

        val result = repository.add(draft)

        assertInstanceOf(Result.Failure::class.java, result)
        assertEquals(DomainError.InvalidCredentials, (result as Result.Failure).error)
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `delete active panel reassigns active to first remaining panel by createdAt`() = runTest {
        val activeEntity = fakePanelEntity("active-id", isActive = 1, createdAt = 2000L)
        val otherEntity = fakePanelEntity("other-id", isActive = 0, createdAt = 1000L)

        coEvery { dao.getById("active-id") } returns activeEntity
        // After delete, getAll returns the remaining panels sorted by createdAt ASC.
        coEvery { dao.getAll() } returns listOf(otherEntity)

        val result = repository.delete("active-id")

        assertInstanceOf(Result.Success::class.java, result)
        coVerify { dao.deleteById("active-id") }
        coVerify { dao.setActivePanel("other-id") }
    }

    @Test
    fun `delete active panel with no remaining panels does not set any active`() = runTest {
        val activeEntity = fakePanelEntity("active-id", isActive = 1)
        coEvery { dao.getById("active-id") } returns activeEntity
        coEvery { dao.getAll() } returns emptyList()

        val result = repository.delete("active-id")

        assertInstanceOf(Result.Success::class.java, result)
        coVerify { dao.deleteById("active-id") }
        coVerify(exactly = 0) { dao.setActivePanel(any()) }
    }

    @Test
    fun `setActive delegates to setActivePanel and returns Success`() = runTest {
        val entity = fakePanelEntity("some-id", isActive = 0)
        coEvery { dao.getById("some-id") } returns entity

        val result = repository.setActive("some-id")

        assertInstanceOf(Result.Success::class.java, result)
        coVerify { dao.setActivePanel("some-id") }
    }

    @Test
    fun `setActive returns Unexpected when panel not found`() = runTest {
        coEvery { dao.getById("missing") } returns null

        val result = repository.setActive("missing")

        assertInstanceOf(Result.Failure::class.java, result)
        assertInstanceOf(DomainError.Unexpected::class.java, (result as Result.Failure).error)
    }

    private fun fakePanelEntity(
        id: String,
        isActive: Int,
        createdAt: Long = System.currentTimeMillis(),
    ) = PanelEntity(
        id = id,
        name = "Panel $id",
        baseUrl = "https://example.com",
        login = "admin",
        password = "pass",
        trustSelfSigned = 0,
        isActive = isActive,
        createdAt = createdAt,
        lastLoginAt = null,
    )
}
