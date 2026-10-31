package androidx.compose.ui.platform

import android.content.AppContext
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

/** في iOS سياق واحد للتطبيق كله. */
val LocalContext = staticCompositionLocalOf<Context> { AppContext }
