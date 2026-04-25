package com.firesin.xuipanel.feature.panels

import app.cash.turbine.test
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.feature.panels.ui.PanelsListUiState
import com.firesin.xuipanel.feature.panels.ui.PanelsListViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PanelsListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: PanelRepository
    private lateinit var viewModel: PanelsListViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        every { repository.observeAll() } returns flowOf(emptyList())
        every { repository.observeActive() } returns flowOf(null)
        viewModel = PanelsListViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading, then Content with empty list`() = runTest {
        viewModel.uiState.test {
            val first = awaitItem()
            assertInstanceOf(PanelsListUiState.Loading::class.java, first)

            testDispatcher.scheduler.advanceUntilIdle()

            val second = awaitItem()
            assertInstanceOf(PanelsListUiState.Content::class.java, second)
            val content = second as PanelsListUiState.Content
            assertEquals(0, content.panels.size)
            assertNull(content.active)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Content state reflects panels from repository`() = runTest {
        val panel = fakePanel("id-1", isActive = true)
        every { repository.observeAll() } returns flowOf(listOf(panel))
        every { repository.observeActive() } returns flowOf(panel)
        viewModel = PanelsListViewModel(repository)

        viewModel.uiState.test {
            skipItems(1) // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem() as PanelsListUiState.Content
            assertEquals(1, state.panels.size)
            assertEquals(panel.id, state.active?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setActive delegates to repository`() = runTest {
        coEvery { repository.setActive(any()) } returns Result.Success(Unit)

        viewModel.setActive("id-1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { repository.setActive("id-1") }
    }

    @Test
    fun `setActive error emits errorMessage`() = runTest {
        coEvery { repository.setActive(any()) } returns Result.Failure(DomainError.InvalidCredentials)

        viewModel.errorMessage.test {
            skipItems(1) // initial null
            viewModel.setActive("id-1")
            testDispatcher.scheduler.advanceUntilIdle()
            val error = awaitItem()
            assertInstanceOf(DomainError.InvalidCredentials::class.java, error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `errorShown clears errorMessage`() = runTest {
        coEvery { repository.setActive(any()) } returns Result.Failure(DomainError.InvalidCredentials)

        viewModel.setActive("id-1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.errorShown()

        assertNull(viewModel.errorMessage.value)
    }

    private fun fakePanel(id: String, isActive: Boolean) = Panel(
        id = id,
        name = "Panel $id",
        baseUrl = "https://example.com",
        login = "admin",
        password = "pass",
        trustSelfSigned = false,
        isActive = isActive,
        createdAt = Instant.now(),
        lastLoginAt = null,
    )
}
