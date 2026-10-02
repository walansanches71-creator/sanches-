package com.sanches.music

import android.Manifest
import android.content.ComponentName
import androidx.activity.result.IntentSenderRequest
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

private val Ink = Color(0xFF050505)
private val Panel = Color(0xFF111111)
private val Panel2 = Color(0xFF191919)
private val Red = Color(0xFFE50914)
private val RedBright = Color(0xFFFF3340)
private val Violet = Red
private val Mint = Red
private val Gold = Red
private val TextSoft = Color(0xFF8F8F8F)

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var songs by mutableStateOf<List<Song>>(emptyList())
    private var current by mutableStateOf<Song?>(null)
    private var isPlaying by mutableStateOf(false)
    private var position by mutableLongStateOf(0L)
    private var duration by mutableLongStateOf(1L)
    private var showPlayer by mutableStateOf(false)
    private var query by mutableStateOf("")
    private var selectedIds by mutableStateOf<Set<Long>>(emptySet())

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadSongs()
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            selectedIds = emptySet()
            loadSongs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SanchesTheme {
                if (showPlayer && current != null) {
                    FullPlayer(current!!, isPlaying, position, duration, { showPlayer = false }, { toggle() },
                        { controller?.seekTo(it) }, { controller?.seekToNextMediaItem() }, { controller?.seekToPreviousMediaItem() })
                } else {
                    Home(
    songs, current, isPlaying, query, selectedIds,
    { query = it }, { playSong(it) }, { showPlayer = true }, { toggle() },
    { id -> selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id },
    { deleteSelected() }, { selectedIds = emptySet() }
)
                }
            }
        }
        requestAudioPermission()
        connectController()
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

    private fun loadSongs() {
        val result = mutableListOf<Song>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID)

        contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
            MediaStore.Audio.Media.IS_MUSIC + " != 0", null,
            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")?.use { c ->
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
                val art = try { contentResolver.openInputStream(artUri)?.use { BitmapFactory.decodeStream(it) } }
                catch (_: Exception) { null }
                val artist = c.getString(artistCol)
                result += Song(id, c.getString(titleCol) ?: "Sem título",
                    if (artist.isNullOrBlank() || artist == "<unknown>") "Artista desconhecido" else artist,
                    c.getString(albumCol) ?: "", uri, art)
            }
        }
        songs = result
    }

    private fun playSong(song: Song) {
        val c = controller ?: return
        val items = songs.map {
            MediaItem.Builder().setMediaId(it.id.toString()).setUri(it.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist)
                    .setAlbumTitle(it.album).build()).build()
        }
        val index = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
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

    override fun onDestroy() {
        controller?.release()
        super.onDestroy()
    }
}

@Composable
private fun SanchesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Red, secondary = Red,
        onBackground = Color.White, onSurface = Color.White
    ), content = content)
}

@Composable
private fun Home(
    songs: List<Song>, current: Song?, playing: Boolean, query: String, selectedIds: Set<Long>,
    onQuery: (String) -> Unit, onSong: (Song) -> Unit, onMiniOpen: () -> Unit, onToggle: () -> Unit,
    onSelect: (Long) -> Unit, onDelete: () -> Unit, onClearSelection: () -> Unit
) {
    val filtered = remember(songs, query) {
        if (query.isBlank()) songs else songs.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
    }

    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFF050505), Color(0xFF0B0B0B), Color(0xFF180607)))
    )) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SANCHES", color = Red, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("Music", fontSize = 30.sp, fontWeight = FontWeight.Black)
                }
                Surface(shape = CircleShape, color = Panel2, modifier = Modifier.size(46.dp)) {
                    IconButton(onClick = {}) { Icon(Icons.Default.Settings, null, tint = TextSoft) }
                }
            }

            SearchBar(query, onQuery)

            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickChip("FAIXAS", songs.size.toString(), Violet)
                QuickChip("ÁLBUNS", songs.map { it.album }.filter { it.isNotBlank() }.distinct().size.toString(), Mint)
                QuickChip("PLAYLISTS", "0", Gold)
            }

            if (current != null && selectedIds.isEmpty()) NowCard(current, playing, onMiniOpen, onToggle)
            if (selectedIds.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedIds.size.toString() + " selecionada(s)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClearSelection) { Icon(Icons.Default.Close, "Cancelar", tint = TextSoft) }
                    FilledIconButton(onDelete, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Red)) {
                        Icon(Icons.Default.Delete, "Apagar")
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (query.isBlank()) "Sua biblioteca" else "Resultados", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(filtered.size.toString(), color = TextSoft, fontSize = 14.sp)
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (query.isBlank()) "Sua biblioteca" else "Resultados", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(filtered.size.toString(), color = TextSoft, fontSize = 14.sp)
            }

            LazyColumn(Modifier.weight(1f),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(filtered, key = { it.id }) { song ->
                    TrackCard(song, current?.id == song.id && playing, selectedIds.contains(song.id), onSong, onSelect)
                }
            }

            if (current != null) CompactPlayer(current, playing, onMiniOpen, onToggle)
        }
    }
}

@Composable
private fun SearchBar(value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value, onValue, Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        singleLine = true, placeholder = { Text("Buscar música, artista ou álbum", color = TextSoft) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSoft) },
        trailingIcon = { if (value.isNotEmpty()) IconButton({ onValue("") }) { Icon(Icons.Default.Close, null, tint = TextSoft) } },
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Panel, focusedContainerColor = Panel2,
            unfocusedBorderColor = Color.Transparent, focusedBorderColor = Violet))
}

@Composable
private fun QuickChip(label: String, value: String, accent: Color) {
    Surface(shape = RoundedCornerShape(15.dp), color = Panel) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(label, fontSize = 10.sp, color = accent, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NowCard(song: Song, playing: Boolean, open: () -> Unit, toggle: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 22.dp).clickable { open() },
        shape = RoundedCornerShape(24.dp), color = Panel2) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(song.art, 74.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("TOCANDO AGORA", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text(song.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(song.artist, color = TextSoft, fontSize = 13.sp, maxLines = 1)
            }
            FilledIconButton(onClick = toggle, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Violet)) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
            }
        }
    }
}

@Composable
private fun TrackCard(song: Song, active: Boolean, selected: Boolean, onClick: (Song) -> Unit, onSelect: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).combinedClickable(onClick = { onClick(song) }, onLongClick = { onSelect(song.id) })
        .background(if (selected) Color(0xFF3A0B0F) else if (active) Color(0xFF21090B) else Color.Transparent).padding(9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Artwork(song.art, 58.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = if (selected || active) RedBright else Color.White, fontSize = 17.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            Text(song.artist + if (song.album.isNotBlank()) "  •  " + song.album else "",
                color = TextSoft, fontSize = 13.sp, maxLines = 1)
        }
        if (selected) {
            Icon(Icons.Default.CheckCircle, null, tint = Red, modifier = Modifier.padding(horizontal = 10.dp))
        } else {
            IconButton(onClick = {}) { Icon(Icons.Default.MoreHoriz, null, tint = TextSoft) }
        }
    }
}

@Composable
private fun CompactPlayer(song: Song, playing: Boolean, open: () -> Unit, toggle: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable { open() }, color = Color(0xFF101329), tonalElevation = 8.dp) {
        Column {
            LinearProgressIndicator(progress = { if (playing) 0.45f else 0f },
                Modifier.fillMaxWidth().height(2.dp), color = Mint, trackColor = Panel2)
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(song.art, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(song.artist, fontSize = 12.sp, color = TextSoft, maxLines = 1)
                }
                IconButton(toggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
                Icon(Icons.Default.QueueMusic, null, tint = TextSoft, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}

@Composable
private fun FullPlayer(
    song: Song, playing: Boolean, position: Long, duration: Long,
    onBack: () -> Unit, onToggle: () -> Unit, onSeek: (Long) -> Unit,
    onNext: () -> Unit, onPrev: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFF30070A), Ink, Color(0xFF080808)))
    )) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(32.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Sanches Music", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                IconButton({}) { Icon(Icons.Default.MoreVert, null) }
            }

            Spacer(Modifier.height(45.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Artwork(song.art, 310.dp) }
            Spacer(Modifier.height(30.dp))
            Text(song.title, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 2)
            Text(song.artist, color = TextSoft, fontSize = 17.sp, maxLines = 1)
            if (song.album.isNotBlank()) Text(song.album, color = TextSoft, fontSize = 13.sp, maxLines = 1)

            Spacer(Modifier.weight(1f))
            Slider(value = position.toFloat().coerceIn(0f, duration.toFloat()),
                onValueChange = { onSeek(it.toLong()) }, valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(position), color = TextSoft, fontSize = 12.sp)
                Text(formatTime(duration), color = TextSoft, fontSize = 12.sp)
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                IconButton({}) { Icon(Icons.Default.Shuffle, null, tint = TextSoft) }
                IconButton(onPrev) { Icon(Icons.Default.SkipPrevious, null, Modifier.size(38.dp)) }
                FilledIconButton(onToggle, Modifier.size(76.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Mint, contentColor = Ink)) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(40.dp))
                }
                IconButton(onNext) { Icon(Icons.Default.SkipNext, null, Modifier.size(38.dp)) }
                IconButton({}) { Icon(Icons.Default.Repeat, null, tint = TextSoft) }
            }
            Spacer(Modifier.height(35.dp))
        }
    }
}

@Composable
private fun Artwork(bitmap: Bitmap?, size: Dp) {
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), null, Modifier.size(size).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Violet, Mint))),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, Modifier.size(size / 3), tint = Color.White)
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
