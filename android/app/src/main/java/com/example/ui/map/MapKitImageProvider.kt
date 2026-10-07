package com.example.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.yandex.runtime.image.ImageProvider

fun imageProviderFromVector(context: Context, @DrawableRes resId: Int): ImageProvider {
    val drawable = ContextCompat.getDrawable(context, resId) ?: throw IllegalArgumentException("Drawable not found")
    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth,
        drawable.intrinsicHeight,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return ImageProvider.fromBitmap(bitmap)
}
