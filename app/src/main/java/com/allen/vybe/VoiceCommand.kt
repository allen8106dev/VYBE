package com.allen.vybe

sealed interface VoiceCommand {
    data class Transport(val action: String): VoiceCommand
    data class Shuffle(val enabled: Boolean?): VoiceCommand
    data class Repeat(val mode: Int): VoiceCommand
    data class SeekBy(val milliseconds: Long): VoiceCommand
    data class Timer(val minutes: Int): VoiceCommand
    data class Open(val screen: String): VoiceCommand
    data class PlaySong(val query: String): VoiceCommand
    data class Search(val query: String): VoiceCommand
    data class Like(val enabled: Boolean): VoiceCommand
    data object Help: VoiceCommand
    data object DisableWake: VoiceCommand
}

/** Exact commands stay separate from song names; unknown speech is a title/artist request. */
fun parseVoiceCommand(spoken: String): VoiceCommand {
    val text=spoken.trim().take(200).replace(Regex("[.!?]+$"),"").trim()
        .replace(Regex("^please\\s+|\\s+please$",RegexOption.IGNORE_CASE),"")
    val command=text.lowercase(java.util.Locale.ROOT).replace(Regex("\\s+")," ")
        .removePrefix("please ").removeSuffix(" please")
    val transport=when(command){
        "pause","pause music","pause the music","pause playback" -> "pause"
        "play","resume","resume music","resume playback","continue","continue playing","play music","play the music" -> "play"
        "stop","stop music","stop the music","stop playback" -> "stop"
        "next","skip","skip song","skip this song","next song","next track","skip to next song" -> "next"
        "previous","previous song","previous track","go back","last song" -> "previous"
        "restart","replay","restart song","restart this song","start over" -> "restart"
        else -> null
    }
    if(transport!=null)return VoiceCommand.Transport(transport)
    return when(command){
        "shuffle","shuffle music","shuffle the queue" -> VoiceCommand.Shuffle(null)
        "shuffle on","enable shuffle","turn on shuffle" -> VoiceCommand.Shuffle(true)
        "shuffle off","disable shuffle","turn off shuffle" -> VoiceCommand.Shuffle(false)
        "repeat","repeat all","repeat queue","repeat the queue","repeat on" -> VoiceCommand.Repeat(1)
        "repeat one","repeat song","repeat this song","loop this song","loop song" -> VoiceCommand.Repeat(2)
        "repeat off","turn off repeat","disable repeat","stop repeating" -> VoiceCommand.Repeat(0)
        "cancel timer","cancel sleep timer","turn off sleep timer","sleep timer off" -> VoiceCommand.Timer(0)
        "home","go home","open home","homepage","go to homepage","go to home" -> VoiceCommand.Open("home")
        "open search","search music","find music" -> VoiceCommand.Open("search")
        "open library","my library","your library","show library" -> VoiceCommand.Open("library")
        "queue","open queue","show queue","what's next","up next" -> VoiceCommand.Open("queue")
        "open player","now playing","show player" -> VoiceCommand.Open("browser-player")
        "open settings","settings" -> VoiceCommand.Open("settings")
        "help","voice help","what can you do" -> VoiceCommand.Help
        "like this song","like song","save this song","add to liked songs","like this" -> VoiceCommand.Like(true)
        "unlike this song","unlike song","remove from liked songs","unlike this" -> VoiceCommand.Like(false)
        "stop listening","turn off voice assistant","turn off wake word" -> VoiceCommand.DisableWake
        else -> {
            val timer=Regex("(?:set (?:a )?)?(?:sleep )?timer (?:for )?([a-z0-9 -]+) minutes?").matchEntire(command)?.let{voiceNumber(it.groupValues[1])}
            val seek=Regex("(?:skip |seek )?(forward|back|backward|backwards)(?: by)? ([a-z0-9 -]+) seconds?").matchEntire(command)
            val seconds=seek?.let{voiceNumber(it.groupValues[2])}
            when {
                timer!=null -> VoiceCommand.Timer(timer.coerceIn(1,180))
                seek!=null&&seconds!=null -> VoiceCommand.SeekBy(seconds.toLong().coerceIn(1,999)*1000*(if(seek.groupValues[1]=="forward")1 else -1))
                command.startsWith("search for ") -> VoiceCommand.Search(text.substringAfter(" ").substringAfter(" ").trim())
                command.startsWith("find ")||command.startsWith("search ") -> VoiceCommand.Search(text.substringAfter(" ").trim())
                command.startsWith("play ") -> VoiceCommand.PlaySong(text.substringAfter(" ").trim())
                command.startsWith("listen to ") -> VoiceCommand.PlaySong(text.substringAfter(" ").substringAfter(" ").trim())
                text.isBlank() -> VoiceCommand.Help
                else -> VoiceCommand.PlaySong(text)
            }
        }
    }
}

private fun voiceNumber(text: String): Int? {
    text.toIntOrNull()?.let{return it}
    val values=mapOf("one" to 1,"two" to 2,"three" to 3,"four" to 4,"five" to 5,"six" to 6,"seven" to 7,"eight" to 8,"nine" to 9,"ten" to 10,"eleven" to 11,"twelve" to 12,"thirteen" to 13,"fourteen" to 14,"fifteen" to 15,"sixteen" to 16,"seventeen" to 17,"eighteen" to 18,"nineteen" to 19,"twenty" to 20,"thirty" to 30,"forty" to 40,"fifty" to 50,"sixty" to 60,"seventy" to 70,"eighty" to 80,"ninety" to 90)
    var result=0
    for(word in text.replace('-',' ').split(' ')){if(word=="and")continue;if(word=="hundred"){result=result.coerceAtLeast(1)*100}else result+=values[word]?:return null}
    return result.takeIf{it>0}
}
