package org.json

/*
 * بديل مكتبة org.json الموجودة في أندرويد (نفس الأسماء والسلوك المستخدم في التطبيق)،
 * حتى يبقى كود الحفظ والقراءة مطابقًا لنسخة أندرويد.
 */

class JSONException(message: String) : RuntimeException(message)

private fun wrap(v: Any?): Any? = when (v) {
    null -> JSONObject.NULL
    is JSONObject, is JSONArray, is String, is Boolean -> v
    is Int, is Long -> v
    is Short -> v.toInt()
    is Byte -> v.toInt()
    is Float -> v.toDouble()
    is Double -> v
    is Number -> v.toDouble()
    is Collection<*> -> JSONArray(v)
    is Map<*, *> -> JSONObject(v)
    is Array<*> -> JSONArray(v.toList())
    is IntArray -> JSONArray(v.toList())
    is FloatArray -> JSONArray(v.map { it.toDouble() })
    is LongArray -> JSONArray(v.toList())
    is DoubleArray -> JSONArray(v.toList())
    else -> v.toString()
}

private fun toInt(v: Any?): Int? = when (v) {
    is Number -> v.toInt()
    is String -> v.toDoubleOrNull()?.toInt()
    else -> null
}

private fun toLong(v: Any?): Long? = when (v) {
    is Long -> v
    is Number -> v.toLong()
    is String -> v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong()
    else -> null
}

private fun toDouble(v: Any?): Double? = when (v) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

private fun toBool(v: Any?): Boolean? = when (v) {
    is Boolean -> v
    is String -> if (v.equals("true", true)) true else if (v.equals("false", true)) false else null
    else -> null
}

private fun toStr(v: Any?): String? = when (v) {
    null -> null
    JSONObject.NULL -> "null"
    is String -> v
    is Double -> numberText(v)
    else -> v.toString()
}

private fun numberText(d: Double): String {
    if (d.isNaN() || d.isInfinite()) throw JSONException("Forbidden numeric value: $d")
    if (d == d.toLong().toDouble() && kotlin.math.abs(d) < 1e15) return d.toLong().toString()
    return d.toString()
}

internal fun quote(s: String, sb: StringBuilder) {
    sb.append('"')
    for (c in s) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\b' -> sb.append("\\b")
            '\u000C' -> sb.append("\\f")
            '/' -> sb.append("\\/")
            else -> if (c < ' ') {
                sb.append("\\u")
                sb.append(c.code.toString(16).padStart(4, '0'))
            } else sb.append(c)
        }
    }
    sb.append('"')
}

internal fun writeValue(v: Any?, sb: StringBuilder) {
    when (v) {
        null, JSONObject.NULL -> sb.append("null")
        is JSONObject -> v.writeTo(sb)
        is JSONArray -> v.writeTo(sb)
        is String -> quote(v, sb)
        is Double -> sb.append(numberText(v))
        is Float -> sb.append(numberText(v.toDouble()))
        else -> sb.append(v.toString())
    }
}

class JSONObject() {
    private val map = LinkedHashMap<String, Any?>()

    constructor(text: String) : this() {
        val v = JSONTokener(text).nextValue()
        if (v !is JSONObject) throw JSONException("Not a JSON object")
        map.putAll(v.map)
    }

    constructor(m: Map<*, *>) : this() {
        m.forEach { (k, v) -> map[k.toString()] = wrap(v) }
    }

    fun put(name: String, value: Any?): JSONObject {
        if (value == null) map.remove(name) else map[name] = wrap(value)
        return this
    }

    fun put(name: String, value: Boolean): JSONObject { map[name] = value; return this }
    fun put(name: String, value: Int): JSONObject { map[name] = value; return this }
    fun put(name: String, value: Long): JSONObject { map[name] = value; return this }
    fun put(name: String, value: Double): JSONObject { numberText(value); map[name] = value; return this }

    fun opt(name: String): Any? = map[name]
    fun has(name: String): Boolean = map.containsKey(name)
    fun isNull(name: String): Boolean = map[name].let { it == null || it == NULL }
    fun remove(name: String): Any? = map.remove(name)
    fun length(): Int = map.size
    fun keys(): Iterator<String> = map.keys.toList().iterator()
    fun names(): JSONArray? = if (map.isEmpty()) null else JSONArray(map.keys.toList())

    fun get(name: String): Any = map[name] ?: throw JSONException("No value for $name")

    fun getString(name: String): String = toStr(get(name)) ?: throw JSONException("Not a string: $name")
    fun getInt(name: String): Int = toInt(get(name)) ?: throw JSONException("Not an int: $name")
    fun getLong(name: String): Long = toLong(get(name)) ?: throw JSONException("Not a long: $name")
    fun getDouble(name: String): Double = toDouble(get(name)) ?: throw JSONException("Not a double: $name")
    fun getBoolean(name: String): Boolean = toBool(get(name)) ?: throw JSONException("Not a boolean: $name")
    fun getJSONObject(name: String): JSONObject = get(name) as? JSONObject ?: throw JSONException("Not an object: $name")
    fun getJSONArray(name: String): JSONArray = get(name) as? JSONArray ?: throw JSONException("Not an array: $name")

    fun optString(name: String): String = optString(name, "")
    fun optString(name: String, fallback: String?): String =
        map[name].let { if (it == null || it == NULL) fallback else toStr(it) } ?: (fallback ?: "")
    fun optInt(name: String): Int = optInt(name, 0)
    fun optInt(name: String, fallback: Int): Int = toInt(map[name]) ?: fallback
    fun optLong(name: String): Long = optLong(name, 0L)
    fun optLong(name: String, fallback: Long): Long = toLong(map[name]) ?: fallback
    fun optDouble(name: String): Double = optDouble(name, Double.NaN)
    fun optDouble(name: String, fallback: Double): Double = toDouble(map[name]) ?: fallback
    fun optBoolean(name: String): Boolean = optBoolean(name, false)
    fun optBoolean(name: String, fallback: Boolean): Boolean = toBool(map[name]) ?: fallback
    fun optJSONObject(name: String): JSONObject? = map[name] as? JSONObject
    fun optJSONArray(name: String): JSONArray? = map[name] as? JSONArray

    internal fun writeTo(sb: StringBuilder) {
        sb.append('{')
        var first = true
        for ((k, v) in map) {
            if (!first) sb.append(',')
            first = false
            quote(k, sb)
            sb.append(':')
            writeValue(v, sb)
        }
        sb.append('}')
    }

    override fun toString(): String = StringBuilder().also { writeTo(it) }.toString()

    companion object {
        val NULL: Any = object {
            override fun toString() = "null"
            override fun equals(other: Any?) = other == null || other === this
            override fun hashCode() = 0
        }
    }
}

class JSONArray() {
    private val list = ArrayList<Any?>()

    constructor(text: String) : this() {
        val v = JSONTokener(text).nextValue()
        if (v !is JSONArray) throw JSONException("Not a JSON array")
        list.addAll(v.list)
    }

    constructor(c: Collection<*>) : this() {
        c.forEach { list.add(wrap(it)) }
    }

    fun length(): Int = list.size

    fun put(value: Any?): JSONArray { list.add(wrap(value)); return this }
    fun put(value: Boolean): JSONArray { list.add(value); return this }
    fun put(value: Int): JSONArray { list.add(value); return this }
    fun put(value: Long): JSONArray { list.add(value); return this }
    fun put(value: Double): JSONArray { numberText(value); list.add(value); return this }
    fun put(index: Int, value: Any?): JSONArray {
        while (list.size <= index) list.add(JSONObject.NULL)
        list[index] = wrap(value)
        return this
    }

    fun remove(index: Int): Any? = if (index in list.indices) list.removeAt(index) else null
    fun opt(index: Int): Any? = list.getOrNull(index)
    fun isNull(index: Int): Boolean = opt(index).let { it == null || it == JSONObject.NULL }
    fun get(index: Int): Any = list.getOrNull(index) ?: throw JSONException("Index $index out of range")

    fun getString(index: Int): String = toStr(get(index)) ?: throw JSONException("Not a string")
    fun getInt(index: Int): Int = toInt(get(index)) ?: throw JSONException("Not an int")
    fun getLong(index: Int): Long = toLong(get(index)) ?: throw JSONException("Not a long")
    fun getDouble(index: Int): Double = toDouble(get(index)) ?: throw JSONException("Not a double")
    fun getBoolean(index: Int): Boolean = toBool(get(index)) ?: throw JSONException("Not a boolean")
    fun getJSONObject(index: Int): JSONObject = get(index) as? JSONObject ?: throw JSONException("Not an object")
    fun getJSONArray(index: Int): JSONArray = get(index) as? JSONArray ?: throw JSONException("Not an array")

    fun optString(index: Int): String = optString(index, "")
    fun optString(index: Int, fallback: String): String = opt(index).let { if (it == null || it == JSONObject.NULL) fallback else toStr(it) } ?: fallback
    fun optInt(index: Int): Int = optInt(index, 0)
    fun optInt(index: Int, fallback: Int): Int = toInt(opt(index)) ?: fallback
    fun optLong(index: Int): Long = optLong(index, 0L)
    fun optLong(index: Int, fallback: Long): Long = toLong(opt(index)) ?: fallback
    fun optDouble(index: Int): Double = optDouble(index, Double.NaN)
    fun optDouble(index: Int, fallback: Double): Double = toDouble(opt(index)) ?: fallback
    fun optBoolean(index: Int): Boolean = optBoolean(index, false)
    fun optBoolean(index: Int, fallback: Boolean): Boolean = toBool(opt(index)) ?: fallback
    fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject
    fun optJSONArray(index: Int): JSONArray? = opt(index) as? JSONArray

    internal fun writeTo(sb: StringBuilder) {
        sb.append('[')
        list.forEachIndexed { i, v ->
            if (i > 0) sb.append(',')
            writeValue(v, sb)
        }
        sb.append(']')
    }

    override fun toString(): String = StringBuilder().also { writeTo(it) }.toString()
}

class JSONTokener(private val s: String) {
    private var i = 0

    private fun skip() {
        while (i < s.length) {
            val c = s[i]
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '﻿') i++ else break
        }
    }

    fun nextValue(): Any? {
        skip()
        if (i >= s.length) throw JSONException("End of input")
        return when (val c = s[i]) {
            '{' -> readObject()
            '[' -> readArray()
            '"', '\'' -> readString(c)
            else -> readLiteral()
        }
    }

    private fun readObject(): JSONObject {
        val o = JSONObject()
        i++
        skip()
        if (i < s.length && s[i] == '}') { i++; return o }
        while (true) {
            skip()
            val k = nextValue()?.toString() ?: throw JSONException("Bad key")
            skip()
            if (i >= s.length || (s[i] != ':' && s[i] != '=')) throw JSONException("Expected ':' at $i")
            i++
            if (i < s.length && s[i] == '>') i++
            o.put(k, nextValue())
            skip()
            if (i >= s.length) throw JSONException("Unterminated object")
            when (s[i]) {
                ',', ';' -> i++
                '}' -> { i++; return o }
                else -> throw JSONException("Expected ',' or '}' at $i")
            }
        }
    }

    private fun readArray(): JSONArray {
        val a = JSONArray()
        i++
        skip()
        if (i < s.length && s[i] == ']') { i++; return a }
        while (true) {
            a.put(nextValue())
            skip()
            if (i >= s.length) throw JSONException("Unterminated array")
            when (s[i]) {
                ',', ';' -> i++
                ']' -> { i++; return a }
                else -> throw JSONException("Expected ',' or ']' at $i")
            }
        }
    }

    private fun readString(q: Char): String {
        i++
        val sb = StringBuilder()
        while (i < s.length) {
            val c = s[i++]
            when {
                c == q -> return sb.toString()
                c == '\\' -> {
                    if (i >= s.length) break
                    when (val e = s[i++]) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'u' -> {
                            sb.append(s.substring(i, i + 4).toInt(16).toChar())
                            i += 4
                        }
                        else -> sb.append(e)
                    }
                }
                else -> sb.append(c)
            }
        }
        throw JSONException("Unterminated string")
    }

    private fun readLiteral(): Any? {
        val start = i
        while (i < s.length && s[i] !in ",:]}/\\\"[{;=# \t\n\r") i++
        val t = s.substring(start, i)
        return when {
            t.isEmpty() -> throw JSONException("Expected literal at $start")
            t == "null" -> JSONObject.NULL
            t == "true" -> true
            t == "false" -> false
            else -> {
                val l = t.toLongOrNull()
                when {
                    l != null && l in Int.MIN_VALUE..Int.MAX_VALUE -> l.toInt()
                    l != null -> l
                    else -> t.toDoubleOrNull() ?: t
                }
            }
        }
    }
}
