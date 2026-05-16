package com.firesin.xuipanel.core.data.repository

import com.firesin.xuipanel.core.data.db.dao.PanelDao
import com.firesin.xuipanel.core.data.db.entity.PanelEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PanelLookupImplTest {

    private lateinit var panelDao: PanelDao
    private lateinit var panelLookupImpl: PanelLookupImpl

    @BeforeEach
    fun setUp() {
        panelDao = mockk()
        panelLookupImpl = PanelLookupImpl(panelDao)
    }

    @Test
    fun `lookup returns PanelMetadata with correct name and twoFactorEnabled=true`() = runTest {
        coEvery { panelDao.getById("abc") } returns fakeEntity(id = "abc", name = "My Panel", twoFactorEnabled = true)

        val meta = panelLookupImpl.lookup("abc")

        assertEquals("My Panel", meta?.name)
        assertTrue(meta?.twoFactorEnabled == true)
    }

    @Test
    fun `lookup returns PanelMetadata with twoFactorEnabled=false`() = runTest {
        coEvery { panelDao.getById("xyz") } returns fakeEntity(id = "xyz", name = "Other Panel", twoFactorEnabled = false)

        val meta = panelLookupImpl.lookup("xyz")

        assertEquals("Other Panel", meta?.name)
        assertTrue(meta?.twoFactorEnabled == false)
    }

    @Test
    fun `lookup returns null when panel does not exist`() = runTest {
        coEvery { panelDao.getById("missing") } returns null

        val meta = panelLookupImpl.lookup("missing")

        assertNull(meta)
    }

    // ---- helpers ----

    private fun fakeEntity(id: String, name: String, twoFactorEnabled: Boolean) = PanelEntity(
        id = id,
        name = name,
        baseUrl = "https://example.com",
        login = "admin",
        password = "secret",
        trustSelfSigned = 0,
        isActive = 1,
        createdAt = System.currentTimeMillis(),
        lastLoginAt = null,
        tlsMode = "SYSTEM",
        pinnedSpkiSha256 = null,
        pinnedAt = null,
        apiToken = null,
        twoFactorEnabled = twoFactorEnabled,
    )
}
