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
    private var pendingBass = 500
    private var pendingBands = floatArrayOf(0f, 0f, 0f, 0f, 0f)

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
                        applyBands(pendingBass, pendingBands)
                    }
                }
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "com.sanches.music.EQ_PRESET" -> applyPreset(intent.getStringExtra("preset") ?: "Normal")
            "com.sanches.music.EQ_BANDS" -> {
                val bass = intent.getIntExtra("bass", 500)
                val bands = intent.getFloatArrayExtra("bands") ?: floatArrayOf()
                applyBands(bass, bands)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun applyBands(bass: Int, values: FloatArray) {
        pendingBass = bass.coerceIn(0, 1000)
        pendingBands = if (values.size == 5) values.copyOf() else floatArrayOf(0f, 0f, 0f, 0f, 0f)

        val eq = equalizer ?: return
        val boost = bassBoost ?: return
        runCatching {
            boost.enabled = true
            eq.enabled = true
            boost.setStrength(pendingBass.toShort())

            val range = eq.bandLevelRange
            val count = eq.numberOfBands.toInt().coerceAtLeast(1)
            val minFreq = 60.0
            val maxFreq = 14000.0

            // Distribui os 5 controles do app pela frequência real do equalizador
            // do aparelho, em escala logarítmica. Assim cada controle atua na região correta.
            for (i in 0 until count) {
                val centerHz = runCatching {
                    val fr = eq.getCenterFreq(i.toShort()).toDouble() / 1000.0
                    fr.coerceIn(minFreq, maxFreq)
                }.getOrDefault(minFreq)

                val pos = kotlin.math.ln(centerHz / minFreq) / kotlin.math.ln(maxFreq / minFreq)
                val scaled = (pos * (pendingBands.lastIndex)).coerceIn(0.0, pendingBands.lastIndex.toDouble())
                val left = kotlin.math.floor(scaled).toInt()
                val right = kotlin.math.ceil(scaled).toInt().coerceAtMost(pendingBands.lastIndex)
                val fraction = scaled - left
                val value = pendingBands[left] * (1.0 - fraction) + pendingBands[right] * fraction
                val millibels = (value * 100.0).toInt()
                    .coerceIn(range[0].toInt(), range[1].toInt())

                eq.setBandLevel(i.toShort(), millibels.toShort())
            }
        }
    }

    private fun applyPreset(name: String) {
        val presetBass = when (name) {
            "Rock", "Metal" -> 800
            "Bass Boost" -> 1000
            "Vocal" -> 250
            else -> 500
        }
        val presetBands = when (name) {
            "Rock" -> floatArrayOf(5f, 3f, 1f, 3f, 5f)
            "Metal" -> floatArrayOf(7f, 4f, 0f, 4f, 6f)
            "Bass Boost" -> floatArrayOf(10f, 6f, 2f, 0f, -2f)
            "Vocal" -> floatArrayOf(-3f, 0f, 4f, 7f, 4f)
            else -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
        }
        pendingBass = presetBass
        pendingBands = presetBands
        val eq = equalizer ?: return
        val bass = bassBoost ?: return
        runCatching {
            applyBands(pendingBass, pendingBands)
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
