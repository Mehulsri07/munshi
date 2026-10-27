package com.opuscube.munshi.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

// Phone photos are large; a 1280 px JPEG is plenty to read a shelf and uploads fast.
fun scaledSize(w: Int, h: Int, max: Int = 1280): Pair<Int, Int> {
    val s = minOf(1.0, max.toDouble() / maxOf(w, h))
    return (w * s).roundToInt() to (h * s).roundToInt()
}

fun newPhotoUri(context: Context): Uri {
    val f = File(context.cacheDir, "shelf/shelf.jpg").apply { parentFile?.mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
}

fun jpegBytes(context: Context, uri: Uri): ByteArray {
    val bmp: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            val (w, h) = scaledSize(info.size.width, info.size.height); d.setTargetSize(w, h)
        }
    } else {
        val raw = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        val (w, h) = scaledSize(raw.width, raw.height); Bitmap.createScaledBitmap(raw, w, h, true)
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
    return out.toByteArray()
}
