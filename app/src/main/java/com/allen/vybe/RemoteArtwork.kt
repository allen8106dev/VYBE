package com.allen.vybe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.HttpURLConnection
import java.net.URI

private val artworkCache = object : android.util.LruCache<String, Bitmap>(12 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = (value.allocationByteCount / 1024).coerceAtLeast(1)
}

private val artworkSlots = Semaphore(2)
private val artworkBytes = object : android.util.LruCache<String, ByteArray>(4 * 1024) {
    override fun sizeOf(key: String, value: ByteArray) = (value.size / 1024).coerceAtLeast(1)
}

@Composable
fun RemoteArtwork(url: String, description: String, modifier: Modifier = Modifier, targetPixels: Int = 720) {
    val cacheKey = "$url|${targetPixels.coerceIn(64, 1024)}"
    val bitmap by produceState<Bitmap?>(null, cacheKey) {
        value = withContext(Dispatchers.IO) {
            artworkCache.get(cacheKey)?.let { return@withContext it }
            artworkSlots.withPermit {
            currentCoroutineContext().ensureActive()
            runCatching {
                artworkCache.get(cacheKey) ?: run {
                    val uri = URI(url)
                    require(uri.scheme == "https" && uri.host in setOf("i.ytimg.com", "yt3.ggpht.com", "yt3.googleusercontent.com"))
                    val bytes = artworkBytes.get(url) ?: run {
                    val connection = uri.toURL().openConnection() as HttpURLConnection
                    connection.connectTimeout = 8000; connection.readTimeout = 8000; connection.instanceFollowRedirects = false
                    try {
                        require(connection.responseCode == 200)
                        val bytes = connection.inputStream.use { input ->
                            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                            while(true) { val n = input.read(buffer); if(n < 0) break; require(out.size() + n <= 2_000_000); out.write(buffer, 0, n) }; out.toByteArray()
                        }
                        artworkBytes.put(url, bytes); bytes
                    } finally { connection.disconnect() }
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192)
                    val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / targetPixels.coerceIn(64, 1024)).coerceAtLeast(1) }
                    val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options))
                    val image = if(uri.host == "i.ytimg.com") trimThumbnailBars(decoded) else decoded
                    if(image !== decoded)decoded.recycle()
                    image.also { artworkCache.put(cacheKey, it) }
                }
            }.getOrNull()
            }
        }
    }
    if(bitmap != null) Image(bitmap!!.asImageBitmap(), description, modifier, contentScale = ContentScale.Crop)
    else Icon(Icons.Rounded.MusicNote, description, modifier.background(Color(0xFF244936)))
}

/** Inspect only a small border sample, once on the image worker, before caching. */
internal fun trimThumbnailBars(bitmap: Bitmap): Bitmap {
    if(bitmap.width <= bitmap.height || bitmap.height < 16)return bitmap
    fun darkRow(y: Int): Boolean = (1..16).count { n ->
        val pixel=bitmap.getPixel((n*bitmap.width/17).coerceAtMost(bitmap.width-1),y)
        android.graphics.Color.red(pixel)<24 && android.graphics.Color.green(pixel)<24 && android.graphics.Color.blue(pixel)<24
    } >= 15
    var border=0
    val limit=bitmap.height/4
    while(border<limit && darkRow(border) && darkRow(bitmap.height-1-border))border++
    // Ignore a thin compression edge; avoid zooming genuinely dark artwork.
    if(border<bitmap.height/24 || border==limit)return bitmap
    return Bitmap.createBitmap(bitmap,0,border,bitmap.width,bitmap.height-2*border)
}

fun youtubeArtwork(id: String) = if(id.matches(Regex("[A-Za-z0-9_-]{11}"))) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else ""
