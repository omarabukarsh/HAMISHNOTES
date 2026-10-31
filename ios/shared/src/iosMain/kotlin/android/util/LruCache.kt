package android.util

/** بديل android.util.LruCache (بنفس السلوك: الأقدم استخدامًا يُطرد أولًا عند تجاوز الحد). */
open class LruCache<K : Any, V : Any>(private var maxSize: Int) {
    private val map = LinkedHashMap<K, V>()
    private var size = 0
    private val lock = platform.Foundation.NSRecursiveLock()

    protected open fun sizeOf(key: K, value: V): Int = 1
    protected open fun entryRemoved(evicted: Boolean, key: K, oldValue: V, newValue: V?) {}

    private inline fun <R> locked(block: () -> R): R {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    fun get(key: K): V? = locked {
        val v = map.remove(key) ?: return@locked null
        map[key] = v
        v
    }

    fun put(key: K, value: V): V? {
        val prev = locked {
            val p = map.remove(key)
            if (p != null) size -= sizeOf(key, p)
            map[key] = value
            size += sizeOf(key, value)
            p
        }
        if (prev != null) entryRemoved(false, key, prev, value)
        trimToSize(maxSize)
        return prev
    }

    fun remove(key: K): V? {
        val p = locked {
            val p = map.remove(key)
            if (p != null) size -= sizeOf(key, p)
            p
        }
        if (p != null) entryRemoved(false, key, p, null)
        return p
    }

    fun trimToSize(max: Int) {
        while (true) {
            val e = locked {
                if (size <= max || map.isEmpty()) return@locked null
                val k = map.keys.first()
                val v = map.remove(k)!!
                size -= sizeOf(k, v)
                k to v
            } ?: break
            entryRemoved(true, e.first, e.second, null)
        }
    }

    fun evictAll() = trimToSize(-1)
    fun size(): Int = locked { size }
    fun maxSize(): Int = maxSize
    fun snapshot(): Map<K, V> = locked { LinkedHashMap(map) }
}

object Log {
    fun d(tag: String, msg: String) = println("$tag: $msg")
    fun w(tag: String, msg: String) = println("$tag: $msg")
    fun e(tag: String, msg: String, t: Throwable? = null) = println("$tag: $msg ${t ?: ""}")
    fun getStackTraceString(t: Throwable): String = t.stackTraceToString()
}
