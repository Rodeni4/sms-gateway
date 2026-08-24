package ru.myski.vodokanal.smsgateway.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import ru.myski.vodokanal.smsgateway.MainActivity

class SmsGatewayService : Service() {

    private var server: SmsServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (server == null) {
            server = SmsServer(this, 8082)
            try {
                server?.start()
                isRunning = true
            } catch (e: Exception) {
                Log.e("SmsGatewayService", "Failed to start server", e)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SMS Gateway Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Running HTTP server for SMS gateway"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Gateway Running")
            .setContentText("Listening on port 8082")
            .setSmallIcon(ru.myski.vodokanal.smsgateway.R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "sms_gateway_channel"
        private const val NOTIFICATION_ID = 1
        var isRunning = false
            private set
    }
}
