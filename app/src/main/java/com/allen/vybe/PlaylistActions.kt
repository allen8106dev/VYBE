package com.allen.vybe

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.allen.vybe.designsystem.*
import com.allen.vybe.domain.*

@Composable
fun PlaylistOverflow(vm: MuseViewModel,playlist: MusicPlaylist,edit: ()->Unit,delete: ()->Unit,editSongs: (()->Unit)?=null,addSongs: (()->Unit)?=null) {
    var expanded by remember{mutableStateOf(false)}
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context=LocalContext.current
    Box {
        ActionIcon(Icons.Rounded.MoreVert,"Options for ${playlist.title}"){expanded=true}
        DropdownMenu(expanded,{expanded=false}) {
            fun choose(action: ()->Unit){expanded=false;action()}
            DropdownMenuItem(text={Text("Edit playlist details")},onClick={choose(edit)},enabled=!busy)
            if(editSongs!=null)DropdownMenuItem(text={Text("Edit songs and order")},onClick={choose(editSongs)},enabled=!busy)
            if(addSongs!=null)DropdownMenuItem(text={Text("Add songs")},onClick={choose(addSongs)},enabled=!busy)
            DropdownMenuItem(text={Text("Add playlist to queue")},onClick={choose{vm.queuePlaylist(playlist)}},enabled=!busy)
            DropdownMenuItem(text={Text("Save a copy on this phone")},onClick={choose{vm.duplicate(playlist)}},enabled=!busy)
            DropdownMenuItem(text={Text(if(playlist.cloud)"Share playlist" else "Share song list")},onClick={choose{vm.sharePlaylist(playlist){text->runCatching{context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text),"Share playlist"))}.onFailure{vm.notify("No app can share this playlist.")}}}},enabled=!busy)
            HorizontalDivider()
            DropdownMenuItem(text={Text("Delete playlist",color=MaterialTheme.colorScheme.error)},onClick={choose(delete)},enabled=!busy)
        }
    }
}

@Composable
fun EditPlaylistDialog(vm: MuseViewModel,playlist: MusicPlaylist,dismiss: ()->Unit) {
    var name by rememberSaveable(playlist.id){mutableStateOf(playlist.title)}
    var description by rememberSaveable(playlist.id){mutableStateOf(playlist.description)}
    var error by remember{mutableStateOf<String?>(null)}
    val busy by vm.busy.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest={if(!busy)dismiss()},title={Text("Edit playlist")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(name,{name=it.take(150)},label={Text("Playlist name")},singleLine=true,enabled=!busy)
        OutlinedTextField(description,{description=it.take(5000)},label={Text("Description")},minLines=2,maxLines=4,enabled=!busy)
        if(playlist.cloud)Text("Changes are saved to your YouTube account.",style=MaterialTheme.typography.bodySmall,color=Muted)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    }},confirmButton={Button(onClick={error=null;vm.edit(playlist,name.trim(),description.trim(),dismiss,{error=it})},enabled=name.isNotBlank()&&!busy){Text("Save")}},dismissButton={TextButton(onClick=dismiss,enabled=!busy){Text("Cancel")}})
}

@Composable
fun DeletePlaylistDialog(vm: MuseViewModel,playlist: MusicPlaylist,dismiss: ()->Unit,deleted: ()->Unit={}) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest={if(!busy)dismiss()},title={Text("Delete this playlist?")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(if(playlist.cloud)"Delete ${playlist.title} from your YouTube account? This cannot be undone." else "Delete ${playlist.title} from this phone? Liked songs and the playing queue stay saved.")
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    }},confirmButton={TextButton(onClick={error=null;vm.delete(playlist,{dismiss();deleted()},{error=it})},enabled=!busy){Text("Delete",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick=dismiss,enabled=!busy){Text("Cancel")}})
}

@Composable
fun SavedTrackRow(vm: MuseViewModel,track: Track,play: ()->Unit,more: ()->Unit) {
    val liked by vm.favourites.collectAsStateWithLifecycle()
    val current by vm.music.currentSongId.collectAsStateWithLifecycle()
    TrackRow(track,play,more,if(track.available)({vm.like(track)})else null,track.id in liked,track.id==current)
}
