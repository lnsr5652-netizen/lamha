package com.haziz.lamha

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ZoomImage(uri: Uri, onZoom: (Boolean) -> Unit, onTap: () -> Unit) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    fun clamp(o: Offset, s: Float): Offset {
        val mx = box.width * (s - 1) / 2f
        val my = box.height * (s - 1) / 2f
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }
    val req = remember(uri) { ImageRequest.Builder(ctx).data(uri).size(2048).build() }
    Box(
        Modifier.fillMaxSize().onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() }, onDoubleTap = {
                    if (scale > 1f) { scale = 1f; off = Offset.Zero } else scale = 2.5f
                    onZoom(scale > 1f)
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        if (ev.changes.size > 1 || scale > 1f) {
                            val z = ev.calculateZoom(); val p = ev.calculatePan()
                            scale = (scale * z).coerceIn(1f, 6f)
                            off = if (scale > 1f) clamp(off + p, scale) else Offset.Zero
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            onZoom(scale > 1f)
                        }
                    } while (ev.changes.any { it.pressed })
                }
            }
    ) {
        AsyncImage(
            req, null, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y },
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun Viewer(env: Env, list: List<Media>, start: Int) {
    val pager = rememberPagerState(start.coerceIn(0, (list.size - 1).coerceAtLeast(0))) { list.size }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Media?>(null) }
    LaunchedEffect(pager.currentPage) { zoomed = false }
    val cur = list[pager.currentPage.coerceIn(0, list.size - 1)]
    val bar = Color.Black.copy(alpha = 0.55f)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, userScrollEnabled = !zoomed, pageSpacing = 12.dp, key = { list[it].key }) { p ->
            val m = list[p]
            if (m.video) Box(Modifier.fillMaxSize().clickable { env.push(Screen.Player(m)) }, contentAlignment = Alignment.Center) {
                AsyncImage(m.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Icon(Icons.Rounded.PlayArrow, "تشغيل", Modifier.size(76.dp).clip(CircleShape).background(bar).padding(14.dp), tint = Color.White)
            } else ZoomImage(m.uri, { zoomed = it }, { chrome = !chrome })
        }
        AnimatedVisibility(chrome, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(Modifier.fillMaxWidth().background(bar).statusBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(env.pop) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع", tint = Color.White) }
                Text(
                    SimpleDateFormat("d MMMM yyyy • h:mm a", Locale("ar")).format(Date(cur.date)),
                    color = Color.White, modifier = Modifier.weight(1f)
                )
                Text("${pager.currentPage + 1}/${list.size}", color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(end = 12.dp))
            }
        }
        AnimatedVisibility(chrome, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(Modifier.fillMaxWidth().background(bar).navigationBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton({ env.ops.share(listOf(cur)) }) { Icon(Icons.Rounded.Share, "مشاركة", tint = Color.White) }
                val fav = cur.key in env.favs
                IconButton({ env.vm.toggleIn(K.fav, listOf(cur.key), !fav) }) {
                    Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "مفضلة", tint = if (fav) Color(0xFFFF6B81) else Color.White)
                }
                if (!cur.video) IconButton({ env.push(Screen.Editor(cur)) }) { Icon(Icons.Rounded.Edit, "تعديل", tint = Color.White) }
                IconButton({ info = cur }) { Icon(Icons.Rounded.Info, "معلومات", tint = Color.White) }
                IconButton({ env.ops.trash(listOf(cur)); env.pop() }) { Icon(Icons.Rounded.Delete, "حذف", tint = Color.White) }
            }
        }
    }
    info?.let { m ->
        AlertDialog(
            onDismissRequest = { info = null }, title = { Text("معلومات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("الاسم: ${m.name}")
                    Text("التاريخ: " + SimpleDateFormat("EEEE d MMMM yyyy، h:mm a", Locale("ar")).format(Date(m.date)))
                    Text("الحجم: ${fmtSize(m.size)}")
                    if (m.w > 0) Text("الأبعاد: ${m.w} × ${m.h}")
                    if (m.video) Text("المدة: ${fmtDur(m.duration)}")
                    Text("المجلد: ${m.path.ifBlank { m.bucket }}")
                }
            },
            confirmButton = { TextButton({ info = null }) { Text("إغلاق") } }
        )
    }
}

@Composable
fun PlayerScreen(env: Env, m: Media) {
    val act = LocalContext.current as Activity
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resume = env.prefs[K.resume] ?: true
    val saved = remember { env.prefs[K.pos(m.key)] ?: 0L }
    val player = remember {
        ExoPlayer.Builder(act).build().apply {
            setMediaItem(MediaItem.fromUri(m.uri)); prepare()
            if (resume && saved > 0 && saved < m.duration - 2000) seekTo(saved)
            playWhenReady = true
        }
    }
    var landscape by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val c = WindowCompat.getInsetsController(act.window, act.window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) player.pause() }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            env.vm.savePos(m.key, if (player.playbackState == Player.STATE_ENDED) 0L else player.currentPosition)
            player.release()
            c.show(WindowInsetsCompat.Type.systemBars())
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(8.dp)) {
            IconButton(env.pop, Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            IconButton({
                landscape = !landscape
                act.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }, Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)) { Icon(Icons.Rounded.ScreenRotation, "تدوير", tint = Color.White) }
        }
    }
}

@Composable
fun MemoryScreen(env: Env, items: List<Media>, title: String) {
    val ctx = LocalContext.current
    val pager = rememberPagerState { items.size }
    var playing by remember { mutableStateOf(true) }
    var mp by remember { mutableStateOf<MediaPlayer?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { mp?.release(); mp = MediaPlayer.create(ctx, uri)?.apply { isLooping = true; start() } }
    }
    DisposableEffect(Unit) { onDispose { mp?.release() } }
    LaunchedEffect(playing, pager.currentPage) {
        if (playing && items.size > 1) { delay(3000); pager.animateScrollToPage((pager.currentPage + 1) % items.size) }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { p ->
            val m = items[p]
            val req = remember(m.uri) { ImageRequest.Builder(ctx).data(m.uri).size(1600).build() }
            AsyncImage(req, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(env.pop) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع", tint = Color.White) }
            Text(title, color = Color.White, modifier = Modifier.weight(1f))
            IconButton({ pick.launch(arrayOf("audio/*")) }) { Icon(Icons.Rounded.MusicNote, "موسيقى", tint = Color.White) }
            IconButton({ playing = !playing }) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "تشغيل/إيقاف", tint = Color.White) }
        }
    }
}
