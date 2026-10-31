package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.hamishnotes.app.R
import com.hamishnotes.app.bundleBytes
import com.hamishnotes.app.buildVector

/** نفس painterResource في أندرويد: الأيقونات مولَّدة من res/drawable (Drawables.kt)، والصور من حزمة التطبيق. */
@Composable
fun painterResource(id: Int): Painter {
    val v = remember(id) { buildVector(id) }
    if (v != null) return rememberVectorPainter(v)
    val img = remember(id) { imageOf(id) }
    return if (img != null) remember(img) { BitmapPainter(img) } else remember { ColorPainter(androidx.compose.ui.graphics.Color.Transparent) }
}

private fun imageOf(id: Int): ImageBitmap? {
    val name = when (id) {
        R.drawable.logo_app -> "logo_app"
        else -> return null
    }
    val bytes = bundleBytes("drawable-nodpi/$name.png") ?: return null
    return try {
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (t: Throwable) {
        null
    }
}
