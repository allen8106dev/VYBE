package com.allen.vybe.browser

import android.app.Activity
import android.content.*
import android.os.*
import android.view.ViewGroup
import android.widget.*

/** Deliberately plain laboratory surface; existing Compose screens remain untouched. */
class BrowserPlaybackActivity : Activity() {
    private var service: BrowserPlaybackService? = null
    private lateinit var container: FrameLayout
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { status.text = service?.diagnostics ?: "Connecting browser?"; handler.postDelayed(this, 1000) }
    }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as BrowserPlaybackService.LocalBinder).service
            service!!.attach(container, this@BrowserPlaybackActivity)
            if (savedQueue != null) { service!!.load(savedQueue!!, savedIndex); savedQueue = null }
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null }
    }
    private var savedQueue: ArrayList<String>? = null
    private var savedIndex = 0
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 12, 12, 12) }
        status = TextView(this).apply { textSize = 13f }
        root.addView(TextView(this).apply { text = "VYBE ? Browser playback lab"; textSize = 20f })
        root.addView(status)
        val controls = LinearLayout(this)
        listOf("Previous" to "previous", "Play" to "play", "Pause" to "pause", "Next" to "next", "Stop" to "stop").forEach { (label, command) ->
            controls.addView(Button(this).apply { text = label; textSize = 11f; setOnClickListener { service?.command(command); if(command == "stop") finish() } }, LinearLayout.LayoutParams(0, 52.dp(), 1f))
        }
        root.addView(controls)
        container = FrameLayout(this); root.addView(container, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)
        if (state == null) { savedQueue = intent.getStringArrayListExtra("queue"); savedIndex = intent.getIntExtra("index", 0) }
        val target = Intent(this, BrowserPlaybackService::class.java)
        startForegroundService(target); bindService(target, connection, BIND_AUTO_CREATE)
        handler.post(refresh)
    }
    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        service?.detach(container); unbindService(connection); service = null
        super.onDestroy()
    }
}
