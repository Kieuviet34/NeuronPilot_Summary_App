package com.bhs.meetingnotes.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.bhs.meetingnotes.MeetingRecordActivity
import com.mediatek.neuropilot.jnidemo.R

/**
 * Foreground Service với type "microphone" theo yêu cầu Android 14/15 và Ràng buộc C5.
 * Giữ thread ghi âm ở mức ưu tiên cao nhất, ngăn hệ điều hành kill AudioRecord khi tắt màn hình.
 */
class AudioRecordingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "channel_meeting_recording"
        const val NOTIFICATION_ID = 2026
        const val ACTION_START = "com.bhs.meetingnotes.action.START_RECORDING"
        const val ACTION_PAUSE = "com.bhs.meetingnotes.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "com.bhs.meetingnotes.action.RESUME_RECORDING"
        const val ACTION_STOP = "com.bhs.meetingnotes.action.STOP_RECORDING"
        const val EXTRA_MEETING_TITLE = "extra_meeting_title"

        fun startService(context: Context, meetingTitle: String) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MEETING_TITLE, meetingTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateState(context: Context, isPaused: Boolean) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = if (isPaused) ACTION_PAUSE else ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AudioRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_MEETING_TITLE) ?: "Cuộc họp"
                startForegroundNotification("Đang ghi âm: $title", "Chất lượng 16kHz Mono • Genio 720 I2S")
            }
            ACTION_PAUSE -> {
                updateNotification("Cuộc họp đang tạm dừng", "Nhấn tiếp tục trong ứng dụng")
            }
            ACTION_RESUME -> {
                updateNotification("Đang tiếp tục ghi âm", "Chất lượng 16kHz Mono • Genio 720 I2S")
            }
            ACTION_STOP -> {
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MeetingNotes::AudioRecorderWakeLock")
            wakeLock?.acquire(3 * 60 * 60 * 1000L) // Max 3 giờ theo Ràng buộc họp 2 giờ + buffer
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ghi âm cuộc họp",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Thông báo trạng thái ghi âm Foreground Service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MeetingRecordActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.bg_circle_red)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundNotification(title: String, content: String) {
        val notification = buildNotification(title, content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(title, content))
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
