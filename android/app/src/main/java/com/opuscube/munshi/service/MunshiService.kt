package com.opuscube.munshi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

// Keeps the process (and so the event stream and announcements in MunshiApp) alive
// while the app is not in front. The work itself lives in the Application graph.
class MunshiService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("counter", "Munshi dukaan par", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "counter")
            .setContentTitle("Munshi dukaan par hai")
            .setContentText("Payment aate hi bataunga.")
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(1, n)
        return START_STICKY
    }

    companion object {
        fun start(context: Context) { context.startForegroundService(Intent(context, MunshiService::class.java)) }
    }
}
