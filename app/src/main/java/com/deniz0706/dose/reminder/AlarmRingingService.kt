package com.deniz0706.dose.reminder

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import com.deniz0706.dose.model.MedicationReminder

class AlarmRingingService : Service() {
    private var ringtone: Ringtone? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "Dose:AlarmWakeLock"
        ).apply { acquire(5 * 60_000L) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reminder = ReminderScheduler.decode(intent?.getStringExtra(ReminderScheduler.EXTRA_REMINDER)) ?: return START_NOT_STICKY
        val timeId = intent.getLongExtra(ReminderScheduler.EXTRA_TIME_ID, reminder.effectiveTimes().first().id)
        val date = intent.getStringExtra(ReminderScheduler.EXTRA_SCHEDULED_DATE)
        val notification = NotificationHelper.build(this, reminder, timeId, date)
        startForeground(NotificationHelper.notificationId(reminder,timeId), notification)
        if (ringtone == null) {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                    isLooping = true
                }
                play()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { ringtone?.stop(); wakeLock?.let { if(it.isHeld) it.release() }; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun stop(context: android.content.Context) = context.stopService(Intent(context, AlarmRingingService::class.java))
    }
}