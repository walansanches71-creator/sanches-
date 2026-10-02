package com.sanches.music

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val art: Bitmap?
)

private val Bg = Color(0xFF090B35)
private val Purple = Color(0xFF28115D)
private val Soft = Color(0xFF8E8AA8)
private val Accent = Color(0xFFFFB51B)

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var songs by mutableStateOf<List<Song>>(emptyList())
    private var current by mutableStateOf<Song?>(null)
    private var isPlaying by mutableStateOf(false)
    private var position by mutableLongStateOf(0L)
    private var duration by mutableLongStateOf(1L)
    private var showPlayer by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) loadSongs() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SanchesTheme {
                if (showPlayer && current != null) {
                    PlayerScreen(
                        song = current!!,
                        playing = isPlaying,
                        position = position,
                        duration = duration,
                        onBack = { showPlayer = false },
                        onToggle = { toggle() },
                        onSeek = { controller?.seekTo(it) },
                        onNext = { controller?.seekToNextMediaItem() },
                        onPrev = { controller?.seekToPreviousMediaItem() }
                    )
                } else {
                    LibraryScreen(
                        songs = songs,
                        current = current,
                        playing = isPlaying,
                        onSong = { playSong(it) },
                        onMini = { showPlayer = true },
                        onToggle = { toggle() }
                    )
                }
            }
        }
        requestAudioPermission()
        connectController()
    }

    private fun toggle() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    private fun requestAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO
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
                    position = c.currentPosition.coerceAtLeast(0)
                    duration = if (c.duration > 0) c.duration else 1
                    val idx = c.currentMediaItemIndex
                    if (idx >= 0 && idx < songs.size) current = songs[idx]
                }
                Thread.sleep(400)
            }
        }.start()
    }

    private fun loadSongs() {
        val result = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID
        )
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            MediaStore.Audio.Media.IS_MUSIC + " != 0",
            null,
            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val artUri = Uri.parse("content://media/external/audio/albumart/" + albumId)
                val art = try {
                    contentResolver.openInputStream(artUri)?.use { BitmapFactory.decodeStream(it) }
                } catch (_: Exception) { null }
                val artist = c.getString(artistCol)
                result += Song(
                    id,
                    c.getString(titleCol) ?: "Sem título",
                    if (artist.isNullOrBlank() || artist == "<unknown>") "Artista desconhecido" else artist,
                    c.getString(albumCol) ?: "",
                    uri,
                    art
                )
            }
        }
        songs = result
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
                        .build()
                )
                .build()
        }
        val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
        current = song
        showPlayer = true
    }

    override fun onDestroy() {
        controller?.release()
        super.onDestroy()
    }
}

@Composable
fun SanchesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg,
            surface = Color(0xFF11123E),
            primary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun LibraryScreen(
    songs: List<Song>,
    current: Song?,
    playing: Boolean,
    onSong: (Song) -> Unit,
    onMini: () -> Unit,
    onToggle: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Bg, Color(0xFF160B42), Purple)))
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Menu, null, Modifier.size(30.dp))
                Spacer(Modifier.width(28.dp))
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF8C2DDB)) {
                    Text("Ver seu resumo ✨", Modifier.padding(horizontal = 18.dp, vertical = 10.dp), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Search, null, Modifier.size(29.dp))
                Spacer(Modifier.width(18.dp))
                Icon(Icons.Default.Settings, null, Modifier.size(27.dp))
            }

            val tabs = listOf("Para você", "Faixas", "Playlists", "Pastas", "Álbuns")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(34.dp)
            ) {
                tabs.forEachIndexed { i, tab ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            tab,
                            fontSize = if (i == 1) 26.sp else 22.sp,
                            fontWeight = if (i == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (i == 1) Color.White else Soft
                        )
                        if (i == 1) {
                            Box(
                                Modifier.padding(top = 10.dp).width(62.dp).height(3.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(28.dp, 20.dp, 28.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(songs.size.toString() + " Músicas", fontSize = 24.sp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.Sort, null)
                Spacer(Modifier.width(18.dp))
                Icon(Icons.Default.FormatListBulleted, null)
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ActionButton("Aleatório", Icons.Default.Shuffle, Modifier.weight(1f)) {
                    if (songs.isNotEmpty()) onSong(songs.random())
                }
                ActionButton("Reproduzir", Icons.Default.PlayArrow, Modifier.weight(1f)) {
                    if (songs.isNotEmpty()) onSong(songs.first())
                }
            }

            LazyColumn(
                Modifier.weight(1f).padding(top = 12.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    SongRow(song, current?.id == song.id && playing, onSong)
                }
            }

            if (current != null) MiniPlayer(current, playing, onMini, onToggle)
        }
    }
}

@Composable
fun ActionButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick,
        modifier.height(58.dp),
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF3B3B3B))
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SongRow(song: Song, active: Boolean, onClick: (Song) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick(song) }.padding(horizontal = 28.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(song.art, 66.dp)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontSize = 19.sp, color = if (active) Accent else Color.White, maxLines = 1)
            Text(
                song.artist + if (song.album.isNotBlank()) " - " + song.album else "",
                fontSize = 14.sp, color = Soft, maxLines = 1
            )
        }
        Text("⋮", fontSize = 28.sp, color = Soft)
    }
}

@Composable
fun MiniPlayer(song: Song, playing: Boolean, onOpen: () -> Unit, onToggle: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable { onOpen() },
        color = Color(0xFF28115D),
        tonalElevation = 10.dp
    ) {
        Row(Modifier.padding(10.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(song.art, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(song.artist, fontSize = 13.sp, color = Soft, maxLines = 1)
            }
            IconButton(onToggle) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(34.dp))
            }
            Icon(Icons.Default.QueueMusic, null, Modifier.padding(end = 8.dp))
        }
    }
}

@Composable
fun PlayerScreen(
    song: Song,
    playing: Boolean,
    position: Long,
    duration: Long,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF17191A)).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(34.dp)) }
            Spacer(Modifier.weight(1f))
            Text("Música", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("  |  Letra", color = Soft, fontSize = 19.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.MoreVert, null)
        }

        Spacer(Modifier.height(70.dp))
        Artwork(song.art, 345.dp)
        Spacer(Modifier.height(32.dp))
        Text(song.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            song.artist + if (song.album.isNotBlank()) " - " + song.album else "",
            color = Soft, fontSize = 19.sp, maxLines = 1
        )

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Icon(Icons.Default.FavoriteBorder, null, Modifier.size(29.dp))
            Icon(Icons.Default.PlaylistAdd, null, Modifier.size(29.dp))
            Icon(Icons.Default.Tune, null, Modifier.size(29.dp))
            Icon(Icons.Default.Timer, null, Modifier.size(29.dp))
            Icon(Icons.Default.QueueMusic, null, Modifier.size(29.dp))
        }

        Spacer(Modifier.height(22.dp))
        Slider(
            value = position.toFloat().coerceIn(0f, duration.toFloat()),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..duration.toFloat()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = Soft, fontSize = 13.sp)
            Text(formatTime(duration), color = Soft, fontSize = 13.sp)
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Icon(Icons.Default.Shuffle, null, Modifier.size(28.dp), tint = Soft)
            IconButton(onPrev) { Icon(Icons.Default.SkipPrevious, null, Modifier.size(42.dp)) }
            FilledIconButton(
                onToggle,
                Modifier.size(78.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(42.dp))
            }
            IconButton(onNext) { Icon(Icons.Default.SkipNext, null, Modifier.size(42.dp)) }
            Icon(Icons.Default.Repeat, null, Modifier.size(28.dp), tint = Soft)
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
fun Artwork(bitmap: Bitmap?, size: Dp) {
    if (bitmap != null) {
        Image(
            bitmap.asImageBitmap(), null,
            Modifier.size(size).clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(Color(0xFF333333)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, null, Modifier.size(size / 3), tint = Soft)
        }
    }
}

fun formatTime(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
