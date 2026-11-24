package com.allen.vybe

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.vosk.Model
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream

class WakeRecognitionTest {
    @Test fun syntheticSpeechRecognizesWakeAndPlaybackCommandOffline() {
        val ins=InstrumentationRegistry.getInstrumentation()
        val ctx=ins.targetContext
        val root=File(ctx.cacheDir,"wake-test-model").apply{mkdirs()}
        ZipInputStream(ctx.assets.open("voice/vosk-model-small-en-us-0.15.zip")).use{zip ->
            while(true){val entry=zip.nextEntry?:break;val file=File(root,entry.name)
                require(file.canonicalPath.startsWith(root.canonicalPath+File.separator))
                if(entry.isDirectory)file.mkdirs() else {file.parentFile?.mkdirs();file.outputStream().use{zip.copyTo(it)}}
            }
        }
        val evidence=org.json.JSONArray()
        Model(File(root,"vosk-model-small-en-us-0.15").absolutePath).use{model ->
            for(fixture in listOf("hey-music-pause.wav","hey-music-song.wav","no-wake.wav")) {
                val wave=ins.context.assets.open("voice/$fixture").use{it.readBytes()}
                val pcm=ByteBuffer.wrap(wave).order(ByteOrder.LITTLE_ENDIAN)
                var offset=12;var audio=byteArrayOf()
                while(offset+8<=wave.size){
                    val size=pcm.getInt(offset+4)
                    if(String(wave,offset,4)=="data"){audio=wave.copyOfRange(offset+8,offset+8+size);break}
                    offset+=8+size+(size%2)
                }
                assertTrue(audio.isNotEmpty())
                val gate=WakeWordGate();val commands=mutableListOf<String>();val transcripts=mutableListOf<String>()
                var clock=10000L
                val decisions=org.json.JSONArray()
                OfflineVoiceDecoder(model){plain,candidate,chosen->decisions.put(JSONObject().put("speech",plain).put("control",candidate).put("chosen",chosen))}.use{recognizer ->
                    val withSilence=audio+ByteArray(64000)
                    for(start in withSilence.indices step 3200){
                        val chunk=withSilence.copyOfRange(start,minOf(start+3200,withSilence.size));clock+=100
                        val shorts=ShortArray(chunk.size/2){ByteBuffer.wrap(chunk,it*2,2).order(ByteOrder.LITTLE_ENDIAN).short}
                        val transcript=recognizer.accept(shorts,shorts.size)
                        if(transcript.final){transcripts+=transcript.text;gate.result(transcript.text,clock)?.let{commands+=it}}
                        else gate.partial(transcript.text,clock)
                    }
                    val text=recognizer.finish().text;transcripts+=text
                    gate.result(text,clock)?.let{commands+=it}
                }
                evidence.put(JSONObject().put("fixture",fixture).put("transcripts",transcripts.joinToString(" | ")).put("commands",commands.joinToString(" | ")).put("decisions",decisions))
                File(ctx.filesDir,"wake-recognition-proof.json").writeText(JSONObject().put("syntheticSpeech",true).put("cases",evidence).toString(2))
                if(fixture=="no-wake.wav")assertTrue("False activation: $transcripts -> $commands",commands.isEmpty())
                else {
                    assertEquals("Transcript: $transcripts",1,commands.size)
                    val command=parseVoiceCommand(commands.single())
                    if(fixture=="hey-music-pause.wav")assertEquals(VoiceCommand.Transport("pause"),command)
                    else {assertTrue("Song lost: $commands",command is VoiceCommand.PlaySong);assertTrue((command as VoiceCommand.PlaySong).query.contains("believer",true))}
                }
            }
        }
    }
}
