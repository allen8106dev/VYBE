package com.allen.vybe.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.allen.vybe.domain.*

val Muted @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
@Composable
fun Artwork(palette: Int, modifier: Modifier = Modifier, label: String = "Original Muse artwork") {
    val colors = when (palette % 3) {
        1 -> listOf(Color(0xFFB68ADA), Color(0xFF312240))
        2 -> listOf(Color(0xFFF0B58D), Color(0xFF543040))
        else -> listOf(Color(0xFF6FC6A8), Color(0xFF153F3F))
    }
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(colors)).semantics { contentDescription = label }) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.minDimension
            val center = Offset(size.width * .57f, size.height * .43f)
            for (i in 0..8) drawCircle(Color.White.copy(alpha = .055f + i * .009f), unit * (.13f + i * .052f), center, style = Stroke(unit * .021f))
            drawCircle(Color(0xFF081E1B).copy(alpha = .55f), unit * .12f, center)
            drawCircle(Color.White.copy(alpha = .8f), unit * .012f, center)
            drawLine(Color.White.copy(alpha = .3f), Offset(unit * .12f, size.height * .82f), Offset(size.width * .88f, size.height * .82f), unit * .005f, StrokeCap.Round)
        }
    }
}
@Composable
fun ActionIcon(icon: ImageVector, description: String, enabled: Boolean = true, tint: Color = LocalContentColor.current, click: () -> Unit) {
    IconButton(onClick = click, enabled = enabled) { Icon(icon, contentDescription = description, tint = if (enabled) tint else tint.copy(alpha = .3f)) }
}
@Composable
fun PageTitle(title: String, subtitle: String? = null, back: (() -> Unit)? = null, action: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", click = back)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        action()
    }
}
@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 20.dp, bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable
fun EmptyState(title: String, text: String, icon: ImageVector = Icons.Rounded.MusicNote) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.widthIn(max = 320.dp))
    }
}
@Composable
fun TrackRow(track: Track, onPlay: () -> Unit, onAdd: (() -> Unit)? = null, onLike: (() -> Unit)? = null, liked: Boolean = false, active: Boolean = false, extra: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = track.available, onClick = onPlay).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        com.allen.vybe.RemoteArtwork(track.artworkUri ?: com.allen.vybe.youtubeArtwork(track.id), "Cover for ${track.title}", Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)), targetPixels = 160)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current, fontWeight = FontWeight.SemiBold)
            Text(if (!track.available) "Unavailable video" else track.artist, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        if (onLike != null) ActionIcon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Remove favourite" else "Favourite ${track.title}", tint = if (liked) MaterialTheme.colorScheme.primary else Muted, click = onLike)
        if (onAdd != null) ActionIcon(Icons.Rounded.MoreVert, "Song options for ${track.title}", enabled = track.available, click = onAdd)
        extra()
    }
}
