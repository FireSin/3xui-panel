package com.firesin.xuipanel.feature.settings.update

import app.cash.turbine.test
import com.firesin.xuipanel.core.common.DomainError
import com.firesin.xuipanel.core.common.Result
import com.firesin.xuipanel.core.data.update.AppUpdateRepository
import com.firesin.xuipanel.core.data.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: AppUpdateViewModel

    private val availableInfo = UpdateInfo(
        currentVersion = "0.1.0",
        latestVersion = "0.2.0",
        isUpdateAvailable = true,
        releaseNotes = "What's new",
        apkUrl = "https://example.com/app.apk",
        apkSizeBytes = 5_000_000L,
        releasePageUrl = "https://github.com/releases/v0.2.0",
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `checkForUpdate transitions Idle to Checking to Available`() = runTest {
        val repo = FakeUpdateRepository(Result.Success(availableInfo))
        viewModel = AppUpdateViewModel(repo)

        viewModel.state.test {
            assertEquals(UpdateUiState.Idle, awaitItem())
            viewModel.checkForUpdate()
            assertEquals(UpdateUiState.Checking, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertInstanceOf(UpdateUiState.Available::class.java, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checkForUpdate transitions to UpToDate when no update available`() = runTest {
        val upToDateInfo = availableInfo.copy(
            latestVersion = "0.1.0",
            isUpdateAvailable = false,
        )
        val repo = FakeUpdateRepository(Result.Success(upToDateInfo))
        viewModel = AppUpdateViewModel(repo)

        viewModel.state.test {
            assertEquals(UpdateUiState.Idle, awaitItem())
            viewModel.checkForUpdate()
            assertEquals(UpdateUiState.Checking, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertInstanceOf(UpdateUiState.UpToDate::class.java, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checkForUpdate transitions to Error on failure`() = runTest {
        val repo = FakeUpdateRepository(Result.Failure(DomainError.Network(RuntimeException("timeout"))))
        viewModel = AppUpdateViewModel(repo)

        viewModel.state.test {
            assertEquals(UpdateUiState.Idle, awaitItem())
            viewModel.checkForUpdate()
            assertEquals(UpdateUiState.Checking, awaitItem())
            testDispatcher.scheduler.advanceUntilIdle()
            assertInstanceOf(UpdateUiState.Error::class.java, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reset sets state back to Idle`() = runTest {
        val repo = FakeUpdateRepository(Result.Failure(DomainError.Network(RuntimeException())))
        viewModel = AppUpdateViewModel(repo)

        viewModel.checkForUpdate()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.state.test {
            // Current state is Error
            assertInstanceOf(UpdateUiState.Error::class.java, awaitItem())
            viewModel.reset()
            assertEquals(UpdateUiState.Idle, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `downloadAndInstall transitions Available to Downloading to ReadyToInstall`() = runTest {
        val apkFile = File("/tmp/test.apk")
        val repo = FakeUpdateRepository(
            checkResult = Result.Success(availableInfo),
            downloadResult = Result.Success(apkFile),
        )
        viewModel = AppUpdateViewModel(repo)

        // First get to Available state
        viewModel.checkForUpdate()
        testDispatcher.scheduler.advanceUntilIdle()

        // Trigger download and advance to completion
        viewModel.downloadAndInstall()
        testDispatcher.scheduler.advanceUntilIdle()

        // After completion the final state must be ReadyToInstall
        assertInstanceOf(UpdateUiState.ReadyToInstall::class.java, viewModel.state.value)
    }

    // ---- Fake repository ----

    private class FakeUpdateRepository(
        private val checkResult: Result<UpdateInfo, DomainError> = Result.Success(
            UpdateInfo(
                currentVersion = "0.1.0",
                latestVersion = "0.1.0",
                isUpdateAvailable = false,
                releaseNotes = null,
                apkUrl = null,
                apkSizeBytes = 0L,
                releasePageUrl = null,
            ),
        ),
        private val downloadResult: Result<File, DomainError> = Result.Failure(
            DomainError.Unexpected(UnsupportedOperationException("not configured")),
        ),
    ) : AppUpdateRepository {
        override suspend fun checkLatest() = checkResult
        override suspend fun downloadApk(
            url: String,
            onProgress: (Long, Long) -> Unit,
        ): Result<File, DomainError> {
            onProgress(0L, 1000L)
            return downloadResult
        }
    }
}
