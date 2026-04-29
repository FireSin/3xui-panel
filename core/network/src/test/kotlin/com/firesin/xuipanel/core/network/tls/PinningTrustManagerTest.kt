package com.firesin.xuipanel.core.network.tls

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class PinningTrustManagerTest {

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
    fun `matching SPKI passes without exception`() {
        val publicKey = generatePublicKey()
        val expectedSpki = SpkiHasher.sha256Base64(publicKey.encoded)

        val tm = PinningTrustManager(systemTm, expectedSpki)
        val chain = fakeChain(publicKey)

        // Should not throw
        tm.checkServerTrusted(chain, "RSA")
    }

    @Test
    fun `mismatched SPKI throws SpkiPinMismatchException`() {
        val publicKey = generatePublicKey()
        val differentKey = generatePublicKey()
        val pinnedSpki = SpkiHasher.sha256Base64(differentKey.encoded)

        val tm = PinningTrustManager(systemTm, pinnedSpki)
        val chain = fakeChain(publicKey)

        assertThrows(SpkiPinMismatchException::class.java) {
            tm.checkServerTrusted(chain, "RSA")
        }
    }

    @Test
    fun `SpkiPinMismatchException contains observedSpki from the certificate`() {
        val publicKey = generatePublicKey()
        val differentKey = generatePublicKey()
        val pinnedSpki = SpkiHasher.sha256Base64(differentKey.encoded)
        val observedSpki = SpkiHasher.sha256Base64(publicKey.encoded)

        val tm = PinningTrustManager(systemTm, pinnedSpki)
        val chain = fakeChain(publicKey)

        val exception = assertThrows(SpkiPinMismatchException::class.java) {
            tm.checkServerTrusted(chain, "RSA")
        }
        org.junit.jupiter.api.Assertions.assertEquals(observedSpki, exception.observedSpki)
    }

    @Test
    fun `empty chain throws CertificateException`() {
        val tm = PinningTrustManager(systemTm, "anypin")

        assertThrows(CertificateException::class.java) {
            tm.checkServerTrusted(emptyArray(), "RSA")
        }
    }

    @Test
    fun `checkClientTrusted delegates to system`() {
        val publicKey = generatePublicKey()
        val tm = PinningTrustManager(systemTm, "anypin")
        val chain = fakeChain(publicKey)

        tm.checkClientTrusted(chain, "RSA")

        verify(exactly = 1) { systemTm.checkClientTrusted(chain, "RSA") }
    }
}
