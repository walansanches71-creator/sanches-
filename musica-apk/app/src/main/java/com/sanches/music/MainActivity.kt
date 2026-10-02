package com.sanches.music

import android.Manifest
import android.app.DownloadManager
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import java.io.ByteArrayOutputStream
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.content.res.Configuration
import android.provider.MediaStore
import android.widget.Toast
import android.media.MediaMetadataRetriever
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.util.LruCache
import android.util.Rational
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val uri: Uri,
    val art: Bitmap?
)

data class Playlist(
    val id: Long,
    val name: String,
    val coverUri: String?,
    val songIds: Set<Long>
)

private val Ink = Color(0xFF050505)
private val Panel = Color(0xFF111111)
private val Panel2 = Color(0xFF191919)
private val Red = Color(0xFFE50914)
private val RedBright = Color(0xFFFF3340)
private val TextSoft = Color(0xFF8F8F8F)
private val artworkCache = LruCache<Long, Bitmap>(48)

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var songs by mutableStateOf<List<Song>>(emptyList())
    private var current by mutableStateOf<Song?>(null)
    private var isPlaying by mutableStateOf(false)
    private var shuffleEnabled by mutableStateOf(false)
    private var repeatMode by mutableIntStateOf(androidx.media3.common.Player.REPEAT_MODE_OFF)
    private var lastBackPressAt = 0L
    private var notificationArtworkBytes: ByteArray? = null
    private var position by mutableLongStateOf(0L)
    private var duration by mutableLongStateOf(1L)
    private var showPlayer by mutableStateOf(false)
    private var query by mutableStateOf("")
    private var selectedIds by mutableStateOf<Set<Long>>(emptySet())
    private var showSettings by mutableStateOf(false)
    private var favorites by mutableStateOf<Set<Long>>(emptySet())
    private var playlists by mutableStateOf<List<Playlist>>(emptyList())
    private var showCreatePlaylist by mutableStateOf(false)
    private var showPlaylistPicker by mutableStateOf(false)
    private var showYoutube by mutableStateOf(false)
    private var youtubePip by mutableStateOf(false)
    private var youtubeReturnSongId by mutableStateOf<Long?>(null)
    private var youtubeReturnPosition by mutableLongStateOf(0L)
    private var youtubeReturnWasPlaying by mutableStateOf(false)
    private var editingPlaylistId by mutableStateOf<Long?>(null)
    private var pendingPlaylistCover by mutableStateOf<Uri?>(null)
    private var songForPlaylist by mutableStateOf<Long?>(null)
    private var historyIds by mutableStateOf<List<Long>>(emptyList())
    private var playCounts by mutableStateOf<Map<Long, Int>>(emptyMap())
    private var showQueue by mutableStateOf(false)
    private var showSleepTimer by mutableStateOf(false)
    private var showEqualizer by mutableStateOf(false)
    private var showBackup by mutableStateOf(false)
    private var showDuplicates by mutableStateOf(false)
    private var showEditorSongId by mutableStateOf<Long?>(null)
    private var sleepUntil by mutableLongStateOf(0L)
    private val sleepHandler = Handler(Looper.getMainLooper())

    private val prefs by lazy { getSharedPreferences("sanches_music", MODE_PRIVATE) }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadSongs()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            selectedIds = emptySet()
            loadSongs()
        }
    }

    private val coverPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pendingPlaylistCover = uri
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSavedData()
        setContent {
            SanchesTheme {
                if (showPlayer && current != null) {
                    FullPlayer(
                        current!!, isPlaying, position, duration,
                        { showPlayer = false },
                        { toggle() },
                        { controller?.seekTo(it) },
                        { controller?.seekToNextMediaItem() },
                        { controller?.seekToPreviousMediaItem() },
                        { controller?.shuffleModeEnabled = !(controller?.shuffleModeEnabled ?: false) },
                        {
                            val next = when (controller?.repeatMode) {
                                androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ONE
                                androidx.media3.common.Player.REPEAT_MODE_ONE -> androidx.media3.common.Player.REPEAT_MODE_ALL
                                else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                            }
                            controller?.repeatMode = next
                        },
                        shuffleEnabled,
                        repeatMode,
                        { toggleFavorite(current!!.id) }
                    )
                } else {
                    Home(
                        songs = songs,
                        current = current,
                        playing = isPlaying,
                        query = query,
                        selectedIds = selectedIds,
                        favorites = favorites,
                        playlists = playlists,
                        historyIds = historyIds,
                        playCounts = playCounts,
                        showSettings = showSettings,
                        onQuery = { query = it },
                        onSong = { playSong(it) },
                        onMiniOpen = { showPlayer = true },
                        onToggle = { toggle() },
                        onSelect = { id -> selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id },
                        onDelete = { deleteSelected() },
                        onClearSelection = { selectedIds = emptySet() },
                        onSettings = { showSettings = it; if (!it) loadSongs() },
                        onToggleFavorite = { toggleFavorite(it) },
                        onOpenPlaylistPicker = { songForPlaylist = it; showPlaylistPicker = true },
                        onRemoveFromPlaylist = { playlistId, songId -> removeSongFromPlaylist(playlistId, songId) },
                        onDeleteSong = { deleteSong(it) },
                        onCreatePlaylist = { showCreatePlaylist = true; pendingPlaylistCover = null },
                        onEditPlaylist = { id -> editingPlaylistId = id; pendingPlaylistCover = null },
                        onDeletePlaylist = { deletePlaylist(it) },
                        onYoutube = { openYoutube(this) },
                        onRefresh = { loadSongs() },
                        onPickCover = { coverPickerLauncher.launch("image/*") },
                        onQueue = { showQueue = true },
                        onSleep = { showSleepTimer = true },
                        onEqualizer = { showEqualizer = true },
                        onBackup = { showBackup = true },
                        onDuplicates = { showDuplicates = true },
                        onEditSong = { showEditorSongId = it }
                    )
                }

                if (showCreatePlaylist) {
                    CreatePlaylistDialog(
                        songs = songs,
                        initialSongId = songForPlaylist,
                        coverUri = pendingPlaylistCover,
                        onPickCover = { coverPickerLauncher.launch("image/*") },
                        onDismiss = {
                            showCreatePlaylist = false
                            pendingPlaylistCover = null
                            songForPlaylist = null
                        },
                        onCreate = { name, ids -> createPlaylist(name, ids) }
                    )
                }

                if (editingPlaylistId != null) {
                    val playlist = playlists.firstOrNull { it.id == editingPlaylistId }
                    if (playlist != null) {
                        EditPlaylistDialog(
                            playlist = playlist,
                            coverUri = pendingPlaylistCover,
                            onPickCover = { coverPickerLauncher.launch("image/*") },
                            onDismiss = { editingPlaylistId = null; pendingPlaylistCover = null },
                            onSave = { name -> savePlaylistEdit(playlist.id, name) },
                            onDelete = { deletePlaylist(playlist.id) }
                        )
                    }
                }

                if (showPlaylistPicker) {
                    PlaylistPickerDialog(
                        playlists = playlists,
                        onDismiss = { showPlaylistPicker = false; songForPlaylist = null },
                        onChoose = { playlistId ->
                            songForPlaylist?.let { addSongToPlaylist(playlistId, it) }
                        },
                        onCreate = {
                            showPlaylistPicker = false
                            showCreatePlaylist = true
                            pendingPlaylistCover = null
                        }
                    )
                }

                if (showQueue) QueueDialog(songs, current, { showQueue = false }) { playSong(it); showQueue = false }
                if (showSleepTimer) SleepTimerDialog(sleepUntil, { showSleepTimer = false }) { setSleepTimer(it); showSleepTimer = false }
                if (showEqualizer) EqualizerDialog({ showEqualizer = false }) { applyEqualizerPreset(it) }
                if (showBackup) BackupDialog({ showBackup = false }, { exportBackup() }, { importBackup() })
                if (showDuplicates) DuplicateDialog(songs, { showDuplicates = false }) { }
                val editorSong = songs.firstOrNull { it.id == showEditorSongId }
                if (editorSong != null) SongEditorDialog(editorSong, { showEditorSongId = null }) { t, a, al -> editSong(editorSong, t, a, al) }

                if (showYoutube) {
                    YoutubeScreen(
                        inPip = youtubePip,
                        onDismiss = { closeYoutubeAndResumeMusic() },
                        onPip = { enterYoutubePip() }
                    )
                }
            }
        }
        requestAudioPermission()
        connectController()
    }

    private fun loadSavedData() {
        favorites = prefs.getStringSet("favorites", emptySet())
            ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

        historyIds = prefs.getString("history", null)?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()
        prefs.getString("play_counts", null)?.let { rawCounts ->
            runCatching {
                val o = JSONObject(rawCounts)
                playCounts = o.keys().asSequence().associate { key -> key.toLong() to o.getInt(key) }
            }
        }
        val raw = prefs.getString("playlists", null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<Playlist>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ids = mutableSetOf<Long>()
                val songsArray = o.optJSONArray("songs") ?: JSONArray()
                for (j in 0 until songsArray.length()) ids += songsArray.getLong(j)
                val cover = if (o.isNull("cover")) null else o.optString("cover", null)
                list += Playlist(o.getLong("id"), o.getString("name"), cover, ids)
            }
            playlists = list
        }
    }

    private fun persistFavorites() {
        prefs.edit().putStringSet("favorites", favorites.map { it.toString() }.toSet()).apply()
    }

    private fun persistPlaylists() {
        val arr = JSONArray()
        playlists.forEach { playlist ->
            arr.put(JSONObject().apply {
                put("id", playlist.id)
                put("name", playlist.name)
                put("cover", playlist.coverUri ?: JSONObject.NULL)
                put("songs", JSONArray(playlist.songIds.toList()))
            })
        }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    private fun recordPlay(id: Long) {
        historyIds = (listOf(id) + historyIds.filterNot { it == id }).take(100)
        playCounts = playCounts + (id to ((playCounts[id] ?: 0) + 1))
        prefs.edit()
            .putString("history", historyIds.joinToString(","))
            .putString("play_counts", JSONObject(playCounts.mapKeys { it.key.toString() }).toString())
            .apply()
    }

    private fun setSleepTimer(minutes: Int) {
        sleepHandler.removeCallbacksAndMessages(null)
        if (minutes <= 0) { sleepUntil = 0L; return }
        sleepUntil = System.currentTimeMillis() + minutes * 60_000L
        sleepHandler.postDelayed({ controller?.pause(); sleepUntil = 0L }, minutes * 60_000L)
        Toast.makeText(this, "Sleep Timer: $minutes min", Toast.LENGTH_SHORT).show()
    }

    private fun applyEqualizerPreset(preset: String) {
        startService(Intent(this, MusicService::class.java).setAction("com.sanches.music.EQ_PRESET").putExtra("preset", preset))
        Toast.makeText(this, "Equalizador: $preset", Toast.LENGTH_SHORT).show()
    }

    private fun exportBackup() {
        val payload = JSONObject().apply {
            put("favorites", JSONArray(favorites.toList()))
            put("history", JSONArray(historyIds))
            put("playCounts", JSONObject(playCounts.mapKeys { it.key.toString() }))
            val ps = JSONArray()
            playlists.forEach { p -> ps.put(JSONObject().apply { put("id", p.id); put("name", p.name); put("cover", p.coverUri ?: JSONObject.NULL); put("songs", JSONArray(p.songIds.toList())) }) }
            put("playlists", ps)
        }
        val file = java.io.File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "sanches_music_backup.json")
        file.writeText(payload.toString(2))
        val uri = androidx.core.content.FileProvider.getUriForFile(this, packageName + ".provider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Exportar backup"))
    }

    private fun importBackup() {
        Toast.makeText(this, "Importação automática ficará disponível no próximo passo do backup.", Toast.LENGTH_LONG).show()
    }

    private fun editSong(song: Song, title: String, artist: String, album: String) {
        runCatching {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, title.trim())
                put(MediaStore.Audio.Media.ARTIST, artist.trim())
                put(MediaStore.Audio.Media.ALBUM, album.trim())
            }
            contentResolver.update(song.uri, values, null, null)
        }.onSuccess {
            showEditorSongId = null
            loadSongs()
            Toast.makeText(this, "Metadados atualizados", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "O Android bloqueou a edição deste arquivo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleFavorite(id: Long) {
        favorites = if (favorites.contains(id)) favorites - id else favorites + id
        persistFavorites()
    }

    private fun createPlaylist(name: String, selectedIds: Set<Long>) {
        val clean = name.trim()
        if (clean.isBlank()) return
        val finalIds = selectedIds + listOfNotNull(songForPlaylist).toSet()
        playlists = playlists + Playlist(
            System.currentTimeMillis(),
            clean,
            pendingPlaylistCover?.toString(),
            finalIds
        )
        pendingPlaylistCover = null
        showCreatePlaylist = false
        songForPlaylist = null
        persistPlaylists()
        Toast.makeText(this, finalIds.size.toString() + " faixa(s) adicionada(s) à playlist", Toast.LENGTH_SHORT).show()
    }

    private fun savePlaylistEdit(id: Long, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        playlists = playlists.map {
            if (it.id == id) it.copy(
                name = clean,
                coverUri = pendingPlaylistCover?.toString() ?: it.coverUri
            ) else it
        }
        pendingPlaylistCover = null
        editingPlaylistId = null
        persistPlaylists()
    }

    private fun deletePlaylist(id: Long) {
        playlists = playlists.filterNot { it.id == id }
        persistPlaylists()
    }

    private fun addSongToPlaylist(playlistId: Long, songId: Long) {
        playlists = playlists.map {
            if (it.id == playlistId) it.copy(songIds = it.songIds + songId) else it
        }
        persistPlaylists()
        songForPlaylist = null
        showPlaylistPicker = false
        Toast.makeText(this, "Adicionada à playlist", Toast.LENGTH_SHORT).show()
    }

    private fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlists = playlists.map {
            if (it.id == playlistId) it.copy(songIds = it.songIds - songId) else it
        }
        persistPlaylists()
    }

    private fun requestAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) loadSongs()
        else permissionLauncher.launch(permission)
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            controller = future.get()
            observeController()
        }, mainExecutor)
    }

    private fun observeController() {
        Thread {
            while (!isFinishing) {
                runOnUiThread {
                    val c = controller ?: return@runOnUiThread
                    isPlaying = c.isPlaying
                    shuffleEnabled = c.shuffleModeEnabled
                    repeatMode = c.repeatMode
                    position = c.currentPosition.coerceAtLeast(0)
                    duration = if (c.duration > 0) c.duration else 1
                    val index = c.currentMediaItemIndex
                    if (index in songs.indices) current = songs[index]
                }
                Thread.sleep(400)
            }
        }.start()
    }

    private fun toggle() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    override fun onBackPressed() {
        if (showYoutube && !youtubePip) {
            closeYoutubeAndResumeMusic()
            return
        }
        if (showPlayer) {
            showPlayer = false
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastBackPressAt <= 1800L) {
            super.onBackPressed()
        } else {
            lastBackPressAt = now
            Toast.makeText(this, "Aperte duas vezes para sair", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildNotificationArtwork(): ByteArray? {
        if (notificationArtworkBytes != null) return notificationArtworkBytes
        return runCatching {
            val size = 512
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.rgb(229, 9, 20))

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            }

            paint.textSize = 190f
            canvas.drawText("☠", size / 2f, 270f, paint)
            paint.textSize = 82f
            canvas.drawText("☠", 92f, 445f, paint)
            canvas.drawText("☠", 420f, 445f, paint)

            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }.also {
                notificationArtworkBytes = it
            }
        }.getOrNull()
    }

    private fun loadSongs() {
        Thread {
            val result = mutableListOf<Song>()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DURATION
            )
            val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%') AND ${MediaStore.Audio.Media.DURATION} > 0"
            runCatching {
                contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        val rawArtist = c.getString(artistCol)
                        result += Song(id, c.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Sem título",
                            if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") "Artista desconhecido" else rawArtist,
                            c.getString(albumCol) ?: "",
                            c.getLong(albumIdCol), uri, null)
                    }
                }
            }.onFailure { e -> runOnUiThread { Toast.makeText(this, "Erro ao ler músicas: ${e.message ?: "desconhecido"}", Toast.LENGTH_LONG).show() } }
            runOnUiThread {
                songs = result.distinctBy { it.id }
                if (result.isEmpty()) Toast.makeText(this, "Nenhuma música encontrada. Verifique a permissão de Áudio e toque em Atualizar biblioteca.", Toast.LENGTH_LONG).show()
            }
        }.start()
    }
    private fun playSong(song: Song) {
        val c = controller ?: return
        val items = songs.map {
            MediaItem.Builder()
                .setMediaId(it.id.toString())
                .setUri(it.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist(it.artist)
                        .setAlbumTitle(it.album)
                        .setArtworkUri(
                            if (it.albumId > 0) Uri.parse("content://media/external/audio/albums/" + it.albumId + "/album_art") else null
                        )
                        .setArtworkData(
                            buildNotificationArtwork(),
                            MediaMetadata.PICTURE_TYPE_ILLUSTRATION
                        )
                        .build()
                ).build()
        }
        val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
        recordPlay(song.id)
        current = song
        showPlayer = true
    }

    private fun deleteSelected() {
        val targets = songs.filter { selectedIds.contains(it.id) }
        if (targets.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 30) {
            val request = MediaStore.createDeleteRequest(contentResolver, targets.map { it.uri })
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            targets.forEach { runCatching { contentResolver.delete(it.uri, null, null) } }
            selectedIds = emptySet()
            loadSongs()
        }
    }

    private fun deleteSong(song: Song) {
        selectedIds = setOf(song.id)
        deleteSelected()
    }

    private fun openYoutube(context: Context) {
        val c = controller
        youtubeReturnSongId = current?.id ?: c?.currentMediaItem?.mediaId?.toLongOrNull()
        youtubeReturnPosition = c?.currentPosition?.coerceAtLeast(0L) ?: 0L
        youtubeReturnWasPlaying = c?.isPlaying == true
        showYoutube = true
        if (Build.VERSION.SDK_INT >= 31) {
            setPictureInPictureParams(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(true)
                    .build()
            )
        }
    }

    private fun closeYoutubeAndResumeMusic() {
        showYoutube = false
        youtubePip = false

        if (!youtubeReturnWasPlaying) {
            youtubeReturnSongId = null
            return
        }

        val c = controller ?: return
        val id = youtubeReturnSongId
        val index = songs.indexOfFirst { it.id == id }

        if (index >= 0) {
            runCatching {
                c.seekTo(index, youtubeReturnPosition)
                c.play()
                current = songs[index]
            }
        } else {
            runCatching { c.seekTo(youtubeReturnPosition); c.play() }
        }

        youtubeReturnSongId = null
        youtubeReturnPosition = 0L
        youtubeReturnWasPlaying = false
    }

    private fun enterYoutubePip() {
        val c = controller
        if (c != null) {
            youtubeReturnSongId = current?.id ?: c.currentMediaItem?.mediaId?.toLongOrNull()
            youtubeReturnPosition = c.currentPosition.coerceAtLeast(0L)
            youtubeReturnWasPlaying = c.isPlaying
            if (youtubeReturnWasPlaying) c.pause()
        }

        if (Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .apply {
                    if (Build.VERSION.SDK_INT >= 31) setAutoEnterEnabled(true)
                }
                .build()
            enterPictureInPictureMode(params)
        } else {
            Toast.makeText(this, "Este aparelho não suporta janela flutuante.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        youtubePip = isInPictureInPictureMode
    }

    override fun onDestroy() {
        controller?.release()
        super.onDestroy()
    }
}

@Composable
private fun SanchesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink,
            surface = Panel,
            primary = Red,
            secondary = Red,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
private fun Home(
    songs: List<Song>, current: Song?, playing: Boolean, query: String,
    selectedIds: Set<Long>, favorites: Set<Long>, playlists: List<Playlist>,
    historyIds: List<Long>, playCounts: Map<Long, Int>, showSettings: Boolean,
    onQuery: (String) -> Unit, onSong: (Song) -> Unit, onMiniOpen: () -> Unit,
    onToggle: () -> Unit, onSelect: (Long) -> Unit, onDelete: () -> Unit,
    onClearSelection: () -> Unit, onSettings: (Boolean) -> Unit,
    onToggleFavorite: (Long) -> Unit, onOpenPlaylistPicker: (Long) -> Unit,
    onRemoveFromPlaylist: (Long, Long) -> Unit, onDeleteSong: (Song) -> Unit,
    onCreatePlaylist: () -> Unit, onEditPlaylist: (Long) -> Unit,
    onDeletePlaylist: (Long) -> Unit, onYoutube: () -> Unit, onRefresh: () -> Unit,
    onPickCover: () -> Unit, onQueue: () -> Unit, onSleep: () -> Unit,
    onEqualizer: () -> Unit, onBackup: () -> Unit, onDuplicates: () -> Unit,
    onEditSong: (Long) -> Unit
) {
    var artistFilter by remember { mutableStateOf<String?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var activePlaylistId by remember { mutableStateOf<Long?>(null) }
    var menuSongId by remember { mutableStateOf<Long?>(null) }
    var smartFilter by remember { mutableStateOf("all") }
    val activePlaylist = playlists.firstOrNull { it.id == activePlaylistId }

    val filtered = remember(songs, query, artistFilter, favoritesOnly, activePlaylistId, playlists, favorites, smartFilter, historyIds, playCounts) {
        songs.filter { song ->
            val textMatch = query.isBlank() || song.title.contains(query, true) || song.artist.contains(query, true) || song.album.contains(query, true)
            val artistMatch = artistFilter == null || song.artist.equals(artistFilter, true)
            val favoriteMatch = !favoritesOnly || favorites.contains(song.id)
            val playlistMatch = activePlaylist == null || activePlaylist.songIds.contains(song.id)
            val smartMatch = when (smartFilter) {
                "history" -> historyIds.contains(song.id)
                "top" -> (playCounts[song.id] ?: 0) > 0
                else -> true
            }
            textMatch && artistMatch && favoriteMatch && playlistMatch && smartMatch
        }
    }
    val artists = remember(songs) {
        songs.map { it.artist.trim() }.filter { it.isNotBlank() && it != "Artista desconhecido" }
            .distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    val albums = remember(songs) { songs.map { it.album }.filter { it.isNotBlank() }.distinct().size }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF050505), Color(0xFF0B0B0B), Color(0xFF180607))))) {
        LazyColumn(
            Modifier.fillMaxSize().padding(bottom = if (current != null) 88.dp else 18.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SANCHES", color = Red, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Text("Music", color = Red, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    }
                    Surface(shape = CircleShape, color = Panel2, modifier = Modifier.size(46.dp)) {
                        IconButton(onClick = { onSettings(true) }) { Icon(Icons.Default.Settings, "Configurações", tint = Red) }
                    }
                }
            }
            item { SearchBar(query, onQuery) }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickChip("FAIXAS", songs.size.toString(), Red)
                    QuickChip("BANDAS", artists.size.toString(), Red)
                    QuickChip("ÁLBUNS", albums.toString(), Red)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !favoritesOnly && activePlaylistId == null, onClick = { favoritesOnly = false; activePlaylistId = null }, label = { Text("Todas") })
                    FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = !favoritesOnly; activePlaylistId = null }, label = { Text("♥ Favoritos") })
                    FilterChip(
                        selected = activePlaylistId != null,
                        onClick = {
                            if (playlists.isNotEmpty()) {
                                activePlaylistId = if (activePlaylistId == null) playlists.first().id else null
                                favoritesOnly = false
                            }
                        },
                        label = { Text("♫ Playlists") }
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterChip(selected = smartFilter == "all", onClick = { smartFilter = "all" }, label = { Text("Biblioteca") })
                    FilterChip(selected = smartFilter == "history", onClick = { smartFilter = "history" }, label = { Text("Histórico") })
                    FilterChip(selected = smartFilter == "top", onClick = { smartFilter = "top" }, label = { Text("Mais tocadas") })
                    TextButton(onClick = onQueue) { Text("Fila", color = Red) }
                    TextButton(onClick = onSleep) { Text("Timer", color = Red) }
                    TextButton(onClick = onEqualizer) { Text("EQ", color = Red) }
                    TextButton(onClick = onBackup) { Text("Backup", color = Red) }
                    TextButton(onClick = onDuplicates) { Text("Duplicadas", color = Red) }
                }
            }
            if (artists.isNotEmpty()) {
                item { Text("Bandas e artistas", color = Red, modifier = Modifier.padding(start = 22.dp, top = 8.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = artistFilter == null, onClick = { artistFilter = null }, label = { Text("Todas") })
                        artists.forEach { artist ->
                            FilterChip(selected = artistFilter == artist, onClick = { artistFilter = if (artistFilter == artist) null else artist }, label = { Text(artist, maxLines = 1) })
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            activePlaylist != null -> activePlaylist.name
                            favoritesOnly -> "Favoritos"
                            query.isBlank() -> "Sua biblioteca"
                            else -> "Resultados"
                        },
                        color = Red, fontSize = 22.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(filtered.size.toString(), color = TextSoft, fontSize = 14.sp)
                }
            }
            if (playlists.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        playlists.forEach { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                selected = activePlaylistId == playlist.id,
                                onClick = {
                                    activePlaylistId = if (activePlaylistId == playlist.id) null else playlist.id
                                    favoritesOnly = false
                                },
                                onEdit = { onEditPlaylist(playlist.id) }
                            )
                        }
                        AddPlaylistCard(onCreatePlaylist)
                    }
                }
            } else {
                item {
                    OutlinedButton(onClick = onCreatePlaylist, modifier = Modifier.padding(horizontal = 22.dp, vertical = 5.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Red)) {
                        Icon(Icons.Default.Add, null, tint = Red)
                        Spacer(Modifier.width(7.dp))
                        Text("Criar playlist", color = Red)
                    }
                }
            }
            if (selectedIds.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedIds.size.toString() + " selecionada(s)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClearSelection) { Icon(Icons.Default.Close, "Cancelar", tint = TextSoft) }
                        FilledIconButton(onClick = onDelete, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Red)) { Icon(Icons.Default.Delete, "Apagar") }
                    }
                }
            }
            items(filtered, key = { it.id }) { song ->
                TrackCard(
                    song = song, active = current?.id == song.id && playing,
                    selected = selectedIds.contains(song.id), selectionMode = selectedIds.isNotEmpty(),
                    favorite = favorites.contains(song.id), onClick = onSong, onSelect = onSelect,
                    onMore = { menuSongId = song.id }
                )
            }
        }

        if (current != null) {
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = Color(0xFF101010), shadowElevation = 12.dp) {
                CompactPlayer(current, playing, onMiniOpen, onToggle)
            }
        }

        FloatingActionButton(
            onClick = onYoutube,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = if (current != null) 82.dp else 18.dp),
            containerColor = Red, contentColor = Color.White
        ) { Icon(Icons.Default.SmartDisplay, "Abrir YouTube") }

        if (showSettings) SettingsDialog(onClose = { onSettings(false) }, onRescan = { onRefresh(); onSettings(false) })

        val menuSong = songs.firstOrNull { it.id == menuSongId }
        if (menuSong != null) {
            TrackMenuDialog(
                song = menuSong, favorite = favorites.contains(menuSong.id), canRemoveFromPlaylist = activePlaylistId != null,
                onDismiss = { menuSongId = null },
                onFavorite = { onToggleFavorite(menuSong.id); menuSongId = null },
                onPlaylist = { onOpenPlaylistPicker(menuSong.id); menuSongId = null },
                onRemoveFromPlaylist = { activePlaylistId?.let { onRemoveFromPlaylist(it, menuSong.id) }; menuSongId = null },
                onEditSong = { onEditSong(menuSong.id); menuSongId = null },
                onDelete = { onDeleteSong(menuSong); menuSongId = null }
            )
        }
    }
}

@Composable
private fun SettingsDialog(onClose: () -> Unit, onRescan: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Panel,
        title = { Text("Configurações", color = Red, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sanches Music", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Biblioteca local • Reprodução em segundo plano", color = TextSoft)
                OutlinedButton(onClick = onRescan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Atualizar biblioteca")
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Fechar", color = Red) } }
    )
}

@Composable
private fun TrackMenuDialog(
    song: Song,
    favorite: Boolean,
    canRemoveFromPlaylist: Boolean,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onPlaylist: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onEditSong: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(song.title, color = Red, fontWeight = FontWeight.Bold, maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                MenuAction(if (favorite) "Remover dos favoritos" else "Adicionar aos favoritos", Icons.Default.Favorite, onFavorite)
                MenuAction("Adicionar à playlist", Icons.Default.PlaylistAdd, onPlaylist)
                if (canRemoveFromPlaylist) {
                    MenuAction("Remover desta playlist", Icons.Default.RemoveCircleOutline, onRemoveFromPlaylist)
                }
                MenuAction("Editar nome/artista/álbum", Icons.Default.Edit, onEditSong)
                MenuAction("Apagar música do celular", Icons.Default.Delete, onDelete)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = Red) } }
    )
}

@Composable
private fun MenuAction(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = Red)
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color.White)
    }
}

@Composable
private fun PlaylistPickerDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onChoose: (Long) -> Unit,
    onCreate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Adicionar à playlist", color = Red, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                playlists.forEach { playlist ->
                    TextButton(onClick = { onChoose(playlist.id) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.QueueMusic, null, tint = Red)
                        Spacer(Modifier.width(8.dp))
                        Text(playlist.name, color = Color.White)
                    }
                }
                TextButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null, tint = Red)
                    Spacer(Modifier.width(8.dp))
                    Text("Criar nova playlist", color = Red)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = Red) } }
    )
}

@Composable
private fun CreatePlaylistDialog(
    songs: List<Song>, initialSongId: Long?, coverUri: Uri?,
    onPickCover: () -> Unit, onDismiss: () -> Unit,
    onCreate: (String, Set<Long>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIds by remember(initialSongId) { mutableStateOf(if (initialSongId != null) setOf(initialSongId) else emptySet()) }
    var filter by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Panel,
        title = { Text("Nova playlist", color = Red, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaylistCover(coverUri?.toString(), 95.dp)
                OutlinedButton(onClick = onPickCover, border = androidx.compose.foundation.BorderStroke(1.dp, Red)) {
                    Icon(Icons.Default.Image, null, tint = Red)
                    Spacer(Modifier.width(6.dp))
                    Text("Escolher capa", color = Red)
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome da playlist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = filter, onValueChange = { filter = it }, label = { Text("Buscar música para adicionar") },
                    singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null, tint = Red) }, modifier = Modifier.fillMaxWidth()
                )
                Text(selectedIds.size.toString() + " faixa(s) selecionada(s)", color = Red, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                val visibleSongs = songs.filter {
                    filter.isBlank() || it.title.contains(filter, true) || it.artist.contains(filter, true) || it.album.contains(filter, true)
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(min = 70.dp, max = 280.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(visibleSongs, key = { it.id }) { song ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                selectedIds = if (selectedIds.contains(song.id)) selectedIds - song.id else selectedIds + song.id
                            }.padding(vertical = 4.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(song.id),
                                onCheckedChange = { checked -> selectedIds = if (checked) selectedIds + song.id else selectedIds - song.id },
                                colors = CheckboxDefaults.colors(checkedColor = Red)
                            )
                            Artwork(song, 40.dp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.title, color = Color.White, maxLines = 1)
                                Text(song.artist, color = TextSoft, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, selectedIds) }) { Text("Criar", color = Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSoft) } }
    )
}

@Composable
private fun EditPlaylistDialog(
    playlist: Playlist,
    coverUri: Uri?,
    onPickCover: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(playlist.id) { mutableStateOf(playlist.name) }
    val shownCover = coverUri?.toString() ?: playlist.coverUri
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Editar playlist", color = Red, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaylistCover(shownCover, 120.dp)
                OutlinedButton(onClick = onPickCover, border = androidx.compose.foundation.BorderStroke(1.dp, Red)) {
                    Icon(Icons.Default.Image, null, tint = Red)
                    Spacer(Modifier.width(6.dp))
                    Text("Alterar capa", color = Red)
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da playlist") },
                    singleLine = true
                )
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = Red)
                    Spacer(Modifier.width(6.dp))
                    Text("Apagar playlist", color = Red)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("Salvar", color = Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSoft) } }
    )
}

@Composable
private fun YoutubeScreen(
    inPip: Boolean,
    onDismiss: () -> Unit,
    onPip: () -> Unit
) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .navigationBarsPadding()
    ) {
        AndroidView(
            factory = {
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.allowContentAccess = true
                    settings.allowFileAccess = false
                    webViewClient = WebViewClient()
                    loadUrl("https://m.youtube.com/")
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!inPip) {
            Surface(
                Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                color = Color(0xDD050505)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("YouTube", color = Red, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("Sanches Music • reprodução em segundo plano", color = TextSoft, fontSize = 11.sp)
                    }
                    FilledIconButton(
                        onClick = onPip,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Red)
                    ) {
                        Icon(Icons.Default.PictureInPictureAlt, "Janela flutuante")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Fechar", tint = Color.White)
                    }
                }
            }

            Surface(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xEE111111)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, null, tint = Red)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Toque em Janela flutuante para continuar assistindo enquanto usa outros apps.",
                        color = TextSoft,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value,
        onValue,
        Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        singleLine = true,
        placeholder = { Text("Buscar música, artista ou álbum", color = TextSoft) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = Red) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton({ onValue("") }) { Icon(Icons.Default.Close, null, tint = TextSoft) }
        },
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Panel,
            focusedContainerColor = Panel2,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Red
        )
    )
}

@Composable
private fun QuickChip(label: String, value: String, accent: Color) {
    Surface(shape = RoundedCornerShape(15.dp), color = Panel) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(label, fontSize = 10.sp, color = accent, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(value, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    Surface(
        modifier = Modifier.width(150.dp).clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color(0xFF2A090C) else Panel
    ) {
        Column(Modifier.padding(8.dp)) {
            Box {
                PlaylistCover(playlist.coverUri, 132.dp)
                IconButton(onClick = onEdit, modifier = Modifier.align(Alignment.TopEnd).size(34.dp)) {
                    Icon(Icons.Default.MoreHoriz, null, tint = Color.White)
                }
            }
            Text(playlist.name, color = if (selected) RedBright else Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(playlist.songIds.size.toString() + " faixas", color = TextSoft, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AddPlaylistCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(150.dp).height(182.dp).clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = Panel
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.AddCircle, null, tint = Red, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(8.dp))
            Text("Nova playlist", color = Red, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlaylistCover(uriString: String?, size: Dp) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uriString) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (uriString.isNullOrBlank()) null
                else context.contentResolver.openInputStream(Uri.parse(uriString))?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), null, Modifier.size(size).clip(RoundedCornerShape(15.dp)), contentScale = ContentScale.Crop)
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(15.dp)).background(
                Brush.linearGradient(listOf(Color(0xFF35070A), Red))
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.QueueMusic, null, tint = Color.White, modifier = Modifier.size(size / 3))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackCard(
    song: Song,
    active: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    favorite: Boolean,
    onClick: (Song) -> Unit,
    onSelect: (Long) -> Unit,
    onMore: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = { if (selectionMode) onSelect(song.id) else onClick(song) },
                onLongClick = { onSelect(song.id) }
            )
            .background(if (selected) Color(0xFF3A0B0F) else if (active) Color(0xFF21090B) else Color.Transparent)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(song, 58.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (selected || active) RedBright else Color.White,
                fontSize = 17.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
            Text(
                song.artist + if (song.album.isNotBlank()) "  •  " + song.album else "",
                color = TextSoft,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
        if (favorite) Icon(Icons.Default.Favorite, null, tint = Red, modifier = Modifier.size(18.dp))
        if (selected) {
            Icon(Icons.Default.CheckCircle, null, tint = Red, modifier = Modifier.padding(horizontal = 8.dp))
        } else {
            IconButton(onClick = onMore) { Icon(Icons.Default.MoreHoriz, "Mais opções", tint = Red) }
        }
    }
}

@Composable
private fun NowCard(song: Song, playing: Boolean, open: () -> Unit, toggle: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp).clickable { open() },
        shape = RoundedCornerShape(24.dp),
        color = Panel2
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(song, 74.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Spacer(Modifier.height(4.dp))
                Text(song.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(song.artist, color = TextSoft, fontSize = 13.sp, maxLines = 1)
            }
            FilledIconButton(onClick = toggle, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Red)) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
            }
        }
    }
}

@Composable
private fun CompactPlayer(song: Song, playing: Boolean, open: () -> Unit, toggle: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable { open() }, color = Color(0xFF101010), tonalElevation = 8.dp) {
        Column {
            LinearProgressIndicator(
                progress = { if (playing) 0.45f else 0f },
                Modifier.fillMaxWidth().height(2.dp),
                color = Red,
                trackColor = Panel2
            )
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(song, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(song.artist, fontSize = 12.sp, color = TextSoft, maxLines = 1)
                }
                IconButton(toggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Red) }
                Icon(Icons.Default.QueueMusic, null, tint = Red, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}

@Composable
private fun FullPlayer(
    song: Song,
    playing: Boolean,
    position: Long,
    duration: Long,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    onFavorite: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF30070A), Ink, Color(0xFF080808))))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sanches Music", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                IconButton(onFavorite) { Icon(Icons.Default.Favorite, null, tint = Red) }
            }
            Spacer(Modifier.height(45.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Artwork(song, 310.dp) }
            Spacer(Modifier.height(30.dp))
            Text(song.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 2)
            Text(song.artist, color = TextSoft, fontSize = 17.sp, maxLines = 1)
            if (song.album.isNotBlank()) Text(song.album, color = TextSoft, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Slider(
                value = position.toFloat().coerceIn(0f, duration.toFloat()),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(position), color = TextSoft, fontSize = 12.sp)
                Text(formatTime(duration), color = TextSoft, fontSize = 12.sp)
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onShuffle) {
                    Icon(Icons.Default.Shuffle, null, tint = if (shuffleEnabled) Color.White else Red)
                }
                IconButton(onPrev) { Icon(Icons.Default.SkipPrevious, null, Modifier.size(38.dp), tint = Red) }
                FilledIconButton(
                    onToggle,
                    Modifier.size(76.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Red, contentColor = Color.White)
                ) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(40.dp))
                }
                IconButton(onNext) { Icon(Icons.Default.SkipNext, null, Modifier.size(38.dp), tint = Red) }
                IconButton(onRepeat) {
                    Icon(
                        if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        null,
                        tint = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF) Red else Color.White
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Artwork(song: Song, size: Dp) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val targetPx = (size.value * density).toInt().coerceIn(96, 960)
    val loaded by produceState<Bitmap?>(initialValue = song.art, song.id, targetPx) {
        if (value == null) {
            value = artworkCache.get(song.id)
            if (value == null) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        var result: Bitmap? = null
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, song.uri)
                            val bytes = retriever.embeddedPicture
                            if (bytes != null) {
                                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                                val sample = generateSequence(1) { it * 2 }
                                    .takeWhile { it <= 32 }
                                    .lastOrNull { bounds.outWidth / it >= targetPx && bounds.outHeight / it >= targetPx } ?: 1
                                result = BitmapFactory.decodeByteArray(
                                    bytes, 0, bytes.size,
                                    BitmapFactory.Options().apply {
                                        inSampleSize = sample
                                        inPreferredConfig = Bitmap.Config.ARGB_8888
                                    }
                                )
                            }
                        } finally {
                            retriever.release()
                        }

                        if (result == null && song.albumId > 0) {
                            val uri = Uri.parse("content://media/external/audio/albums/" + song.albumId + "/album_art")
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            context.contentResolver.openInputStream(uri)?.use {
                                BitmapFactory.decodeStream(it, null, bounds)
                            }
                            val sample = generateSequence(1) { it * 2 }
                                .takeWhile { it <= 32 }
                                .lastOrNull { bounds.outWidth / it >= targetPx && bounds.outHeight / it >= targetPx } ?: 1
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                result = BitmapFactory.decodeStream(
                                    input, null,
                                    BitmapFactory.Options().apply {
                                        inSampleSize = sample
                                        inPreferredConfig = Bitmap.Config.ARGB_8888
                                    }
                                )
                            }
                        }
                        result
                    }.getOrNull()
                }?.also { artworkCache.put(song.id, it) }
            }
        }
    }

    if (loaded != null) {
        Image(
            loaded!!.asImageBitmap(),
            null,
            Modifier.size(size).clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(20.dp)).background(
                Brush.linearGradient(listOf(Color(0xFF5C0006), RedBright))
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, null, Modifier.size(size / 3), tint = Color.White)
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}


@Composable
private fun QueueDialog(
    songs: List<Song>,
    current: Song?,
    onDismiss: () -> Unit,
    onPlay: (Song) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Fila de reprodução", color = Red, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(songs, key = { it.id }) { song ->
                    TextButton(onClick = { onPlay(song) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (song.id == current?.id) "▶ " + song.title else song.title, color = if (song.id == current?.id) Red else Color.White)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = Red) } }
    )
}

@Composable
private fun SleepTimerDialog(
    remaining: Long,
    onDismiss: () -> Unit,
    onSet: (Int) -> Unit
) {
    val left = if (remaining > System.currentTimeMillis()) ((remaining - System.currentTimeMillis()) / 60000L).toInt() else 0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Sleep Timer", color = Red, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (left > 0) Text("Ativo: aproximadamente $left min", color = TextSoft)
                listOf(15, 30, 45, 60, 90).forEach { m ->
                    TextButton(onClick = { onSet(m) }, modifier = Modifier.fillMaxWidth()) { Text("$m minutos", color = Color.White) }
                }
                TextButton(onClick = { onSet(0) }, modifier = Modifier.fillMaxWidth()) { Text("Desativar", color = Red) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = Red) } }
    )
}

@Composable
private fun EqualizerDialog(
    onDismiss: () -> Unit,
    onPreset: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Equalizador", color = Red, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Preset aplicado ao áudio em reprodução.", color = TextSoft, fontSize = 12.sp)
                listOf("Normal", "Rock", "Metal", "Bass Boost", "Vocal").forEach { preset ->
                    TextButton(onClick = { onPreset(preset); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                        Text(preset, color = Color.White)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = Red) } }
    )
}

@Composable
private fun BackupDialog(
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Backup", color = Red, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Salva favoritos, histórico, contagens e playlists.", color = TextSoft)
                Button(onClick = { onExport(); onDismiss() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                    Icon(Icons.Default.Upload, null); Spacer(Modifier.width(8.dp)); Text("Exportar backup")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, null, tint = Red); Spacer(Modifier.width(8.dp)); Text("Importar backup", color = Red)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = Red) } }
    )
}

@Composable
private fun DuplicateDialog(
    songs: List<Song>,
    onDismiss: () -> Unit,
    onDelete: (Set<Long>) -> Unit
) {
    val groups = remember(songs) {
        songs.groupBy { it.title.trim().lowercase() + "|" + it.artist.trim().lowercase() + "|" + it.album.trim().lowercase() }
            .values.filter { it.size > 1 }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Músicas duplicadas", color = Red, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (groups.isEmpty()) Text("Nenhuma duplicata encontrada.", color = TextSoft)
                else {
                    Text(groups.size.toString() + " grupo(s) encontrado(s). Se quiser apagar, selecione as faixas na biblioteca.", color = TextSoft)
                    groups.take(10).forEach { group ->
                        Text(group.first().title + " • " + group.size + " arquivos", color = Color.White, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar", color = Red) } }
    )
}

@Composable
private fun SongEditorDialog(
    song: Song,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember(song.id) { mutableStateOf(song.title) }
    var artist by remember(song.id) { mutableStateOf(song.artist) }
    var album by remember(song.id) { mutableStateOf(song.album) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Editar música", color = Red, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text("Artista") }, singleLine = true)
                OutlinedTextField(album, { album = it }, label = { Text("Álbum") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, artist, album) }) { Text("Salvar", color = Red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSoft) } }
    )
}
