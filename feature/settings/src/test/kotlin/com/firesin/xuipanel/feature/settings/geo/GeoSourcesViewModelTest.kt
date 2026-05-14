package com.firesin.xuipanel.feature.settings.geo

import app.cash.turbine.test
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.core.xui.XuiClient
import com.firesin.xuipanel.core.xui.dto.CustomGeoResourceDto
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class GeoSourcesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: PanelRepository
    private lateinit var xuiClient: XuiClient

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        xuiClient = mockk()
        // Default stub: aliases endpoint returns empty list unless overridden per-test
        coEvery { xuiClient.fetchCustomGeoAliases(any(), any(), any(), any()) } returns
            Result.Success(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load_success_shows_content`() = runTest {
        val panel = fakePanel()
        val items = listOf(fakeGeoResource(1, "myips"), fakeGeoResource(2, "mysites"))
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchCustomGeoList(any(), any(), any(), any()) } returns
            Result.Success(items)

        val vm = GeoSourcesViewModel(repository, xuiClient)

        vm.uiState.test {
            skipItems(1) // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertInstanceOf(GeoSourcesUiState.Content::class.java, state)
            assertEquals(items, (state as GeoSourcesUiState.Content).items)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `load_failure_shows_error`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchCustomGeoList(any(), any(), any(), any()) } returns
            Result.Failure(DomainError.InvalidCredentials)

        val vm = GeoSourcesViewModel(repository, xuiClient)

        vm.uiState.test {
            skipItems(1) // Loading
            testDispatcher.scheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertInstanceOf(GeoSourcesUiState.Error::class.java, state)
            assertEquals(DomainError.InvalidCredentials, (state as GeoSourcesUiState.Error).error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete_success_refreshes_list`() = runTest {
        val panel = fakePanel()
        val items = listOf(fakeGeoResource(1, "myips"))
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchCustomGeoList(any(), any(), any(), any()) } returns
            Result.Success(items)
        coEvery { xuiClient.deleteCustomGeo(any(), any(), any(), any(), any()) } returns
            Result.Success(Unit)

        val vm = GeoSourcesViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.delete(1, "Удалено")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(atLeast = 2) { xuiClient.fetchCustomGeoList(any(), any(), any(), any()) }
        assertTrue(vm.uiState.value is GeoSourcesUiState.Content)
    }

    @Test
    fun `add_success_dismisses_dialog_and_refreshes`() = runTest {
        val panel = fakePanel()
        val items = listOf(fakeGeoResource(1, "newips"))
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchCustomGeoList(any(), any(), any(), any()) } returns
            Result.Success(items)
        coEvery { xuiClient.addCustomGeo(any(), any(), any(), any(), any()) } returns
            Result.Success(Unit)

        val vm = GeoSourcesViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        var onDoneCalled = false
        vm.add("geoip", "newips", "https://example.com/my.dat", onDone = { onDoneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(onDoneCalled)
        coVerify(atLeast = 2) { xuiClient.fetchCustomGeoList(any(), any(), any(), any()) }
        assertInstanceOf(GeoSourcesUiState.Content::class.java, vm.uiState.value)
        assertEquals(items, (vm.uiState.value as GeoSourcesUiState.Content).items)
    }

    @Test
    fun `no_active_panel_produces_NoActivePanel_state`() = runTest {
        every { repository.observeActive() } returns flowOf(null)

        val vm = GeoSourcesViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        assertInstanceOf(GeoSourcesUiState.NoActivePanel::class.java, vm.uiState.value)
    }

    @Test
    fun `aliases_populated_on_success`() = runTest {
        val panel = fakePanel()
        val aliasList = listOf("geoip:cn", "geoip:private", "geosite:google")
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchCustomGeoList(any(), any(), any(), any()) } returns
            Result.Success(emptyList())
        coEvery { xuiClient.fetchCustomGeoAliases(any(), any(), any(), any()) } returns
            Result.Success(aliasList)

        val vm = GeoSourcesViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(aliasList, vm.aliases.value)
    }

    @Test
    fun `aliases_stay_empty_on_failure`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel)
        coEvery { xuiClient.fetchCustomGeoList(any(), any(), any(), any()) } returns
            Result.Success(emptyList())
        coEvery { xuiClient.fetchCustomGeoAliases(any(), any(), any(), any()) } returns
            Result.Failure(DomainError.Network(RuntimeException("timeout")))

        val vm = GeoSourcesViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.aliases.value.isEmpty())
    }

    @Test
    fun `aliases_cleared_when_panel_becomes_null`() = runTest {
        val panel = fakePanel()
        every { repository.observeActive() } returns flowOf(panel, null)
        coEvery { xuiClient.fetchCustomGeoList(any(), any(), any(), any()) } returns
            Result.Success(emptyList())
        coEvery { xuiClient.fetchCustomGeoAliases(any(), any(), any(), any()) } returns
            Result.Success(listOf("geoip:cn"))

        val vm = GeoSourcesViewModel(repository, xuiClient)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.aliases.value.isEmpty())
        assertInstanceOf(GeoSourcesUiState.NoActivePanel::class.java, vm.uiState.value)
    }

    private fun fakePanel() = Panel(
        id = "p1",
        name = "Test Panel",
        baseUrl = "https://example.com",
        login = "admin",
        password = "secret",
        tlsMode = TlsMode.SYSTEM,
        pinnedSpkiSha256 = null,
        pinnedAt = null,
        isActive = true,
        createdAt = Instant.now(),
        lastLoginAt = null,
    )

    private fun fakeGeoResource(id: Int, alias: String) = CustomGeoResourceDto(
        id = id,
        type = "geoip",
        alias = alias,
        url = "https://example.com/$alias.dat",
    )
}
