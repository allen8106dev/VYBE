package com.allen.vybe

import android.content.*
import android.os.*
import android.webkit.WebView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.allen.vybe.browser.*
import com.allen.vybe.domain.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.*

/** Transition-focused live YouTube proof. HTML event telemetry is not an audible guarantee. */
class BrowserAudioContinuityTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun recordMediaEventsAcrossNativeAndAndroidHome() {
        val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext
        fun shell(cmd:String)=ins.uiAutomation.executeShellCommand(cmd).use{p->java.io.FileInputStream(p.fileDescriptor).use{String(it.readBytes())}}
        val label=InstrumentationRegistry.getArguments().getString("label","current").replace(Regex("[^a-zA-Z0-9_-]"),"").take(32)
        val videoId=InstrumentationRegistry.getArguments().getString("videoId","aqz-KE-bpKQ").also{require(it.matches(Regex("[A-Za-z0-9_-]{11}")))}
        val title=if(videoId=="7wtfhZwyrcc") "Imagine Dragons - Believer" else "Audio continuity test"
        val output=java.io.File(context.filesDir,"audio-continuity-$label.json");val result=JSONObject();val samples=JSONArray()
        result.put("samples",samples)
        fun save(){output.writeText(result.toString(2))}
        shell("am start -W -f 0x10008000 -n com.allen.vybe/.MainActivity")
        var engine:BrowserPlaybackService?=null;val bound=CountDownLatch(1)
        val connection=object:ServiceConnection{
            override fun onServiceConnected(n:ComponentName,b:IBinder){engine=(b as BrowserPlaybackService.LocalBinder).service;bound.countDown()}
            override fun onServiceDisconnected(n:ComponentName){engine=null}
        }
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use{scenario->
            scenario.onActivity{a->MainActivity::class.java.getDeclaredMethod("playBrowser",List::class.java,Int::class.javaPrimitiveType).apply{isAccessible=true}.invoke(a,listOf(Track(videoId,title,if(videoId=="7wtfhZwyrcc") "Imagine Dragons" else "Blender Foundation",Source.YOUTUBE)),0)}
            context.bindService(Intent(context,BrowserPlaybackService::class.java),connection,Context.BIND_AUTO_CREATE)
            assertTrue(bound.await(10,TimeUnit.SECONDS));lateinit var web:WebView
            ins.runOnMainSync{web=BrowserPlaybackService::class.java.getDeclaredField("web").apply{isAccessible=true}.get(engine) as WebView}
            fun js(code:String):String{val done=CountDownLatch(1);var value="null";ins.runOnMainSync{web.evaluateJavascript(code){value=it;done.countDown()}};assertTrue("JS observation timeout",done.await(8,TimeUnit.SECONDS));return value}
            fun sample(name:String):JSONObject{
                val raw=JSONTokener(js("JSON.stringify((()=>{const m=(document.querySelector('video.html5-main-video')||document.querySelector('video,audio'));return m?{paused:m.paused,ended:m.ended,time:m.currentTime,duration:m.duration,ready:m.readyState,muted:m.muted,volume:m.volume,health:window.__vybeHealth?.read(m),elements:[...document.querySelectorAll('video,audio')].map(v=>({main:v.matches('.html5-main-video'),paused:v.paused,muted:v.muted,time:v.currentTime})),lastEnd:window.__vybeEndProbe||null,sourceMatches:new URL(location.href).searchParams.get('v')===${org.json.JSONObject.quote(videoId)},playerApi:(()=>{const p=document.getElementById('movie_player');return p?{unMute:typeof p.unMute,muted:p.isMuted?.(),volume:p.getVolume?.(),quality:p.getPlaybackQuality?.()}:null})()}:{missing:true}})())")).nextValue()
                val data=JSONObject(raw as? String?:"{}").put("phase",name).put("at",SystemClock.elapsedRealtime())
                    .put("interactive",context.getSystemService(PowerManager::class.java).isInteractive)
                    .put("keyguardLocked",context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked)
                samples.put(data);save();return data
            }
            fun awaitUnlocked(){
                val deadline=SystemClock.elapsedRealtime()+180000
                var wasLocked=false
                while(!context.getSystemService(PowerManager::class.java).isInteractive||context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked){
                    assertTrue("Unlock phone to continue native navigation checks",SystemClock.elapsedRealtime()<deadline)
                    sample("manualLockBeforeNavigation");wasLocked=true;Thread.sleep(5000)
                }
                if(wasLocked){sample("manualUnlock");Thread.sleep(1500)}
            }
            fun click(text:String){awaitUnlocked();ui.onNode(hasText(text) and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick){it()}}
            try{
                ui.waitUntil(30000){BrowserPlaybackService.latest.duration>0}
                js(BrowserMediaHealth.SCRIPT);ins.runOnMainSync{engine!!.command("play")}
                ui.waitUntil(15000){BrowserPlaybackService.latest.playing};Thread.sleep(12000)
                js("window.__vybeEndProbe=null;document.addEventListener('ended',e=>{const m=e.target;if(m instanceof HTMLMediaElement)window.__vybeEndProbe={time:m.currentTime,duration:m.duration,at:performance.now()}},true)")
                val start=sample("steadyStart");result.put("baseline",start)
                awaitUnlocked();ui.onNode(hasContentDescription("Mini-player song artwork") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick){it()};Thread.sleep(4000);sample("player")
                awaitUnlocked();ui.onNodeWithContentDescription("Back to library").performSemanticsAction(SemanticsActions.OnClick){it()};Thread.sleep(4000);sample("nativeHome")
                click("Search");Thread.sleep(4000);sample("search")
                click("Your library");Thread.sleep(4000);sample("library")
                val beforeHome=sample("beforeAndroidHome")
                result.put("homeLaunch",shell("am start -W -a android.intent.action.MAIN -c android.intent.category.HOME"))
                Thread.sleep(10000);val afterHome=sample("androidHome")
                result.put("androidHomeAdvanced",afterHome.optDouble("time")>beforeHome.optDouble("time")+5&&!afterHome.optBoolean("paused",true))
                result.put("foreground",shell("dumpsys activity activities").lineSequence().firstOrNull{it.contains("topResumedActivity=")}.orEmpty())
                Thread.sleep(10000);sample("backgroundSteady")
                if(InstrumentationRegistry.getArguments().getString("lock")=="true"){
                    result.put("sleepCommand",shell("input keyevent 223"))
                    Thread.sleep(2000)
                    val screenOff=!context.getSystemService(PowerManager::class.java).isInteractive
                    result.put("screenOffVerified",screenOff);save()
                    assertTrue("Display did not turn off; lock test was not exercised",screenOff)
                    val beforeLock=sample("screenOffStart")
                    Thread.sleep(30000);val afterLock=sample("screenOff30Seconds")
                    result.put("screenOffAdvanced",afterLock.optDouble("time")>beforeLock.optDouble("time")+25&&!afterLock.optBoolean("paused",true))
                    shell("input keyevent 224");Thread.sleep(2000);sample("screenAwake")
                    assertTrue("Playback stopped with display off",result.optBoolean("screenOffAdvanced"))
                }
                result.put("audioFocus",shell("dumpsys audio").lineSequence().filter{it.contains("com.allen.vybe")||it.contains("AudioFocusDelegate")}.take(50).joinToString("\n"))
                val audioDump=shell("dumpsys audio")
                result.put("focusStack",audioDump.substringAfter("Audio Focus stack entries").substringBefore("No external focus policy"))
                var completed=false
                if(InstrumentationRegistry.getArguments().getString("fullSong")=="true"){
                    val targetDuration=start.optDouble("duration")
                    val deadline=SystemClock.elapsedRealtime()+300000
                    while(SystemClock.elapsedRealtime()<deadline&&!completed){
                        Thread.sleep(5000);val check=sample("wholeSong")
                        val ended=check.optJSONObject("lastEnd")
                        completed=targetDuration>150&&ended!=null&&ended.optDouble("time")>=targetDuration-1&&kotlin.math.abs(ended.optDouble("duration")-targetDuration)<1
                    }
                    result.put("wholeSongReachedEnd",completed);assertTrue("Song did not reach its natural end",completed)
                }
                val end=sample("end");val a=start.optJSONObject("health");val b=end.optJSONObject("health")
                result.put("pauseEvents",(b?.optInt("pauses")?:0)-(a?.optInt("pauses")?:0)).put("waitingEvents",(b?.optInt("waiting")?:0)-(a?.optInt("waiting")?:0)).put("waitingMs",(b?.optLong("waitMs")?:0)-(a?.optLong("waitMs")?:0))
                result.put("steadyAdvanced",completed||(end.optDouble("time")>start.optDouble("time")+25&&!end.optBoolean("paused",true)));save()
                if(InstrumentationRegistry.getArguments().getString("strict")=="true"){
                    assertTrue("Playback did not advance across transitions",result.optBoolean("steadyAdvanced"))
                    assertTrue("Unexpected pause events: ${result.optInt("pauseEvents")}",result.optInt("pauseEvents")<=if(completed)1 else 0)
                    assertFalse("Bridge still acquires duplicate audio focus",result.optString("focusStack").contains("BrowserPlaybackService"))
                    assertFalse("Real media is muted",end.optBoolean("muted",true))
                    assertTrue("Buffering during transitions: ${result.optLong("waitingMs")} ms",result.optLong("waitingMs")==0L)
                }
            }finally{save();shell("input keyevent 224");shell("am start -W -n com.allen.vybe/.MainActivity");ins.runOnMainSync{engine?.command("stop")};context.unbindService(connection)}
        }
    }
}
