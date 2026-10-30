package com.mynotes.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** صفحة تسجيل الدخول الأولى: Google أو «ليس الآن». */
@Composable
fun SignInScreen() {
    val c = AppController
    val activity = LocalContext.current.findActivity()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Lib.bg).systemBarsPadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // الشعار بألوانه الأصلية داخل مربع فاتح بظل خفيف
            val tile = RoundedCornerShape(28.dp)
            Box(
                Modifier.size(112.dp).shadow(14.dp, tile, ambientColor = Lib.text, spotColor = Lib.text)
                    .clip(tile).background(Color(0xFFFFFDF7)).border(1.dp, Lib.line, tile),
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(R.drawable.logo_app), null, Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)))
            }
            Spacer(Modifier.height(22.dp))
            UnderlinedTitle(L("هامش", "Hamish"), size = 44)
            Spacer(Modifier.height(16.dp))
            Text(
                L("ملاحظاتك وملفات PDF وأدوات المذاكرة في مكان واحد.\nسجّل الدخول لربط حسابك، أو تخطَّ وابدأ مباشرة.", "Your notes, PDFs and study tools in one place.\nSign in to link your account, or skip and start right away."),
                fontSize = 15.sp, lineHeight = 22.sp, color = Lib.muted, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(36.dp))

            val shape = RoundedCornerShape(50)
            Row(
                Modifier.fillMaxWidth().shadow(2.dp, shape).clip(shape).background(Color.White).border(1.dp, Color(0xFFDADCE0), shape)
                    .clickable(enabled = !busy) {
                        if (activity == null) return@clickable
                        busy = true
                        error = null
                        scope.launch {
                            GoogleAuth.signIn(activity)
                                .onSuccess { (email, name) -> c.setAccount(email, name) }
                                .onFailure { error = GoogleAuth.messageFor(it) }
                            busy = false
                        }
                    }.padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painterResource(R.drawable.ic_google), "Google", Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (busy) L("جارٍ الدخول…", "Signing in…") else L("المتابعة باستخدام Google", "Continue with Google"),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E2A44)
                )
            }
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error ?: "", fontSize = 13.sp, color = Lib.exam, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.clip(RoundedCornerShape(50)).clickable(enabled = !busy) { c.skipLogin() }
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(L("ليس الآن", "Not now"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Lib.muted)
            }
        }
        Text(
            L("ملاحظاتك تبقى على جهازك. نستخدم اسمك وبريدك فقط.", "Your notes stay on your device. We only use your name and email."),
            fontSize = 12.sp, color = Lib.muted, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 28.dp, vertical = 20.dp)
        )
    }
}
