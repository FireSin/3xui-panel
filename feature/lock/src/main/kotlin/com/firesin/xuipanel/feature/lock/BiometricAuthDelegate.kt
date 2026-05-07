package com.firesin.xuipanel.feature.lock

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Wraps [BiometricPrompt] so that the Composable layer doesn't need to
 * reference Android Biometric API directly. Call [authenticate] from a
 * side-effect (LaunchedEffect / button click) while the host Activity is alive.
 */
class BiometricAuthDelegate(
    private val activity: FragmentActivity,
    private val onSuccess: () -> Unit,
    private val onError: (errorCode: Int, message: String) -> Unit,
    private val onFailed: () -> Unit,
) {

    private val executor = ContextCompat.getMainExecutor(activity)

    private val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onError(errorCode, errString.toString())
        }

        override fun onAuthenticationFailed() {
            onFailed()
        }
    }

    private val prompt = BiometricPrompt(activity, executor, callback)

    fun authenticate(promptInfo: BiometricPrompt.PromptInfo) {
        prompt.authenticate(promptInfo)
    }
}
