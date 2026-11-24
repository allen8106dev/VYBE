package com.allen.vybe

import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

data class VoiceTranscript(val final: Boolean,val text: String)

/** Open vocabulary for songs, plus a small parallel grammar for accurate controls. */
class OfflineVoiceDecoder(model: Model,private val onResult: (String,String,String)->Unit={_,_,_->}): AutoCloseable {
    private val speech=Recognizer(model,16000f).apply{setWords(true)}
    private val controls=Recognizer(model,16000f,grammar()).apply{setWords(true);setPartialWords(true)}
    private var controlResult=""
    fun accept(audio: ShortArray,count: Int): VoiceTranscript {
        if(controls.acceptWaveForm(audio,count))controlResult=controls.result
        if(!speech.acceptWaveForm(audio,count))return VoiceTranscript(false,JSONObject(speech.partialResult).optString("partial"))
        val spoken=JSONObject(speech.result)
        val plain=spoken.optString("text")
        val partial=JSONObject(controls.partialResult)
        val partialCandidate=JSONObject().put("text",partial.optString("partial")).put("result",partial.optJSONArray("partial_result")?:JSONArray()).toString()
        // Each decoder finds silence at slightly different times. Keep both streams
        // intact, and compare word times instead of flushing the next word's onset.
        val candidate=listOf(controlResult,partialCandidate).filter{it.isNotBlank()}
            .maxByOrNull{overlap(spoken,JSONObject(it))}.orEmpty()
            .takeIf{it.isNotBlank()&&overlap(spoken,JSONObject(it))>0}.orEmpty()
        val selected=choose(plain,candidate)
        onResult(plain,candidate,selected)
        return VoiceTranscript(true,selected)
    }
    fun finish(): VoiceTranscript {
        val plain=JSONObject(speech.finalResult).optString("text")
        val tail=controls.finalResult
        return VoiceTranscript(true,choose(plain,if(JSONObject(tail).optString("text").isNotBlank())tail else controlResult))
    }
    fun reset(){speech.reset();controls.reset();controlResult=""}
    override fun close(){controls.close();speech.close()}
    private fun overlap(first: JSONObject,second: JSONObject): Double {
        val a=first.optJSONArray("result")?:return 0.0
        val b=second.optJSONArray("result")?:return 0.0
        if(a.length()==0||b.length()==0)return 0.0
        val start=maxOf(a.getJSONObject(0).optDouble("start"),b.getJSONObject(0).optDouble("start"))
        val end=minOf(a.getJSONObject(a.length()-1).optDouble("end"),b.getJSONObject(b.length()-1).optDouble("end"))
        return (end-start).coerceAtLeast(0.0)
    }
    private fun choose(plain: String,json: String): String {
        if(json.isBlank())return plain
        val result=JSONObject(json);val candidate=result.optString("text")
        val withoutWake: (String)->String={it.replace(Regex("^(?:hey|okay|ok) music\\s*"),"").trim()}
        val original=withoutWake(plain)
        if(Regex("^(?:hey|okay|ok) music$").matches(plain))return plain
        // Explicit song/search requests keep the unrestricted title and artist.
        if(original.startsWith("play ")&&original !in listOf("play music","play the music")||original.startsWith("search ")||original.startsWith("find ")||original.startsWith("listen to "))return plain
        val request=parseVoiceCommand(withoutWake(candidate))
        val wakeOnly=Regex("^(?:hey|okay|ok) music$").matches(candidate)
        if(candidate.contains("[unk]")||request is VoiceCommand.PlaySong||request is VoiceCommand.Search||request==VoiceCommand.Help&&!wakeOnly)return plain
        val words=result.optJSONArray("result")?:return plain
        if(words.length()==0)return plain
        val confidence=(0 until words.length()).map{words.getJSONObject(it).optDouble("conf")}.average()
        return if(confidence>=0.85)candidate else plain
    }
    companion object {
        private fun grammar(): String {
            val commands=listOf("pause","pause music","pause the music","resume","resume music","play","play music","play the music","stop","stop music","next","next song","skip","skip song","previous","previous song","restart song","shuffle","shuffle on","shuffle off","turn on shuffle","turn off shuffle","repeat all","repeat one","repeat this song","repeat off","go home","open home","open library","open search","open queue","open player","open settings","cancel timer","cancel sleep timer")
            val all=commands+listOf("stop listening","turn off voice assistant","turn off wake word","like this song","save this song","add to liked songs","unlike this song","remove from liked songs")
            return JSONArray(listOf("hey music","okay music","[unk]")+all+all.map{"hey music $it"}).toString()
        }
    }
}
