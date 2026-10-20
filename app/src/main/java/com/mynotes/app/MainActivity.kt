package com.mynotes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppController.initialize(applicationContext)
        setContent {
            MyNotesTheme { MyNotesApp() }
        }
    }

    override fun onStop() {
        super.onStop()
        AppController.flush()
    }
}

@Composable
fun MyNotesApp() {
    val c = AppController
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        c.messages.collect { snackbar.showSnackbar(it) }
    }

    // زر الرجوع من المستند يعيد إلى المكتبة بدل إغلاق التطبيق
    BackHandler(enabled = c.activeId != null) { c.activate(null) }

    Box(Modifier.fillMaxSize().background(Palette.Background).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            if (c.tabs.isNotEmpty()) TabBar()
            val active = c.tabs.firstOrNull { it.id == c.activeId }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (active == null) {
                    LibraryScreen()
                } else {
                    key(active.id) { ReaderScreen(active) }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun TabBar() {
    val c = AppController
    Row(
        Modifier.fillMaxWidth().height(44.dp).background(Palette.TabBar)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.Bottom
    ) {
        TabItem(
            title = "المكتبة",
            active = c.activeId == null,
            home = true,
            onClick = { c.activate(null) },
            onClose = null
        )
        c.tabs.forEach { s ->
            key(s.id) {
                TabItem(
                    title = s.title,
                    active = c.activeId == s.id,
                    home = false,
                    onClick = { c.activate(s.id) },
                    onClose = { c.closeTab(s.id) }
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    title: String,
    active: Boolean,
    home: Boolean,
    onClick: () -> Unit,
    onClose: (() -> Unit)?
) {
    val activeColor = if (home) Palette.Background else Color.White
    Column(
        Modifier.padding(start = 4.dp)
            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
            .background(if (active) activeColor else Color.Transparent)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier.fillMaxWidth().height(3.dp)
                .background(if (active) Palette.Navy else Color.Transparent)
        )
        Row(
            Modifier.height(37.dp)
                .padding(start = 12.dp, end = if (onClose == null) 12.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (home) {
                Icon(
                    Icons.Default.Home, null, Modifier.size(18.dp),
                    tint = if (active) Palette.Navy else Palette.TextSecondary
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) Palette.TextPrimary else Palette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 170.dp)
            )
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close, "إغلاق التبويب", Modifier.size(16.dp),
                        tint = Palette.TextSecondary
                    )
                }
            }
        }
    }
}
