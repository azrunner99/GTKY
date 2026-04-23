package com.gtky.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.File
import java.io.FileOutputStream

object PhotoStorage {

    private const val SIZE = 512
    private const val QUALITY = 80
    private const val DIR = "avatars"

    /**
     * Save a bitmap to internal storage at avatars/<userId>.jpg, downscaled to 512x512
     * (center-cropped square) and JPEG quality 80. Returns the absolute file path.
     */
    fun saveAvatar(context: Context, userId: Long, source: Bitmap): String {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, "$userId.jpg")
        val scaled = cropAndScale(source, SIZE)
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        }
        if (scaled !== source) scaled.recycle()
        return file.absolutePath
    }

    fun deleteAvatar(context: Context, userId: Long) {
        val file = File(File(context.filesDir, DIR), "$userId.jpg")
        if (file.exists()) file.delete()
    }

    /**
     * Decode an avatar from disk. Returns null if the file is missing or unreadable.
     */
    fun loadAvatar(path: String): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(path)
        } catch (_: Exception) { null }
    }

    private fun cropAndScale(src: Bitmap, target: Int): Bitmap {
        val w = src.width
        val h = src.height
        val size = minOf(w, h)
        val x = (w - size) / 2
        val y = (h - size) / 2
        val square = Bitmap.createBitmap(src, x, y, size, size)
        if (size == target) return square
        val scale = target.toFloat() / size
        val matrix = Matrix().apply { postScale(scale, scale) }
        val scaled = Bitmap.createBitmap(square, 0, 0, size, size, matrix, true)
        if (scaled !== square) square.recycle()
        return scaled
    }
}
