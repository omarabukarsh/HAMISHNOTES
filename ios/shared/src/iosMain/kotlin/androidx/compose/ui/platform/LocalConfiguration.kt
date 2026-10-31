package androidx.compose.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density

/** بديل LocalConfiguration في أندرويد: أبعاد النافذة بوحدات dp. */
class Configuration(val screenWidthDp: Int, val screenHeightDp: Int) {
    val orientation: Int get() = if (screenWidthDp > screenHeightDp) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT
    val smallestScreenWidthDp: Int get() = minOf(screenWidthDp, screenHeightDp)

    companion object {
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
    }
}

object LocalConfiguration {
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    val current: Configuration
        @Composable get() {
            val size = LocalWindowInfo.current.containerSize
            val d: Density = LocalDensity.current
            return Configuration((size.width / d.density).toInt(), (size.height / d.density).toInt())
        }
}
