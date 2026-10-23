package com.mynotes.app

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** نوافذ القفل: تعيين الرمز، وفتح مستند مقفل بالرمز أو البصمة. */
@Composable
fun LockDialogs() {
    val c = AppController
    c.setPinFor?.let { id -> SetPinDialog(id) }
    c.unlockRequest?.let { id -> UnlockDialog(id) }
}

@Composable
private fun SetPinDialog(id: String) {
    val c = AppController
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    val ok = p1.length in 4..8 && p1 == p2
    AlertDialog(
        onDismissRequest = { c.setPinFor = null },
        title = { Text(L("رمز القفل", "Lock PIN")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    L("اختر رمزًا من 4 إلى 8 أرقام. يُستعمل لكل المستندات المقفلة، ويمكن استعمال البصمة أيضًا.",
                        "Choose a 4–8 digit PIN. It’s used for all locked documents; fingerprint works too."),
                    fontSize = 13.sp
                )
                PinField(p1, L("الرمز", "PIN")) { p1 = it }
                PinField(p2, L("أعد كتابة الرمز", "Repeat PIN")) { p2 = it }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                c.setPin(p1)
                c.setPinFor = null
                if (id.isNotEmpty()) c.toggleLock(id) else c.messages.tryEmit(L("تم تغيير الرمز", "PIN changed"))
            }) { Text(L("حفظ", "Save")) }
        },
        dismissButton = { TextButton(onClick = { c.setPinFor = null }) { Text(L("إلغاء", "Cancel")) } }
    )
}

@Composable
private fun UnlockDialog(id: String) {
    val c = AppController
    val ctx = LocalContext.current
    val name = c.library.firstOrNull { it.id == id }?.name ?: ""
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val activity = ctx as? FragmentActivity
    val canBio = remember {
        activity != null && BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    fun bio() {
        val act = activity ?: return
        val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(act), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                c.unlockGranted()
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(L("فتح «$name»", "Open “$name”"))
                .setNegativeButtonText(L("استعمال الرمز", "Use PIN"))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
        )
    }
    LaunchedEffect(id) { if (canBio) bio() }
    AlertDialog(
        onDismissRequest = { c.unlockRequest = null },
        title = { Text(L("مستند مقفل", "Locked document")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(name, fontSize = 13.sp)
                PinField(pin, L("الرمز", "PIN")) {
                    pin = it
                    wrong = false
                }
                if (wrong) Text(L("الرمز غير صحيح", "Wrong PIN"), color = Palette.Danger, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = pin.length >= 4, onClick = {
                if (c.checkPin(pin)) c.unlockGranted() else wrong = true
            }) { Text(L("فتح", "Open")) }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                if (canBio) TextButton(onClick = { bio() }) { Text(L("البصمة", "Fingerprint")) }
                TextButton(onClick = { c.unlockRequest = null }) { Text(L("إلغاء", "Cancel")) }
            }
        }
    )
}

@Composable
private fun PinField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() }.take(8)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}
