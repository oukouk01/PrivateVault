package com.example.privatevault.ui.main

import android.content.res.AssetFileDescriptor
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import java.io.ByteArrayInputStream
import java.io.InputStream

/** 把 in-memory 字节数组封装成 MediaDataSource,供 MediaMetadataRetriever 读取视频元数据。 */
class ByteArrayMediaDataSource(private val data: ByteArray) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= data.size) return -1
        val len = minOf(size, data.size - position.toInt())
        System.arraycopy(data, position.toInt(), buffer, offset, len)
        return len
    }
    override fun getSize(): Long = data.size.toLong()
    override fun close() {}
}
