package com.sanches.music

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer

class MusicService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var bassBoost: BassBoost? = null
    private var equalizer: Equalizer? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(), true
        )
        player.setHandleAudioBecomingNoisy(true)
        mediaSession = MediaSession.Builder(this, player).build()
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                releaseEffects()
                if (audioSessionId > 0) {
                    runCatching {
                        bassBoost = BassBoost(0, audioSessionId).apply { enabled = true }
                        equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
                    }
                }
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.sanches.music.EQ_PRESET") applyPreset(intent.getStringExtra("preset") ?: "Normal")
        return super.onStartCommand(intent, flags, startId)
    }

    private fun applyPreset(name: String) {
        val eq = equalizer ?: return
        val bass = bassBoost ?: return
        runCatching {
            bass.setStrength(
                when (name) {
                    "Rock", "Metal" -> 800
                    "Bass Boost" -> 1000
                    "Vocal" -> 250
                    else -> 500
                }.toShort()
            )
            val bands = eq.numberOfBands
            for (i in 0 until bands) {
                val level = when (name) {
                    "Rock" -> if (i < bands / 2) 500 else 250
                    "Metal" -> if (i < bands / 2) 700 else 350
                    "Bass Boost" -> if (i < bands / 2) 1000 else 0
                    "Vocal" -> if (i > bands / 2) 500 else -100
                    else -> 0
                }
                val range = eq.bandLevelRange
                eq.setBandLevel(i.toShort(), level.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
        }
    }

    private fun releaseEffects() {
        runCatching { bassBoost?.release() }
        runCatching { equalizer?.release() }
        bassBoost = null
        equalizer = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        releaseEffects()
        mediaSession?.release()
        player.release()
        super.onDestroy()
    }
}
