package com.example.privatevault.data

import android.content.Context
import java.io.File

// Private media storage:
//   <filesDir>/private_media/images/*.enc
//   <filesDir>/private_media/videos/*.enc
//   Creates .nomedia in each subdirectory
class VaultRepository(private val context: Context) {

    enum class MediaType(val subdir: String) {
        IMAGE("images"), VIDEO("videos")
    }

    data class MediaItem(
        val file: File,
        val type: MediaType,
        val sizeBytes: Long,
        val modifiedAt: Long
    )

    val root: File
        get() = File(context.filesDir, "private_media").apply { if (!exists()) mkdirs() }

    fun dirFor(type: MediaType): File = File(root, type.subdir).apply {
        if (!exists()) mkdirs()
        ensureNoMedia(this)
    }

    // Create .nomedia in each subdirectory to prevent media scanner from indexing encrypted files
    fun ensureNoMedia() {
        ensureNoMedia(dirFor(MediaType.IMAGE))
        ensureNoMedia(dirFor(MediaType.VIDEO))
        ensureNoMedia(root)
    }

    private fun ensureNoMedia(dir: File) {
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) {
            noMedia.writeText("")
        }
    }

    fun list(type: MediaType): List<MediaItem> {
        val dir = dirFor(type)
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".enc") }
            ?.map { MediaItem(it, type, it.length(), it.lastModified()) }
            ?.sortedByDescending { it.modifiedAt }
            ?: emptyList()
    }

    // Create a new encrypted file path in the appropriate directory
    fun newEncryptedFile(type: MediaType, suffix: String = ".enc"): File {
        val dir = dirFor(type)
        val name = "${System.currentTimeMillis()}_${(0..0xffff).random().toString(16)}$suffix"
        return File(dir, name)
    }

    fun deleteItem(item: MediaItem): Boolean = item.file.delete()
}