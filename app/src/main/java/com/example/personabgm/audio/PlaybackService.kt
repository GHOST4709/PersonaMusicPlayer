package com.example.personabgm.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.personabgm.MainActivity
import com.example.personabgm.R
import com.example.personabgm.context.ContextEngine
import com.example.personabgm.data.local.datastore.SettingsRepository
import com.example.personabgm.data.local.db.ZoneDao
import com.example.personabgm.data.local.db.ZoneStateDao
import com.example.personabgm.data.repository.ManifestRepository
import com.example.personabgm.data.repository.WeatherRepository
import com.example.personabgm.geofence.LocationProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Hosts the context engine, the playback engine, the MediaSession, and (via Media3) the foreground
 * notification. Declared with `foregroundServiceType="mediaPlayback"` in the manifest.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var remote: PlaybackRemote
    @Inject lateinit var mediaCache: MediaCache
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var manifestRepository: ManifestRepository
    @Inject lateinit var zoneDao: ZoneDao
    @Inject lateinit var zoneStateDao: ZoneStateDao
    @Inject lateinit var weatherRepository: WeatherRepository
    @Inject lateinit var locationProvider: LocationProvider

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: CrossfadeEngine? = null
    private var mediaSession: MediaSession? = null
    private var startupWatchdog: Job? = null

    override fun onCreate() {
        super.onCreate()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_stat_persona)
            },
        )

        val contextEngine = ContextEngine(
            scope = serviceScope,
            settingsRepository = settingsRepository,
            manifestRepository = manifestRepository,
            zoneDao = zoneDao,
            zoneStateDao = zoneStateDao,
            weatherRepository = weatherRepository,
            locationProvider = locationProvider,
            timeChanges = systemTimeChanges(),
        )

        val engine = CrossfadeEngine(
            context = this,
            scope = serviceScope,
            mediaCache = mediaCache,
            settingsFlow = settingsRepository.settings,
            manifestFlow = manifestRepository.manifest.distinctUntilChanged(),
            autoContextFlow = contextEngine.autoContext,
            commands = remote.commands,
            publishState = remote::publish,
            // Remember whether the person was listening, so a geofence event can revive playback after the OS kills us.
            onWantsPlaybackChanged = { wants -> serviceScope.launch { settingsRepository.setPlaybackActive(wants) } },
        )
        this.engine = engine

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, CrossfadePlayer(engine.activePlayer, engine))
            .setSessionActivity(openApp)
            .build()

        // After each crossfade the active ExoPlayer changes; rebind the session once (spec 4.1).
        engine.onActivePlayerChanged = { player ->
            mediaSession?.setPlayer(CrossfadePlayer(player, engine))
        }

        contextEngine.start()
        engine.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_FOREGROUND_START, false) == true) promoteToForeground()
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * A service started with `startForegroundService` must call `startForeground` within seconds, or Android
     * kills the app. Media3 only does that once audio is actually playing, which on a cold start can take
     * longer (loading, network). So post a placeholder immediately; it uses Media3's notification id, so the
     * real media notification simply replaces it. If nothing starts playing, leave the foreground again.
     */
    private fun promoteToForeground() {
        if (engine?.isPlayingNow == true) return // already foreground with Media3's own notification

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(STARTING_CHANNEL_ID, "Starting music", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = NotificationCompat.Builder(this, STARTING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_persona)
            .setContentTitle("Persona BGM")
            .setContentText("Getting your music ready")
            .setOngoing(true)
            .build()
        try {
            ServiceCompat.startForeground(
                this,
                DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not enter the foreground", e)
            stopSelf()
            return
        }

        startupWatchdog?.cancel()
        startupWatchdog = serviceScope.launch {
            delay(STARTUP_GRACE_MS)
            if (engine?.isPlayingNow != true) {
                ServiceCompat.stopForeground(this@PlaybackService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /** Emits whenever the system clock, date, or time zone changes, so Day/Night is re-evaluated (spec 5.5). */
    private fun systemTimeChanges(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        ContextCompat.registerReceiver(this@PlaybackService, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { unregisterReceiver(receiver) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Swiping the app away must not stop music that is playing (this is an ambient background app).
     * If nothing is playing there is no reason to keep the service around.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (engine?.isPlayingNow != true) stopSelf()
    }

    override fun onDestroy() {
        startupWatchdog?.cancel()
        mediaSession?.release()
        mediaSession = null
        engine?.release()
        engine = null
        serviceScope.cancel()
        remote.onServiceStopped()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val STARTING_CHANNEL_ID = "persona_starting"
        private const val STARTUP_GRACE_MS = 15_000L

        /** Set on the start intent by [PlaybackRemote.sendFromBackground]. */
        const val EXTRA_FOREGROUND_START = "com.example.personabgm.extra.FOREGROUND_START"
    }
}
