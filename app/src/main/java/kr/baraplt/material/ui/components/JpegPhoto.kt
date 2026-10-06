package kr.baraplt.material.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object JpegPhoto {
    fun compress(source: ByteArray): ByteArray {
        if (source.isEmpty()) return byteArrayOf()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return byteArrayOf()
        var sample = 1
        while (bounds.outWidth / sample > 2560 || bounds.outHeight / sample > 2560) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            source,
            0,
            source.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return byteArrayOf()
        val longEdge = maxOf(decoded.width, decoded.height).coerceAtLeast(1)
        val scaled = if (longEdge > 1280) {
            val ratio = 1280f / longEdge
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            decoded
        }
        val rotation = runCatching { rotationDegrees(source) }.getOrDefault(0)
        val upright = if (rotation == 0) {
            scaled
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
        }
        var quality = 72
        var out = toJpeg(upright, quality)
        while (out.size > 300_000 && quality > 40) {
            quality -= 12
            out = toJpeg(upright, quality)
        }
        if (upright !== scaled) upright.recycle()
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        return out
    }

    fun thumbnail(source: ByteArray, maxEdge: Int): Bitmap? {
        if (source.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxEdge * 2 || bounds.outHeight / sample > maxEdge * 2) sample *= 2
        return BitmapFactory.decodeByteArray(
            source,
            0,
            source.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    private fun toJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private fun rotationDegrees(source: ByteArray): Int {
        val exif = ExifInterface(ByteArrayInputStream(source))
        return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }
}
