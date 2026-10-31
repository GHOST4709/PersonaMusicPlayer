package com.example.personabgm.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.personabgm.MainActivity
import com.example.personabgm.R
import com.example.personabgm.data.local.datastore.SettingsRepository
import com.example.personabgm.data.repository.ManifestRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Hosts the playback engine, the MediaSession, and (via Media3) the foreground notification.
 * Declared with `foregroundServiceType="mediaPlayback"` in the manifest.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var remote: PlaybackRemote
    @Inject lateinit var mediaCache: MediaCache
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var manifestRepository: ManifestRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: CrossfadeEngine? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_stat_persona)
            },
        )

        val engine = CrossfadeEngine(
            context = this,
            scope = serviceScope,
            mediaCache = mediaCache,
            settingsFlow = settingsRepository.settings,
            manifestFlow = manifestRepository.manifest.distinctUntilChanged(),
            commands = remote.commands,
            publishState = remote::publish,
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

        engine.start()
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
        mediaSession?.release()
        mediaSession = null
        engine?.release()
        engine = null
        serviceScope.cancel()
        remote.onServiceStopped()
        super.onDestroy()
    }
}
