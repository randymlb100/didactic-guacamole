package com.lotterynet.pro.core.render

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

fun renderCacheFileName(key: String): String {
    val safe = key.replace(':', '-').replace(Regex("[^A-Za-z0-9._-]"), "")
    return "$safe.png"
}

fun renderCachePageKey(key: String, index: Int): String {
    return "$key-page-${index + 1}"
}

class LocalRenderCacheRepository(
    private val context: Context,
) {
    private val cacheDir: File
        get() = File(context.cacheDir, "render_cache").apply { mkdirs() }

    fun getUriIfPresent(key: String): Uri? {
        val file = File(cacheDir, renderCacheFileName(key))
        if (!file.exists() || file.length() <= 0L) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun getUrisIfPresent(key: String, pageCount: Int): List<Uri>? {
        if (pageCount <= 0) return null
        val uris = (0 until pageCount).mapNotNull { index ->
            getUriIfPresent(renderCachePageKey(key, index))
        }
        return uris.takeIf { it.size == pageCount }
    }

    fun saveBitmap(key: String, bitmap: Bitmap): Uri? {
        return runCatching {
            val file = File(cacheDir, renderCacheFileName(key))
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    fun saveBitmaps(key: String, bitmaps: List<Bitmap>): List<Uri> {
        if (bitmaps.isEmpty()) return emptyList()
        if (bitmaps.size == 1) return listOfNotNull(saveBitmap(key, bitmaps.first()))
        return bitmaps.mapIndexedNotNull { index, bitmap ->
            saveBitmap(renderCachePageKey(key, index), bitmap)
        }
    }

    fun clear(key: String) {
        File(cacheDir, renderCacheFileName(key)).delete()
        cacheDir.listFiles()
            ?.filter { it.name.startsWith(renderCacheFileName("$key-page-").removeSuffix(".png")) }
            ?.forEach { it.delete() }
    }
}
