package com.firesin.xuipanel.core.common.twofactor

import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TwoFactorOtpDispatcherTest {

    private lateinit var dispatcher: TwoFactorOtpDispatcher

    @BeforeEach
    fun setUp() {
        dispatcher = TwoFactorOtpDispatcher()
    }

    @Test
    fun `requestOtp suspends until completeRequest and returns the OTP`() = runTest {
        val requestsCollected = mutableListOf<TwoFactorOtpRequest>()
        val collector = launch {
            dispatcher.requests.collect { requestsCollected += it }
        }

        val result = async { dispatcher.requestOtp("panel1", "My Panel") }

        // Let the coroutine emit and suspend
        testScheduler.runCurrent()

        val req = requestsCollected.first()
        assertEquals("panel1", req.panelId)
        assertEquals("My Panel", req.panelName)

        dispatcher.completeRequest(req.requestId, "123456")

        assertEquals("123456", result.await())
        collector.cancel()
    }

    @Test
    fun `requestOtp returns null when completeRequest called with null (cancel)`() = runTest {
        val requestsCollected = mutableListOf<TwoFactorOtpRequest>()
        val collector = launch {
            dispatcher.requests.collect { requestsCollected += it }
        }

        val result = async { dispatcher.requestOtp("panel2", "Panel B") }

        testScheduler.runCurrent()

        val req = requestsCollected.first()
        dispatcher.completeRequest(req.requestId, null)

        assertNull(result.await())
        collector.cancel()
    }

    @Test
    fun `requestOtp returns null on timeout after 90 seconds`() = runTest {
        // Don't collect requests — nobody completes them
        val result = async { dispatcher.requestOtp("panel3", "Panel C") }

        advanceTimeBy(90_001L)

        assertNull(result.await())
    }

    @Test
    fun `concurrent requestOtp calls are independent and return correct OTPs`() = runTest {
        val requestsCollected = mutableListOf<TwoFactorOtpRequest>()
        val collector = launch {
            dispatcher.requests.collect { requestsCollected += it }
        }

        val result1 = async { dispatcher.requestOtp("panel-a", "Panel A") }
        val result2 = async { dispatcher.requestOtp("panel-b", "Panel B") }

        testScheduler.runCurrent()

        val req1 = requestsCollected.first { it.panelId == "panel-a" }
        val req2 = requestsCollected.first { it.panelId == "panel-b" }

        dispatcher.completeRequest(req2.requestId, "654321")
        dispatcher.completeRequest(req1.requestId, "111111")

        assertEquals("111111", result1.await())
        assertEquals("654321", result2.await())

        collector.cancel()
    }
}
