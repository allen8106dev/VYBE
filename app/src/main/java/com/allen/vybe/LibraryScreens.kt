package com.allen.vybe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.allen.vybe.designsystem.*
import com.allen.vybe.domain.*

@Composable
fun SearchScreen(vm: MuseViewModel,play: (Track)->Unit,add: (Track)->Unit,openChannel: (String)->Unit,onAccount: ()->Unit,onVoice: ()->Unit={}) {
    val state by vm.search.collectAsStateWithLifecycle()
    val recent by vm.recentSearches.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    var query by remember(state.query) {mutableStateOf(state.query)}
    var filter by rememberSaveable {mutableStateOf("Songs")}
    var searchFocused by remember {mutableStateOf(false)}
    fun submit(){vm.search(query)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {PageTitle("Search","A song, an artist, a whole new mood.")}
        item {
            OutlinedTextField(query,{value->query=value.take(200);vm.searchAsYouType(query)},Modifier.fillMaxWidth().onFocusChanged{searchFocused=it.isFocused},shape=RoundedCornerShape(18.dp),placeholder={Text("Search songs or artists")},leadingIcon={Icon(Icons.Rounded.Search,null)},trailingIcon={Row {
                ActionIcon(Icons.Rounded.Mic,"Find and play a song by voice",click=onVoice)
                ActionIcon(Icons.AutoMirrored.Rounded.ArrowForward,"Submit search",enabled=query.isNotBlank(),click=::submit)
            }},singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={submit()}))
            if(state.submitted||state.tracks.isNotEmpty()||state.channels.isNotEmpty())Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Songs","Channels").forEach {name->FilterChip(filter==name,{filter=name},label={Text(name)})}}
        }
        if(!account.connected)item {
            Card(onClick=onAccount,colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Connect to discover more",fontWeight=FontWeight.Bold);Text("Connect your YouTube account to search for songs and artists.",color=Muted);Text("Open Settings",color=MaterialTheme.colorScheme.primary)}}
        }
        if(searchFocused&&query.isBlank()&&recent.isNotEmpty()) {
            item{SectionTitle("Recent searches")}
            items(recent,key={"recent-${it.id}"}){track->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)){SavedTrackRow(vm,track,{play(track)},{add(track)})}
                    ActionIcon(Icons.Rounded.Close,"Remove ${track.title} from recent searches"){vm.removeRecentSearch(track.id)}
                }
            }
            item{TextButton(onClick=vm::clearRecentSearches,modifier=Modifier.fillMaxWidth()){Text("Clear recents")}}
        }
        if(!state.submitted&&query.trim().length==1)item{Text("Type one more character to see results.",Modifier.padding(vertical=8.dp),color=Muted)}
        if(state.loading)item{LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=12.dp))}
        state.error?.let {error->item{EmptyState("Couldn't search",error,Icons.Rounded.CloudOff);TextButton(onClick=::submit){Text("Try again")}}}
        if(state.submitted&&!state.loading&&state.error==null&&state.tracks.isEmpty()&&state.channels.isEmpty())item{EmptyState("No songs found","Try another title or artist.",Icons.Rounded.Search)}
        if(!state.submitted&&account.connected&&query.trim().length>=2&&!state.loading&&state.error==null&&state.tracks.isEmpty()&&state.channels.isEmpty())item{EmptyState("No matches yet","Try another title or artist.",Icons.Rounded.Search)}
        if(filter=="Songs")items(state.tracks,key={it.id}){track->SavedTrackRow(vm,track,{play(track)},{add(track)})}
        if(filter=="Channels")items(state.channels,key={it.id}){channel->ListItem(headlineContent={Text(channel.title)},supportingContent={Text("View channel on YouTube")},leadingContent={Icon(Icons.Rounded.AccountCircle,null)},modifier=Modifier.clickable{openChannel(channel.id)},colors=ListItemDefaults.colors(containerColor=Color.Transparent))}
        if(state.next!=null)item{OutlinedButton(onClick={vm.search(state.query,more=true)},enabled=!state.loading,modifier=Modifier.fillMaxWidth()){Text("More results")}}
    }
}

@Composable
fun LibraryScreen(vm: MuseViewModel,onOpen: (MusicPlaylist)->Unit,onOpenLiked: ()->Unit,onSettings: ()->Unit) {
    val local by vm.playlists.collectAsStateWithLifecycle()
    val cloud by vm.cloud.collectAsStateWithLifecycle()
    val liked by vm.likedSongs.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    var query by rememberSaveable{mutableStateOf("")}
    var newPlaylist by rememberSaveable{mutableStateOf(false)}
    var editingPlaylist by remember{mutableStateOf<MusicPlaylist?>(null)}
    var deletingPlaylist by remember{mutableStateOf<MusicPlaylist?>(null)}
    var alphabetical by rememberSaveable{mutableStateOf(true)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item {PageTitle("Your library","Keep the songs that stay with you.",action={FilledTonalIconButton(onClick={newPlaylist=true}){Icon(Icons.Rounded.Add,"Create playlist")}})}
        if(query.isBlank()||"liked songs".contains(query.trim(),true))item{LikedSongsLibraryRow(liked.size,onOpenLiked)}
        item{OutlinedTextField(query,{query=it.take(150)},Modifier.fillMaxWidth().padding(vertical=8.dp),shape=RoundedCornerShape(18.dp),placeholder={Text("Search your library")},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true,trailingIcon={if(query.isNotBlank())ActionIcon(Icons.Rounded.Close,"Clear library search"){query=""}})}
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Your playlists",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);ActionIcon(Icons.Rounded.Add,"Create playlist"){newPlaylist=true};ActionIcon(Icons.AutoMirrored.Rounded.Sort,if(alphabetical)"Sort Z to A" else "Sort A to Z"){alphabetical=!alphabetical};if(account.connected)ActionIcon(Icons.Rounded.Refresh,"Refresh YouTube playlists",enabled=!account.loading,click=vm::refreshCloud)}}
        val lists=(local+cloud).filter{it.title.contains(query,true)}.let{if(alphabetical)it.sortedBy{p->p.title.lowercase()}else it.sortedByDescending{p->p.title.lowercase()}}
        if(lists.isEmpty()&&!account.loading)item{EmptyState(if(query.isBlank())"Make it a playlist" else "No matching playlists",if(query.isBlank())"Create a collection for your favourites, late nights or next road trip." else "Try a different playlist name.",Icons.AutoMirrored.Rounded.QueueMusic);Button(onClick={newPlaylist=true},modifier=Modifier.fillMaxWidth()){Text("Create playlist")}}
        items(lists,key={"${it.cloud}-${it.id}"}){playlist->PlaylistRow(playlist,{onOpen(playlist)}){PlaylistOverflow(vm,playlist,{editingPlaylist=playlist},{deletingPlaylist=playlist})}}
        if(account.loading)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        account.error?.let{error->item{Text(error,color=MaterialTheme.colorScheme.error);TextButton(onClick=vm::refreshCloud){Text("Retry sync")}}}
        if(!account.connected)item{OutlinedCard(onClick=onSettings,modifier=Modifier.fillMaxWidth().padding(top=12.dp)){Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.CloudQueue,null,tint=MaterialTheme.colorScheme.primary);Column(Modifier.weight(1f).padding(start=14.dp)){Text("Your YouTube playlists",fontWeight=FontWeight.SemiBold);Text("Connect your account to bring them here",color=Muted,style=MaterialTheme.typography.bodySmall)}}}}
    }
    editingPlaylist?.let{EditPlaylistDialog(vm,it){editingPlaylist=null}}
    deletingPlaylist?.let{DeletePlaylistDialog(vm,it,{deletingPlaylist=null})}
    if(newPlaylist)CreatePlaylistDialog(account.connected,{newPlaylist=false}){name,inCloud->vm.create(name,inCloud);newPlaylist=false}
}

@Composable
fun LikedSongsLibraryRow(count: Int,open: ()->Unit) {
    Surface(onClick=open,shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) {
                Icon(Icons.Rounded.Favorite,null,Modifier.size(26.dp),tint=MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(horizontal=16.dp)) {
                Text("Liked songs",fontWeight=FontWeight.SemiBold)
                Text("${songCount(count)} saved",style=MaterialTheme.typography.bodySmall,color=Muted)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,null,tint=Muted)
        }
    }
}

@Composable
fun LikedSongsScreen(vm: MuseViewModel,back: ()->Unit,add: (Track)->Unit) {
    val liked by vm.likedSongs.collectAsStateWithLifecycle()
    var query by rememberSaveable{mutableStateOf("")}
    val songs=remember(liked,query){liked.filter{it.title.contains(query,true)||it.artist.contains(query,true)}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item{PageTitle("Liked songs", "${songCount(liked.size)} saved",back=back)}
        item{OutlinedTextField(query,{query=it.take(150)},Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),placeholder={Text("Find a liked song")},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true,trailingIcon={if(query.isNotBlank())ActionIcon(Icons.Rounded.Close,"Clear liked songs search"){query=""}})}
        if(songs.isNotEmpty())item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={vm.play(songs)},modifier=Modifier.weight(1f)){Icon(Icons.Rounded.PlayArrow,null);Text("Play all")};FilledTonalButton(onClick={vm.play(songs,shuffle=true)},modifier=Modifier.weight(1f)){Icon(Icons.Rounded.Shuffle,null);Text("Shuffle")}}}
        if(songs.isEmpty())item{EmptyState(if(query.isBlank())"A place for your favourites" else "No matching songs",if(query.isBlank())"Like a song from its menu to save it here." else "Try another title or artist.",Icons.Rounded.FavoriteBorder)}
        itemsIndexed(songs,key={index,track->"${track.id}-$index"}){index,track->SavedTrackRow(vm,track,{vm.play(songs,index)},{add(track)})}
    }
}

@Composable
fun PlaylistRow(playlist: MusicPlaylist,open: ()->Unit,options: (@Composable ()->Unit)?=null) {
    Surface(onClick=open,shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center){Icon(Icons.AutoMirrored.Rounded.QueueMusic,null,Modifier.size(28.dp),tint=MaterialTheme.colorScheme.primary)}
            Column(Modifier.weight(1f).padding(horizontal=16.dp)){Text(playlist.title,fontWeight=FontWeight.SemiBold,maxLines=2);Text("${songCount(playlist.count)} · ${if(playlist.cloud)"YouTube account" else "On this phone"}",style=MaterialTheme.typography.bodySmall,color=Muted)}
            if(options!=null)options() else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,null,tint=Muted)
        }
    }
}

@Composable
fun PlaylistScreen(vm: MuseViewModel,back: ()->Unit,play: (List<Track>,Int,Boolean)->Unit,add: (Track)->Unit,addSongs: ()->Unit={}) {
    val state by vm.selected.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var details by rememberSaveable{mutableStateOf(false)}
    var delete by rememberSaveable{mutableStateOf(false)}
    var editing by rememberSaveable{mutableStateOf(false)}
    var query by rememberSaveable{mutableStateOf("")}
    var sort by rememberSaveable{mutableStateOf("Custom order")}
    var sortMenu by remember{mutableStateOf(false)}
    val playlist=state.playlist
    val songs=remember(state.tracks,query,sort) {
        val found=state.tracks.filter{it.title.contains(query,true)||it.artist.contains(query,true)}
        when(sort){"Title"->found.sortedBy{it.title.lowercase()};"Artist"->found.sortedBy{it.artist.lowercase()};else->found}
    }
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item{PageTitle("Playlist",back=back,action={if(playlist!=null)PlaylistOverflow(vm,playlist,{details=true},{delete=true},{editing=!editing;query="";sort="Custom order"},addSongs)})}
        if(playlist==null)item{if(state.loading)LinearProgressIndicator(Modifier.fillMaxWidth())else EmptyState("Playlist unavailable",state.error?:"Refresh your library and try again.")}
        else {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
                    Text(playlist.title,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                    Text("${songCount(state.tracks.size)} - ${if(playlist.cloud)"YouTube account" else "On this phone"}",color=Muted)
                    if(playlist.description.isNotBlank())Text(playlist.description,color=Muted,maxLines=3)
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Button(onClick={play(songs,0,false)},enabled=songs.any{it.available}){Icon(Icons.Rounded.PlayArrow,null);Text("Play all")}
                        FilledTonalButton(onClick={play(songs,0,true)},enabled=songs.any{it.available}){Icon(Icons.Rounded.Shuffle,null);Text("Shuffle")}
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick=addSongs){Icon(Icons.Rounded.Add,null);Text("Add songs")}
                    TextButton(onClick={editing=!editing;query="";sort="Custom order"},enabled=!busy){Icon(if(editing)Icons.Rounded.Check else Icons.Rounded.Edit,null);Text(if(editing)"Done editing" else "Edit songs")}
                }
                if(editing)Text("Use the arrows to change order, or remove a song. Playback keeps going.",style=MaterialTheme.typography.bodySmall,color=Muted)
                else OutlinedTextField(query,{query=it.take(200)},Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),placeholder={Text("Find in playlist")},leadingIcon={Icon(Icons.Rounded.Search,null)},singleLine=true,trailingIcon={if(query.isNotBlank())ActionIcon(Icons.Rounded.Close,"Clear playlist search"){query=""}})
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Box {
                        TextButton(onClick={sortMenu=true},enabled=!editing){Icon(Icons.AutoMirrored.Rounded.Sort,null);Text(sort)}
                        DropdownMenu(sortMenu,{sortMenu=false}){listOf("Custom order","Title","Artist").forEach{label->DropdownMenuItem(text={Text(label)},onClick={sort=label;sortMenu=false})}}
                    }
                    ActionIcon(Icons.Rounded.Refresh,"Refresh playlist songs",enabled=!busy&&!state.loading){vm.select(playlist.id,playlist.cloud)}
                }
                if(state.loading||busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.error?.let{item{EmptyState("Couldn't load songs",it,Icons.Rounded.CloudOff)}}
            if(!state.loading&&state.error==null&&state.tracks.isEmpty())item{EmptyState("Your mix starts here","Add songs from Search using their song menu.")}
            else if(songs.isEmpty()&&query.isNotBlank())item{EmptyState("No matching songs","Try another title or artist.",Icons.Rounded.Search)}
            itemsIndexed(songs,key={n,t->t.itemId?:"${t.id}-$n"}){index,track->
                Column {
                    SavedTrackRow(vm,track,{play(songs,index,false)},{add(track)})
                    if(editing)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){ActionIcon(Icons.Rounded.KeyboardArrowUp,"Move ${track.title} up",enabled=index>0&&!busy){vm.move(playlist,track,index-1)};ActionIcon(Icons.Rounded.KeyboardArrowDown,"Move ${track.title} down",enabled=index<songs.lastIndex&&!busy){vm.move(playlist,track,index+1)};ActionIcon(Icons.Rounded.RemoveCircleOutline,"Remove ${track.title}",enabled=!busy){vm.remove(playlist,track)}}
                }
            }
        }
    }
    if(details&&playlist!=null)EditPlaylistDialog(vm,playlist){details=false}
    if(delete&&playlist!=null)DeletePlaylistDialog(vm,playlist,{delete=false},back)
}

@Composable
fun NameDialog(title: String,initial: String,dismiss: ()->Unit,save: (String)->Unit) {
    var name by rememberSaveable{mutableStateOf(initial)}
    AlertDialog(onDismissRequest=dismiss,title={Text(title)},text={OutlinedTextField(name,{name=it.take(150)},label={Text("Playlist name")},singleLine=true)},confirmButton={Button(onClick={save(name.trim())},enabled=name.isNotBlank()){Text("Save")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}

@Composable
fun CreatePlaylistDialog(connected: Boolean,dismiss: ()->Unit,save: (String,Boolean)->Unit) {
    var name by rememberSaveable{mutableStateOf("")};var cloud by rememberSaveable{mutableStateOf(false)}
    AlertDialog(onDismissRequest=dismiss,title={Text("New playlist")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){OutlinedTextField(name,{name=it.take(150)},label={Text("Playlist name")},singleLine=true);Text("Save to",style=MaterialTheme.typography.labelLarge);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(!cloud,{cloud=false},label={Text("This phone")});FilterChip(cloud,{cloud=true},enabled=connected,label={Text("YouTube account")})};if(!connected)Text("Connect YouTube in Settings to sync playlists.",style=MaterialTheme.typography.bodySmall,color=Muted)}},confirmButton={Button(onClick={save(name.trim(),cloud)},enabled=name.isNotBlank()){Text("Create")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})
}

@Composable
fun AddToPlaylistDialog(vm: MuseViewModel,track: Track,onDismiss: ()->Unit) {
    val local by vm.playlists.collectAsStateWithLifecycle();val cloud by vm.cloud.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    var creating by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Add to playlist")},text={LazyColumn(Modifier.heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){items(local+cloud,key={"${it.cloud}-${it.id}"}){p->TextButton(onClick={vm.add(p,track);onDismiss()},enabled=!busy){Column(Modifier.fillMaxWidth()){Text(p.title);Text(if(p.cloud)"YouTube account" else "On this phone",style=MaterialTheme.typography.bodySmall,color=Muted)}}};item{TextButton(onClick={creating=true},enabled=!busy){Icon(Icons.Rounded.Add,null);Text("New playlist")}}}},confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
    if(creating)NameDialog("New playlist","",{creating=false}){name->vm.createAndAdd(name,track);creating=false;onDismiss()}
}
