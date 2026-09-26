package com.moniuh.scan3d.core

/** Open-addressing hash map from Long to Int without boxing. [Long.MIN_VALUE] is reserved. */
internal class LongIntMap(initialCapacity: Int = 1024) {
    private var keys: LongArray
    private var values: IntArray
    private var mask: Int
    var size = 0
        private set

    init {
        var cap = 16
        while (cap < initialCapacity * 2) cap = cap shl 1
        keys = LongArray(cap) { EMPTY }
        values = IntArray(cap)
        mask = cap - 1
    }

    fun get(key: Long, default: Int = -1): Int {
        var i = slot(key)
        while (true) {
            val k = keys[i]
            if (k == EMPTY) return default
            if (k == key) return values[i]
            i = (i + 1) and mask
        }
    }

    fun put(key: Long, value: Int) {
        if ((size + 1) * 2 > keys.size) grow()
        var i = slot(key)
        while (true) {
            val k = keys[i]
            if (k == EMPTY) {
                keys[i] = key
                values[i] = value
                size++
                return
            }
            if (k == key) {
                values[i] = value
                return
            }
            i = (i + 1) and mask
        }
    }

    private fun slot(key: Long): Int {
        var h = key * -0x61c8864680b583ebL
        h = h xor (h ushr 29)
        return h.toInt() and mask
    }

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        keys = LongArray(oldKeys.size * 2) { EMPTY }
        values = IntArray(oldKeys.size * 2)
        mask = keys.size - 1
        size = 0
        for (i in oldKeys.indices) if (oldKeys[i] != EMPTY) put(oldKeys[i], oldValues[i])
    }

    companion object {
        const val EMPTY = Long.MIN_VALUE
    }
}

internal class FloatList(capacity: Int = 1024) {
    var data = FloatArray(capacity)
        private set
    var size = 0
        private set

    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    fun toArray(): FloatArray = data.copyOf(size)
}

internal class IntList(capacity: Int = 1024) {
    var data = IntArray(capacity)
        private set
    var size = 0
        private set

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    operator fun get(i: Int) = data[i]

    fun toArray(): IntArray = data.copyOf(size)
}

internal class LongList(capacity: Int = 1024) {
    var data = LongArray(capacity)
        private set
    var size = 0
        private set

    fun add(v: Long) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    operator fun get(i: Int) = data[i]
}

internal class ByteList(capacity: Int = 1024) {
    var data = ByteArray(capacity)
        private set
    var size = 0
        private set

    fun add(v: Byte) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    fun toArray(): ByteArray = data.copyOf(size)
}

/** Packs three signed 21-bit integers into one Long. */
internal fun packXyz(x: Int, y: Int, z: Int): Long =
    ((x + OFFSET_21).toLong() shl 42) or ((y + OFFSET_21).toLong() shl 21) or (z + OFFSET_21).toLong()

internal fun unpackX(key: Long): Int = ((key ushr 42) and MASK_21).toInt() - OFFSET_21
internal fun unpackY(key: Long): Int = ((key ushr 21) and MASK_21).toInt() - OFFSET_21
internal fun unpackZ(key: Long): Int = (key and MASK_21).toInt() - OFFSET_21

private const val OFFSET_21 = 1 shl 20
private const val MASK_21 = (1L shl 21) - 1
