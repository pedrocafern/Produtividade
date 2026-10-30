package br.pessoal.produtividade

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

class PomodoroService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var ticker: Job? = null
    private val prefs by lazy { getSharedPreferences("pomodoro", 0) }

    override fun onCreate() { super.onCreate(); criarCanal() }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE) {
            val remaining = restante()
            prefs.edit().putBoolean("running", false).putLong("remaining", remaining)
                .putLong("endElapsed", SystemClock.elapsedRealtime() + remaining)
                .putLong("endWall", System.currentTimeMillis() + remaining).apply()
            pararServico()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notificacao(restante()))
        ticker?.cancel()
        ticker = scope.launch {
            try {
                while (true) {
                    val left = restante()
                    if (left <= 0) {
                        prefs.edit().putBoolean("running", false).putLong("remaining", 0).apply()
                        val nm = getSystemService(NotificationManager::class.java)
                        nm.notify(NOTIFICATION_ID, notificacao(0, concluido = true))
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf(startId)
                        break
                    }
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notificacao(left))
                    delay(15_000)
                }
            } catch (_: CancellationException) { }
        }
        return START_STICKY
    }

    override fun onDestroy() { ticker?.cancel(); scope.coroutineContext[Job]?.cancel(); super.onDestroy() }

    private fun restante(): Long {
        val sameBoot = prefs.getInt("boot", -1) == runCatching { android.provider.Settings.Global.getInt(contentResolver, android.provider.Settings.Global.BOOT_COUNT) }.getOrDefault(0)
        return PomodoroClock.restante(
            prefs.getLong("endElapsed", 0), prefs.getLong("endWall", 0), prefs.getInt("boot", -1),
            runCatching { android.provider.Settings.Global.getInt(contentResolver, android.provider.Settings.Global.BOOT_COUNT) }.getOrDefault(0),
            SystemClock.elapsedRealtime(), System.currentTimeMillis(), prefs.getLong("remaining", Modo.FOCO.minutos * 60_000L),
        )
    }

    private fun notificacao(ms: Long, concluido: Boolean = false): Notification {
        val totalSeconds = ceil(ms.coerceAtLeast(0) / 1_000.0).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val mode = runCatching { Modo.valueOf(prefs.getString("mode", "FOCO") ?: "FOCO") }.getOrDefault(Modo.FOCO)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle(if (concluido) "Pomodoro concluído" else "${mode.rotulo} em andamento")
            .setContentText(if (concluido) "Sessão finalizada" else "Restam ${"%02d:%02d".format(minutes, seconds)}")
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(!concluido).setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (!concluido) builder.setWhen(prefs.getLong("endWall", System.currentTimeMillis())).setUsesChronometer(true)
        if (!concluido) {
            val pause = PendingIntent.getService(this, 3, Intent(this, PomodoroService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_media_pause, "Pausar", pause)
        }
        return builder.build()
    }

    private fun criarCanal() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Pomodoro", NotificationManager.IMPORTANCE_LOW).apply { description = "Sessão ativa do Pomodoro" },
        )
    }

    private fun pararServico() { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }

    companion object {
        private const val CHANNEL_ID = "pomodoro_session"
        private const val NOTIFICATION_ID = 41
        private const val ACTION_PAUSE = "br.pessoal.produtividade.PAUSE_POMODORO"
        fun iniciar(context: Context) {
            val intent = Intent(context, PomodoroService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
        fun parar(context: Context) { context.stopService(Intent(context, PomodoroService::class.java)) }
    }
}
