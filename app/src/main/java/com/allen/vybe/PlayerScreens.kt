package com.allen.vybe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.allen.vybe.browser.*
import com.allen.vybe.designsystem.*

fun timeLabel(milliseconds: Long): String {val seconds=milliseconds.coerceAtLeast(0)/1000;return "%d:%02d".format(seconds/60,seconds%60)}
fun songCount(count: Int)="$count ${if(count==1)"song" else "songs"}"

@Composable
fun MusicQueueScreen(engine: BrowserPlaybackService?,back: ()->Unit) {
    val player by BrowserPlaybackService.state.collectAsStateWithLifecycle()
    var menu by remember{mutableStateOf<Int?>(null)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item{PageTitle("Your queue","${songCount(player.count)} · Your listening session",back)}
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                ActionIcon(Icons.Rounded.Shuffle,if(player.shuffle)"Disable shuffle" else "Enable shuffle",tint=if(player.shuffle)MaterialTheme.colorScheme.primary else Muted){engine?.command("shuffle")}
                ActionIcon(if(player.repeat==2)Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,when(player.repeat){1->"Repeat all";2->"Repeat one";else->"Repeat off"},tint=if(player.repeat!=0)MaterialTheme.colorScheme.primary else Muted){engine?.command("repeat")}
            }
        }
        if(player.songs.isEmpty())item{EmptyState("Room for your next favourite","Use a song's menu to add it to your queue.")}
        else {
            item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Playing & up next",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);TextButton(onClick={engine?.clearUpcoming()},enabled=player.index<player.count-1){Text("Clear up next")}}}
            itemsIndexed(player.songs,key={_,song->song.id}){index,song->
                Surface(color=if(index==player.index)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.clickable{engine?.select(index)}.padding(start=14.dp,top=10.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) {
                        RemoteArtwork(youtubeArtwork(song.id),"Cover for ${song.title}",Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),targetPixels=128)
                        Column(Modifier.weight(1f).padding(horizontal=12.dp)){Text(song.title,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold,color=if(index==player.index)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface);Text(if(index==player.index)"Now playing" else song.artist,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall,color=Muted)}
                        Box {
                            ActionIcon(Icons.Rounded.MoreVert,"Queue options for ${song.title}"){menu=index}
                            DropdownMenu(expanded=menu==index,onDismissRequest={menu=null}) {
                                DropdownMenuItem(text={Text("Move up")},enabled=index>0,onClick={engine?.move(index,index-1);menu=null})
                                DropdownMenuItem(text={Text("Move down")},enabled=index<player.count-1,onClick={engine?.move(index,index+1);menu=null})
                                DropdownMenuItem(text={Text("Remove from queue")},onClick={engine?.remove(index);menu=null})
                            }
                        }
                    }
                }
            }
        }
    }
}
