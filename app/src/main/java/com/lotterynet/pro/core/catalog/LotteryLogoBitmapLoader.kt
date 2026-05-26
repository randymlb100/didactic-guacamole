package com.lotterynet.pro.core.catalog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.LruCache
import com.caverock.androidsvg.SVG
import java.io.File
import java.io.FileOutputStream

object LotteryLogoBitmapLoader {
    private const val MAX_LOGO_DIMENSION_PX = 192
    private const val MAX_CACHE_BYTES = 4 * 1024 * 1024
    private const val DISK_CACHE_VERSION = "v2"

    data class TargetSize(
        val width: Int,
        val height: Int,
    )

    data class VisibleBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        fun width(): Int = right - left
        fun height(): Int = bottom - top
    }

    private val bitmapCache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    fun load(context: Context, assetPath: String?): Bitmap? {
        if (assetPath.isNullOrBlank()) return null
        bitmapCache.get(assetPath)?.let { return it }
        readDiskCachedLogo(context, assetPath)?.let { bitmap ->
            bitmapCache.put(assetPath, bitmap)
            return bitmap
        }
        return runCatching {
            context.assets.open(assetPath).use { stream ->
                when {
                    assetPath.endsWith(".svg", ignoreCase = true) -> {
                        val svg = SVG.getFromInputStream(stream)
                        val sourceWidth = svg.documentWidth.takeIf { it > 0f }?.toInt() ?: 120
                        val sourceHeight = svg.documentHeight.takeIf { it > 0f }?.toInt() ?: 120
                        val target = resolveTargetSize(sourceWidth, sourceHeight)
                        val rendered = Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                            val canvas = Canvas(bitmap)
                            svg.setDocumentWidth(target.width.toFloat())
                            svg.setDocumentHeight(target.height.toFloat())
                            svg.renderToCanvas(canvas)
                        }
                        trimTransparentPadding(rendered)
                    }

                    else -> decodeRasterLogo(context, assetPath)
                }
            }
        }.getOrNull()?.also { bitmap ->
            bitmapCache.put(assetPath, bitmap)
            writeDiskCachedLogo(context, assetPath, bitmap)
        }
    }

    fun peek(assetPath: String?): Bitmap? {
        if (assetPath.isNullOrBlank()) return null
        return bitmapCache.get(assetPath)
    }

    fun prewarm(context: Context, assetPaths: Iterable<String?>) {
        val appContext = context.applicationContext
        normalizeLogoAssetPaths(assetPaths).forEach { assetPath ->
            load(appContext, assetPath)
        }
    }

    internal fun normalizeLogoAssetPaths(assetPaths: Iterable<String?>): List<String> {
        return assetPaths
            .mapNotNull { assetPath -> assetPath?.trim()?.takeIf { it.isNotBlank() } }
            .distinct()
    }

    internal fun cacheFileNameForAsset(assetPath: String): String {
        val safe = assetPath.replace('/', '-').replace('\\', '-').replace(Regex("[^A-Za-z0-9._-]"), "")
        return "$DISK_CACHE_VERSION-$safe.png"
    }

    internal fun resolveTargetSize(
        sourceWidth: Int,
        sourceHeight: Int,
    ): TargetSize {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return TargetSize(MAX_LOGO_DIMENSION_PX, MAX_LOGO_DIMENSION_PX)
        }
        val maxSource = kotlin.math.max(sourceWidth, sourceHeight)
        if (maxSource <= MAX_LOGO_DIMENSION_PX) {
            return TargetSize(sourceWidth, sourceHeight)
        }
        val scale = MAX_LOGO_DIMENSION_PX.toDouble() / maxSource.toDouble()
        return TargetSize(
            width = kotlin.math.max(1, kotlin.math.round(sourceWidth * scale).toInt()),
            height = kotlin.math.max(1, kotlin.math.round(sourceHeight * scale).toInt()),
        )
    }

    internal fun resolveInSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
    ): Int {
        var sample = 1
        val maxSource = kotlin.math.max(sourceWidth, sourceHeight)
        while ((maxSource / (sample * 2)) >= MAX_LOGO_DIMENSION_PX) {
            sample *= 2
        }
        return sample
    }

    internal fun trimTransparentPadding(bitmap: Bitmap, alphaThreshold: Int = 4): Bitmap {
        val bounds = findVisibleLogoBounds(bitmap, alphaThreshold) ?: return bitmap
        if (bounds.left == 0 && bounds.top == 0 && bounds.right == bitmap.width && bounds.bottom == bitmap.height) {
            return bitmap
        }
        return Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height())
    }

    internal fun findVisibleLogoBounds(bitmap: Bitmap, alphaThreshold: Int = 4): Rect? {
        return findVisibleLogoBoundsValues(
            width = bitmap.width,
            height = bitmap.height,
            alphaThreshold = alphaThreshold,
            alphaAt = { x, y -> bitmap.getPixel(x, y) ushr 24 },
        )?.let { Rect(it.left, it.top, it.right, it.bottom) }
    }

    internal fun findVisibleLogoBoundsValues(
        width: Int,
        height: Int,
        alphaThreshold: Int = 4,
        alphaAt: (x: Int, y: Int) -> Int,
    ): VisibleBounds? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = alphaAt(x, y)
                if (alpha > alphaThreshold) {
                    if (x < left) left = x
                    if (y < top) top = y
                    if (x > right) right = x
                    if (y > bottom) bottom = y
                }
            }
        }
        return if (right >= left && bottom >= top) {
            VisibleBounds(left, top, right + 1, bottom + 1)
        } else {
            null
        }
    }

    private fun decodeRasterLogo(context: Context, assetPath: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(assetPath).use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = resolveInSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = context.assets.open(assetPath).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null
        val target = resolveTargetSize(decoded.width, decoded.height)
        val scaled = if (target.width == decoded.width && target.height == decoded.height) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, target.width, target.height, true)
        }
        return trimTransparentPadding(scaled)
    }

    private fun diskCacheDir(context: Context): File {
        return File(context.filesDir, "logo_bitmap_cache").apply { mkdirs() }
    }

    private fun diskCacheFile(context: Context, assetPath: String): File {
        return File(diskCacheDir(context), cacheFileNameForAsset(assetPath))
    }

    private fun readDiskCachedLogo(context: Context, assetPath: String): Bitmap? {
        val file = diskCacheFile(context, assetPath)
        if (!file.exists() || file.length() <= 0L) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun writeDiskCachedLogo(context: Context, assetPath: String, bitmap: Bitmap) {
        runCatching {
            FileOutputStream(diskCacheFile(context, assetPath)).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
    }
}
