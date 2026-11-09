package sh.lsx.untisx.data.automation

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Rings like a real alarm clock: a foreground service plays the alarm sound on the alarm stream
 * (also in silent/vibrate mode), vibrates and keeps the full-screen notification up. A plain
 * notification sound was not reliable for waking up.
 */
class WakeAlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focus: AudioFocusRequest? = null
    private var volume = 0.25f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        val label = intent?.getStringExtra(WakeAlarm.EXTRA_LABEL).orEmpty()
        val notification = WakeAlarm.ringingNotification(this, label)
        ServiceCompat.startForeground(
            this,
            WakeAlarm.NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0,
        )
        ringing = true

        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YUntis:alarm")
                ?.apply { acquire(RING_MILLIS + 5_000L) }
        }
        handler.removeCallbacksAndMessages(null)
        startSound()
        startVibration()
        // stops by itself after a while, like other alarm clocks
        handler.postDelayed({ WakeAlarm.stopRinging(this) }, RING_MILLIS)
        return START_NOT_STICKY
    }

    private fun alarmUri(): Uri? =
        RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    private fun startSound() {
        if (player != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val audio = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).build()
                .also { audio?.requestAudioFocus(it) }
        }

        val candidates = listOfNotNull(
            alarmUri(),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        ).distinct()
        for (uri in candidates) {
            val started = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setDataSource(this@WakeAlarmService, uri)
                    isLooping = true
                    setVolume(volume, volume)
                    prepare()
                    start()
                }
            }.getOrNull()
            if (started != null) {
                player = started
                break
            }
        }
        // gets louder over half a minute
        val ramp = object : Runnable {
            override fun run() {
                val p = player ?: return
                volume = (volume + 0.075f).coerceAtMost(1f)
                runCatching { p.setVolume(volume, volume) }
                if (volume < 1f) handler.postDelayed(this, 3_000L)
            }
        }
        handler.postDelayed(ramp, 3_000L)
    }

    private fun startVibration() {
        if (vibrator != null) return
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!v.hasVibrator()) return
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        @Suppress("DEPRECATION")
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0), attributes)
        vibrator = v
    }

    private fun stopEverything() {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focus?.let { getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it) }
        }
        focus = null
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        ringing = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (ringing || player != null) {
            handler.removeCallbacksAndMessages(null)
            runCatching { player?.release() }
            runCatching { vibrator?.cancel() }
            runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
            ringing = false
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "de.theunknown.yuntis.alarm.STOP"
        private const val RING_MILLIS = 10 * 60_000L

        /** Whether the alarm is ringing right now (the alarm screen closes when it stops). */
        @Volatile
        var ringing = false
            private set

        fun start(context: Context, label: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WakeAlarmService::class.java).putExtra(WakeAlarm.EXTRA_LABEL, label),
            )
        }

        fun stop(context: Context) {
            if (!ringing) return
            runCatching { context.startService(Intent(context, WakeAlarmService::class.java).setAction(ACTION_STOP)) }
                .onFailure { context.stopService(Intent(context, WakeAlarmService::class.java)) }
        }
    }
}
