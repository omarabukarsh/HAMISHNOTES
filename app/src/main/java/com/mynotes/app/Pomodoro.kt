package com.mynotes.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * أزرار مؤقت الدراسة (25 دقيقة دراسة ثم 5 راحة)، نفسها داخل وضع التركيز وخارجه:
 * تشغيل، ثم إيقاف مؤقت / متابعة، وإنهاء.
 */
@Composable
fun PomodoroControl(showStartLabel: Boolean = true) {
    val c = AppController
    val shape = RoundedCornerShape(10.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (c.pomoPhase == 0) {
            Row(
                Modifier.clip(shape).clickable { c.startPomodoro() }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhIcon(R.drawable.ph_timer, Palette.Navy, 20.dp, "تشغيل مؤقت الدراسة")
                if (showStartLabel) {
                    Spacer(Modifier.width(6.dp))
                    Text("مؤقت 25 د", color = Palette.Navy, fontSize = 13.sp)
                }
            }
        } else {
            val left = c.pomoLeftMs / 1000
            val study = c.pomoPhase == 1
            Text(
                (if (study) "دراسة " else "راحة ") + "%02d:%02d".format(left / 60, left % 60),
                color = when {
                    c.pomoPaused -> Palette.TextSecondary
                    study -> Palette.Navy
                    else -> Palette.GoldDeep
                },
                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Row(
                Modifier.clip(shape)
                    .clickable { if (c.pomoPaused) c.resumePomodoro() else c.pausePomodoro() }
                    .padding(8.dp)
            ) {
                if (c.pomoPaused) PhIcon(R.drawable.ph_play, Palette.Navy, 18.dp, "متابعة المؤقت")
                else PhIcon(R.drawable.ph_pause, Palette.Navy, 18.dp, "إيقاف مؤقت")
            }
            Row(Modifier.clip(shape).clickable { c.stopPomodoro() }.padding(8.dp)) {
                PhIcon(R.drawable.ph_stop, Palette.TextSecondary, 18.dp, "إنهاء المؤقت")
            }
        }
    }
}
