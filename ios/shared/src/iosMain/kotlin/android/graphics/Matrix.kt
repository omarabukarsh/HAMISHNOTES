package android.graphics

/** مصفوفة تحويل (تكبير + إزاحة + تدوير بزوايا قائمة)، بالقدر الذي يستخدمه رسم الصفحات. */
class Matrix {
    var scaleX = 1f
        private set
    var scaleY = 1f
        private set
    var transX = 0f
        private set
    var transY = 0f
        private set
    var rotation = 0f
        private set

    fun setScale(sx: Float, sy: Float) {
        scaleX = sx; scaleY = sy; transX = 0f; transY = 0f
    }

    fun postScale(sx: Float, sy: Float): Boolean {
        scaleX *= sx; scaleY *= sy; transX *= sx; transY *= sy
        return true
    }

    fun postTranslate(dx: Float, dy: Float): Boolean {
        transX += dx; transY += dy
        return true
    }

    fun postRotate(deg: Float): Boolean {
        rotation = (rotation + deg) % 360f
        return true
    }
}
