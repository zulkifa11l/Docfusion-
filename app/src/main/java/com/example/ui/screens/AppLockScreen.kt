package com.example.ui.screens

import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.DocFusionViewModel

@Composable
fun AppLockScreen(
    viewModel: DocFusionViewModel,
    onUnlocked: () -> Unit
) {
    val isPinSaved = viewModel.settingsManager.appLockPin != null
    var enteredPin by remember { mutableStateOf("") }
    var setupPhase by remember { mutableStateOf(if (isPinSaved) "Unlock" else "Setup") }
    var tempPin by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf(if (isPinSaved) "Enter PIN to access Docfusion" else "Create a safe 4-digit PIN lock") }
    var isError by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    
    val biometricPrompt = remember(activity) {
        if (activity != null) {
            BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // If user cancels or clicks back, do not force an error state
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        statusMsg = "Biometric error: $errString"
                        isError = true
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onUnlocked()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    statusMsg = "Recognition failed. Please try again."
                    isError = true
                }
            })
        } else null
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Docfusion Secure Unlock")
            .setSubtitle("Use your fingerprint or facial recognition to access the app")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
    }

    fun triggerBiometricPrompt() {
        if (biometricPrompt != null) {
            try {
                biometricPrompt.authenticate(promptInfo)
            } catch (e: Exception) {
                statusMsg = "Biometrics unavailable: ${e.localizedMessage}"
                isError = true
            }
        } else {
            statusMsg = "Biometric setup failed to initialize"
            isError = true
        }
    }

    LaunchedEffect(isPinSaved) {
        if (isPinSaved) {
            triggerBiometricPrompt()
        }
    }

    fun handlePinDigit(digit: String) {
        if (enteredPin.length < 4) {
            enteredPin += digit
            isError = false
        }
        
        if (enteredPin.length == 4) {
            when (setupPhase) {
                "Unlock" -> {
                    if (viewModel.verifyLockPin(enteredPin)) {
                        onUnlocked()
                    } else {
                        enteredPin = ""
                        statusMsg = "Incorrect PIN code. Try again!"
                        isError = true
                    }
                }
                "Setup" -> {
                    tempPin = enteredPin
                    enteredPin = ""
                    setupPhase = "Confirm"
                    statusMsg = "Confirm your 4-digit PIN lock"
                }
                "Confirm" -> {
                    if (enteredPin == tempPin) {
                        viewModel.settingsManager.appLockPin = enteredPin
                        viewModel.settingsManager.isAppLockEnabled = true
                        onUnlocked()
                    } else {
                        enteredPin = ""
                        tempPin = ""
                        setupPhase = "Setup"
                        statusMsg = "PINs did not match. Draw a new one!"
                        isError = true
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Docfusion Secure",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = statusMsg,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Pin Dots Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 1..4) {
                    val isActive = enteredPin.length >= i
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Numeric Keypad
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val digits = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("Biometric", "0", "Back")
                )

                for (row in digits) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        for (key in row) {
                            when (key) {
                                "Biometric" -> {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                if (isPinSaved) {
                                                    triggerBiometricPrompt()
                                                } else {
                                                    statusMsg = "Please establish a PIN first before using Biometrics"
                                                    isError = true
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = "Unlock with fingerprint",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                                "Back" -> {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .clickable { handleBackspace() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Backspace,
                                            contentDescription = "Backspace",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                else -> {
                                    Button(
                                        onClick = { handlePinDigit(key) },
                                        modifier = Modifier.size(72.dp),
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                                    ) {
                                        Text(
                                            text = key,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
