package com.firesin.xuipanel.feature.panels

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.common.TlsMode
import com.firesin.xuipanel.core.data.model.Panel
import com.firesin.xuipanel.core.data.model.PanelDraft
import com.firesin.xuipanel.core.data.repository.PanelRepository
import com.firesin.xuipanel.feature.panels.ui.AuthMode
import com.firesin.xuipanel.feature.panels.ui.PanelAddEditUiState
import com.firesin.xuipanel.feature.panels.ui.PanelAddEditViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PanelAddEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: PanelRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(panelId: String? = null): PanelAddEditViewModel {
        val savedState = SavedStateHandle(
            buildMap { if (panelId != null) put(PanelAddEditViewModel.ARG_PANEL_ID, panelId) }
        )
        return PanelAddEditViewModel(repository, savedState)
    }

    @Test
    fun `submit with valid form calls add and transitions to Saved`() = runTest {
        coEvery { repository.probeTwoFactor(any()) } returns Result.Success(false)
        coEvery { repository.add(any()) } returns Result.Success(fakePanel("new-id"))

        val vm = createViewModel()
        vm.updateName("My Panel")
        vm.updateBaseUrl("https://panel.example.com:2053")
        vm.updateLogin("admin")
        vm.updatePassword("secret")

        vm.uiState.test {
            skipItems(1) // initial Editing

            vm.submit()
            testDispatcher.scheduler.advanceUntilIdle()

            val saving = awaitItem()
            assertInstanceOf(PanelAddEditUiState.Saving::class.java, saving)

            val saved = awaitItem()
            assertInstanceOf(PanelAddEditUiState.Saved::class.java, saved)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify { repository.add(any<PanelDraft>()) }
    }

    @Test
    fun `submit with invalid credentials probe returns Editing with submitError`() = runTest {
        coEvery { repository.probeTwoFactor(any()) } returns Result.Success(false)
        coEvery { repository.add(any()) } returns Result.Failure(DomainError.InvalidCredentials)

        val vm = createViewModel()
        vm.updateName("My Panel")
        vm.updateBaseUrl("https://panel.example.com:2053")
        vm.updateLogin("admin")
        vm.updatePassword("wrongpass")

        vm.uiState.test {
            skipItems(1)

            vm.submit()
            testDispatcher.scheduler.advanceUntilIdle()

            val saving = awaitItem()
            assertInstanceOf(PanelAddEditUiState.Saving::class.java, saving)

            val editing = awaitItem() as PanelAddEditUiState.Editing
            assertNotNull(editing.submitError)
            assertInstanceOf(DomainError.InvalidCredentials::class.java, editing.submitError)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit with empty name shows validation error and does not call repository`() = runTest {
        val vm = createViewModel()
        vm.updateBaseUrl("https://panel.example.com:2053")
        vm.updateLogin("admin")
        vm.updatePassword("secret")
        // name left empty

        vm.uiState.test {
            skipItems(1)

            vm.submit()
            testDispatcher.scheduler.advanceUntilIdle()

            val editing = awaitItem() as PanelAddEditUiState.Editing
            assertNotNull(editing.errors.name)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { repository.add(any()) }
    }

    @Test
    fun `submit with userinfo in baseUrl shows validation error`() = runTest {
        val vm = createViewModel()
        vm.updateName("My Panel")
        vm.updateBaseUrl("https://user:pass@panel.example.com:2053")
        vm.updateLogin("admin")
        vm.updatePassword("secret")

        vm.uiState.test {
            skipItems(1)

            vm.submit()
            testDispatcher.scheduler.advanceUntilIdle()

            val editing = awaitItem() as PanelAddEditUiState.Editing
            assertNotNull(editing.errors.baseUrl)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { repository.add(any()) }
    }

    @Test
    fun `edit mode loads panel data from repository`() = runTest {
        val panel = fakePanel("edit-id")
        coEvery { repository.get("edit-id") } returns panel

        val vm = createViewModel(panelId = "edit-id")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.uiState.value as PanelAddEditUiState.Editing
        assert(state.isEditMode)
        assert(state.form.name == panel.name)
        assert(state.form.login == panel.login)
    }

    @Test
    fun `submit in edit mode calls update`() = runTest {
        val panel = fakePanel("edit-id")
        coEvery { repository.get("edit-id") } returns panel
        coEvery { repository.probeTwoFactor(any()) } returns Result.Success(false)
        coEvery { repository.update(any(), any()) } returns Result.Success(panel)

        val vm = createViewModel(panelId = "edit-id")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.uiState.test {
            skipItems(1)

            vm.submit()
            testDispatcher.scheduler.advanceUntilIdle()

            val saving = awaitItem()
            assertInstanceOf(PanelAddEditUiState.Saving::class.java, saving)

            val saved = awaitItem()
            assertInstanceOf(PanelAddEditUiState.Saved::class.java, saved)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify { repository.update("edit-id", any()) }
    }

    @Test
    fun `PinMismatch error shows dialog instead of submitError`() = runTest {
        coEvery { repository.probeTwoFactor(any()) } returns Result.Success(false)
        coEvery { repository.add(any()) } returns Result.Failure(
            DomainError.PinMismatch(panelId = "", observedSpki = "newSpki==")
        )

        val vm = createViewModel()
        vm.updateName("My Panel")
        vm.updateBaseUrl("https://panel.example.com:2053")
        vm.updateLogin("admin")
        vm.updatePassword("secret")

        vm.uiState.test {
            skipItems(1)

            vm.submit()
            testDispatcher.scheduler.advanceUntilIdle()

            skipItems(1) // Saving

            val editing = awaitItem() as PanelAddEditUiState.Editing
            assertNull(editing.submitError)
            assertNotNull(editing.pinMismatchDialog)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── 2FA tests ─────────────────────────────────────────────────────────────

    @Test
    fun `submit cookie panel - 2FA required and no OTP entered - shows OTP field`() = runTest {
        coEvery { repository.probeTwoFactor(any()) } returns Result.Success(true)

        val vm = createViewModel()
        vm.updateName("My Panel")
        vm.updateBaseUrl("https://panel.example.com:2053")
        vm.updateLogin("admin")
        vm.updatePassword("secret")

        vm.uiState.test {
            skipItems(1) // initial Editing

            vm.submit()
            testDispatcher.scheduler.advanceUntilIdle()

            val saving = awaitItem()
            assertInstanceOf(PanelAddEditUiState.Saving::class.java, saving)

            val editing = awaitItem() as PanelAddEditUiState.Editing
            assert(editing.form.twoFactorRequired) { "Expected twoFactorRequired=true" }
            assertNull(editing.submitError)

            cancelAndIgnoreRemainingEvents()
        }

        // probeLogin must NOT have been called yet
        coVerify(exactly = 0) { repository.add(any()) }
    }

    @Test
    fun `submit cookie panel - 2FA required with valid OTP - saves panel`() = runTest {
        // First submit: probeTwoFactor returns true → sets twoFactorRequired
        coEvery { repository.probeTwoFactor(any()) } returns Result.Success(true)
        coEvery { repository.add(any()) } returns Result.Success(fakePanel("new-id"))

        val vm = createViewModel()
        vm.updateName("My Panel")
        vm.updateBaseUrl("https://panel.example.com:2053")
        vm.updateLogin("admin")
        vm.updatePassword("secret")

        // First submit — triggers probe, OTP field appears
        vm.uiState.test {
            skipItems(1)
            vm.submit()
            testDispatcher.scheduler.advanceUntilIdle()
            skipItems(1) // Saving
            val editing = awaitItem() as PanelAddEditUiState.Editing
            assert(editing.form.twoFactorRequired)
            cancelAndIgnoreRemainingEvents()
        }

        // User enters OTP and re-submits
        vm.updateTwoFactorCode("123456")

        vm.uiState.test {
            skipItems(1)
            vm.submit()
            testDispatcher.scheduler.advanceUntilIdle()

            val saving = awaitItem()
            assertInstanceOf(PanelAddEditUiState.Saving::class.java, saving)

            val saved = awaitItem()
            assertInstanceOf(PanelAddEditUiState.Saved::class.java, saved)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            repository.add(
                match { draft: PanelDraft ->
                    draft.twoFactorCode == "123456" && draft.twoFactorEnabled
                },
            )
        }
    }

    @Test
    fun `submit panel with optional API token saves it alongside login_password`() = runTest {
        // The form no longer toggles between LOGIN / TOKEN modes — login/password
        // are always required and the API token is an optional Bearer accelerator.
        // The 2FA probe runs unconditionally because it relies on the cookie
        // session, which any modern panel needs anyway.
        coEvery { repository.probeTwoFactor(any()) } returns Result.Success(false)
        coEvery { repository.add(any()) } returns Result.Success(fakePanel("token-id"))

        val vm = createViewModel()
        vm.updateName("My Panel")
        vm.updateBaseUrl("https://panel.example.com:2053")
        vm.updateLogin("admin")
        vm.updatePassword("secret")
        vm.updateApiToken("mytoken123")

        vm.uiState.test {
            skipItems(1)
            vm.submit()
            testDispatcher.scheduler.advanceUntilIdle()

            skipItems(1) // Saving
            val saved = awaitItem()
            assertInstanceOf(PanelAddEditUiState.Saved::class.java, saved)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.probeTwoFactor(any()) }
        coVerify {
            repository.add(match { it.apiToken == "mytoken123" && it.login == "admin" })
        }
    }

    @Test
    fun `submit cookie panel - 2FA probe network failure - shows error`() = runTest {
        coEvery { repository.probeTwoFactor(any()) } returns Result.Failure(
            DomainError.Network(Exception("timeout"))
        )

        val vm = createViewModel()
        vm.updateName("My Panel")
        vm.updateBaseUrl("https://panel.example.com:2053")
        vm.updateLogin("admin")
        vm.updatePassword("secret")

        vm.uiState.test {
            skipItems(1)
            vm.submit()
            testDispatcher.scheduler.advanceUntilIdle()

            skipItems(1) // Saving

            val editing = awaitItem() as PanelAddEditUiState.Editing
            assertNotNull(editing.submitError)
            assertInstanceOf(DomainError.Network::class.java, editing.submitError)
            assert(!editing.form.twoFactorRequired)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── rePin + 2FA tests ─────────────────────────────────────────────────────

    @Test
    fun `requestRePin on 2FA panel - first call shows OTP field`() = runTest {
        val panel = fakePanel("repin-id")
        coEvery { repository.get("repin-id") } returns panel
        coEvery { repository.probeTwoFactor(any()) } returns Result.Success(true)

        val vm = createViewModel(panelId = "repin-id")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.uiState.test {
            skipItems(1) // loaded Editing

            vm.requestRePin()
            testDispatcher.scheduler.advanceUntilIdle()

            val saving = awaitItem()
            assertInstanceOf(PanelAddEditUiState.Saving::class.java, saving)

            val editing = awaitItem() as PanelAddEditUiState.Editing
            assert(editing.form.twoFactorRequired) { "Expected twoFactorRequired=true after requestRePin on 2FA panel" }
            assertNull(editing.submitError)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { repository.rePin(any(), any()) }
    }

    @Test
    fun `requestRePin on 2FA panel - second call with OTP completes repin`() = runTest {
        val panel = fakePanel("repin-id2")
        coEvery { repository.get("repin-id2") } returns panel
        coEvery { repository.probeTwoFactor(any()) } returns Result.Success(true)
        coEvery { repository.rePin(any(), any()) } returns Result.Success(panel)

        val vm = createViewModel(panelId = "repin-id2")
        testDispatcher.scheduler.advanceUntilIdle()

        // First call: probeTwoFactor returns true → twoFactorRequired set
        vm.uiState.test {
            skipItems(1)
            vm.requestRePin()
            testDispatcher.scheduler.advanceUntilIdle()
            skipItems(1) // Saving
            val editing = awaitItem() as PanelAddEditUiState.Editing
            assert(editing.form.twoFactorRequired)
            cancelAndIgnoreRemainingEvents()
        }

        // User enters OTP
        vm.updateTwoFactorCode("654321")

        // Second call: twoFactorRequired already true → goes straight to rePin
        vm.uiState.test {
            skipItems(1)
            vm.requestRePin()
            testDispatcher.scheduler.advanceUntilIdle()

            val saving = awaitItem()
            assertInstanceOf(PanelAddEditUiState.Saving::class.java, saving)

            val saved = awaitItem()
            assertInstanceOf(PanelAddEditUiState.Saved::class.java, saved)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            repository.rePin(
                "repin-id2",
                match { draft: PanelDraft -> draft.twoFactorCode == "654321" },
            )
        }
        // probeTwoFactor called only once (first requestRePin)
        coVerify(exactly = 1) { repository.probeTwoFactor(any()) }
    }

    @Test
    fun `requestRePin on non-2FA panel - repin called without OTP probe`() = runTest {
        val panel = fakePanel("repin-id3")
        coEvery { repository.get("repin-id3") } returns panel
        coEvery { repository.probeTwoFactor(any()) } returns Result.Success(false)
        coEvery { repository.rePin(any(), any()) } returns Result.Success(panel)

        val vm = createViewModel(panelId = "repin-id3")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.uiState.test {
            skipItems(1)
            vm.requestRePin()
            testDispatcher.scheduler.advanceUntilIdle()

            val saving = awaitItem()
            assertInstanceOf(PanelAddEditUiState.Saving::class.java, saving)

            val saved = awaitItem()
            assertInstanceOf(PanelAddEditUiState.Saved::class.java, saved)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify { repository.rePin("repin-id3", match { it.twoFactorCode == null }) }
    }

    @Test
    fun `requestRePin probe failure shows error without calling rePin`() = runTest {
        val panel = fakePanel("repin-id4")
        coEvery { repository.get("repin-id4") } returns panel
        coEvery { repository.probeTwoFactor(any()) } returns Result.Failure(
            DomainError.Network(Exception("timeout"))
        )

        val vm = createViewModel(panelId = "repin-id4")
        testDispatcher.scheduler.advanceUntilIdle()

        vm.uiState.test {
            skipItems(1)
            vm.requestRePin()
            testDispatcher.scheduler.advanceUntilIdle()

            skipItems(1) // Saving

            val editing = awaitItem() as PanelAddEditUiState.Editing
            assertNotNull(editing.submitError)
            assertInstanceOf(DomainError.Network::class.java, editing.submitError)

            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { repository.rePin(any(), any()) }
    }

    private fun fakePanel(id: String) = Panel(
        id = id,
        name = "Panel $id",
        baseUrl = "https://panel.example.com:2053",
        login = "admin",
        password = "pass",
        tlsMode = TlsMode.SYSTEM,
        pinnedSpkiSha256 = null,
        pinnedAt = null,
        isActive = false,
        createdAt = Instant.now(),
        lastLoginAt = null,
    )
}
