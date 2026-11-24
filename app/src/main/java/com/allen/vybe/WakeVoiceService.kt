package com.allen.vybe

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vosk.Model
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

data class WakeVoiceState(val enabled: Boolean=false,val status: String="Off",val heard: String="")

/** Explicitly enabled, local-only recognition. No audio files or remote audio requests. */
class WakeVoiceService: Service() {
    companion object {
        private val mutableState=MutableStateFlow(WakeVoiceState())
        val state=mutableState.asStateFlow()
        const val STOP="com.allen.vybe.voice.STOP"
        const val ARM="com.allen.vybe.voice.ARM"
        private const val CHANNEL="vybe_voice"
        private const val NOTIFICATION=904
    }
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var running=false
    @Volatile private var recorder: AudioRecord?=null
    private var started=false
    private val armRequested=java.util.concurrent.atomic.AtomicBoolean(false)
    override fun onBind(intent: Intent?)=null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action==STOP){stopSelf();return START_NOT_STICKY}
        if(intent?.action==ARM&&started){armRequested.set(true);return START_NOT_STICKY}
        if(started)return START_NOT_STICKY
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY}
        started=true;running=true
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Hey Music microphone",NotificationManager.IMPORTANCE_LOW))
        if(Build.VERSION.SDK_INT>=30)startForeground(NOTIFICATION,notification("Preparing offline voice…"),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIFICATION,notification("Preparing offline voice…"))
        update("Preparing offline voice…")
        worker.execute { listen() }
        return START_NOT_STICKY
    }
    private fun notification(status: String): Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,WakeVoiceService::class.java).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Hey Music · microphone on").setContentText(status).setContentIntent(open)
            .setOngoing(true).setSilent(true).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Stop listening",stop).build()
    }
    private fun update(status: String,heard: String=mutableState.value.heard) {
        main.post {
            if(running){mutableState.value=WakeVoiceState(true,status,heard);getSystemService(NotificationManager::class.java).notify(NOTIFICATION,notification(status))}
        }
    }
    private fun modelDirectory(): File {
        val root=File(filesDir,"voice-model-v1")
        val model=File(root,"vosk-model-small-en-us-0.15")
        val ready=File(root,"ready")
        if(ready.exists())return model
        root.mkdirs()
        ZipInputStream(assets.open("voice/vosk-model-small-en-us-0.15.zip")).use { zip ->
            while(running){
                val entry=zip.nextEntry?:break
                val file=File(root,entry.name)
                require(file.canonicalPath.startsWith(root.canonicalPath+File.separator))
                if(entry.isDirectory)file.mkdirs() else {file.parentFile?.mkdirs();file.outputStream().use { zip.copyTo(it) }}
                zip.closeEntry()
            }
        }
        check(running){"Listening stopped"}
        ready.writeText("1")
        return model
    }
    @SuppressLint("MissingPermission", "WakelockTimeout")
    private fun listen() {
        val lock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"VYBE:voice")
        lock.acquire()
        var model: Model?=null
        var decoder: OfflineVoiceDecoder?=null
        var audio: AudioRecord?=null
        try {
            model=Model(modelDirectory().absolutePath)
            if(!running)return
            val recognizer=OfflineVoiceDecoder(model)
            decoder=recognizer
            val size=maxOf(AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT),8192)
            val recording=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size)
            audio=recording;recorder=recording
            check(recording.state==AudioRecord.STATE_INITIALIZED){"Microphone could not start"}
            if(!running)return
            recording.startRecording()
            check(recording.recordingState==AudioRecord.RECORDSTATE_RECORDING){"Microphone unavailable"}
            val gate=WakeWordGate()
            val buffer=ShortArray(1600)
            var armed=false
            update("Say Hey Music, then a command")
            while(running){
                val count=recording.read(buffer,0,buffer.size)
                if(count<=0){if(running)error("Microphone interrupted");break}
                val now=SystemClock.elapsedRealtime()
                if(armRequested.getAndSet(false)){recognizer.reset();gate.arm(now)}
                val transcript=recognizer.accept(buffer,count)
                val command=if(transcript.final)gate.result(transcript.text,now) else {gate.partial(transcript.text,now);null}
                gate.expire(now)
                if(gate.listening!=armed){
                    armed=gate.listening
                    if(armed)getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createOneShot(80,VibrationEffect.DEFAULT_AMPLITUDE))
                    update(if(armed)"Listening for your command..." else "Say Hey Music, then a command")
                }
                if(command!=null){
                    update("Say Hey Music, then a command",command)
                    main.post{if(running)(application as MuseApplication).music.handleVoice(command)}
                    recognizer.reset()
                }
            }
        } catch(error: Exception) {
            if(running)main.post{mutableState.value=WakeVoiceState(status=error.message?:"Voice listener stopped");stopSelf()}
        } catch(error: LinkageError) {
            if(running)main.post{mutableState.value=WakeVoiceState(status="Offline voice is unavailable on this device");stopSelf()}
        } finally {
            recorder=null
            runCatching{audio?.stop()};runCatching{audio?.release()}
            runCatching{decoder?.close()};runCatching{model?.close()}
            lock.release()
            main.post{stopSelf()}
        }
    }

    override fun onDestroy() {
        running=false
        runCatching {recorder?.stop()}
        worker.shutdown()
        if(mutableState.value.enabled)mutableState.value=WakeVoiceState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
