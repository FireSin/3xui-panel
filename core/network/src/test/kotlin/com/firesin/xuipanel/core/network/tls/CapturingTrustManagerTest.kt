package com.firesin.xuipanel.core.network.tls

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class CapturingTrustManagerTest {

    private val systemTm: X509TrustManager = mockk(relaxed = true)

    private fun fakeChain(publicKey: PublicKey): Array<X509Certificate> {
        val cert = mockk<X509Certificate>()
        every { cert.publicKey } returns publicKey
        return arrayOf(cert)
    }

    private fun generatePublicKey(): PublicKey {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(1024)
        return kpg.generateKeyPair().public
    }

    @Test
    fun `captures SPKI when system trust succeeds`() {
        val publicKey = generatePublicKey()
        val tm = CapturingTrustManager(systemTm)
        val chain = fakeChain(publicKey)

        tm.checkServerTrusted(chain, "RSA")

        val captured = tm.capturedSpki.get()
        assertNotNull(captured)
        assertEquals(SpkiHasher.sha256Base64(publicKey.encoded), captured)
    }

    @Test
    fun `captures SPKI even when system trust fails`() {
        val publicKey = generatePublicKey()
        every {
            systemTm.checkServerTrusted(any(), any())
        } throws CertificateException("untrusted")

        val tm = CapturingTrustManager(systemTm)
        val chain = fakeChain(publicKey)

        // Should not throw despite system TM failure
        tm.checkServerTrusted(chain, "RSA")

        val captured = tm.capturedSpki.get()
        assertNotNull(captured)
        assertEquals(SpkiHasher.sha256Base64(publicKey.encoded), captured)
    }

    @Test
    fun `initial capturedSpki is null`() {
        val tm = CapturingTrustManager(systemTm)
        assertNull(tm.capturedSpki.get())
    }

    @Test
    fun `empty chain throws CertificateException`() {
        val tm = CapturingTrustManager(systemTm)
        org.junit.jupiter.api.Assertions.assertThrows(CertificateException::class.java) {
            tm.checkServerTrusted(emptyArray(), "RSA")
        }
    }

    @Test
    fun `checkClientTrusted delegates to system`() {
        val publicKey = generatePublicKey()
        val tm = CapturingTrustManager(systemTm)
        val chain = fakeChain(publicKey)

        tm.checkClientTrusted(chain, "RSA")

        verify(exactly = 1) { systemTm.checkClientTrusted(chain, "RSA") }
    }

    @Test
    fun `second handshake with same SPKI is accepted`() {
        val publicKey = generatePublicKey()
        val tm = CapturingTrustManager(systemTm)
        val chain = fakeChain(publicKey)

        tm.checkServerTrusted(chain, "RSA")
        // Should not throw — same SPKI
        tm.checkServerTrusted(chain, "RSA")
    }

    @Test
    fun `second handshake with different SPKI throws SpkiPinMismatchException`() {
        val first = generatePublicKey()
        val second = generatePublicKey()
        val tm = CapturingTrustManager(systemTm)

        tm.checkServerTrusted(fakeChain(first), "RSA")

        org.junit.jupiter.api.Assertions.assertThrows(SpkiPinMismatchException::class.java) {
            tm.checkServerTrusted(fakeChain(second), "RSA")
        }
    }

    @Test
    fun `SpkiPinMismatchException contains observedSpki from the second certificate`() {
        val first = generatePublicKey()
        val second = generatePublicKey()
        val secondSpki = SpkiHasher.sha256Base64(second.encoded)
        val tm = CapturingTrustManager(systemTm)

        tm.checkServerTrusted(fakeChain(first), "RSA")

        val exception = org.junit.jupiter.api.Assertions.assertThrows(SpkiPinMismatchException::class.java) {
            tm.checkServerTrusted(fakeChain(second), "RSA")
        }
        assertEquals(secondSpki, exception.observedSpki)
    }
}
