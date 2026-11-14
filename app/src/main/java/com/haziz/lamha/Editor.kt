package com.haziz.lamha

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Edit(
    val rot: Int = 0, val ratio: Float = 0f, val bright: Float = 0f, val contrast: Float = 1f,
    val sat: Float = 1f, val filter: Int = 0, val text: String = "", val textSize: Float = 0.07f
)

val FILTER_NAMES = listOf("الأصل", "أبيض وأسود", "سيبيا", "دافئ", "بارد", "حيوي", "باهت")

fun filterMatrix(i: Int): ColorMatrix? = when (i) {
    1 -> ColorMatrix().apply { setSaturation(0f) }
    2 -> ColorMatrix(floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
    3 -> ColorMatrix(floatArrayOf(1.1f, 0f, 0f, 0f, 8f, 0f, 1.0f, 0f, 0f, 0f, 0f, 0f, 0.9f, 0f, -8f, 0f, 0f, 0f, 1f, 0f))
    4 -> ColorMatrix(floatArrayOf(0.92f, 0f, 0f, 0f, -6f, 0f, 1.0f, 0f, 0f, 0f, 0f, 0f, 1.12f, 0f, 8f, 0f, 0f, 0f, 1f, 0f))
    5 -> ColorMatrix().apply { setSaturation(1.5f) }
    6 -> ColorMatrix(floatArrayOf(0.85f, 0f, 0f, 0f, 30f, 0f, 0.85f, 0f, 0f, 30f, 0f, 0f, 0.85f, 0f, 30f, 0f, 0f, 0f, 1f, 0f))
    else -> null
}

fun decode(ctx: Context, uri: Uri, max: Int): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val s = max.toFloat() / maxOf(info.size.width, info.size.height)
        if (s < 1f) d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
    }

fun render(src: Bitmap, e: Edit): Bitmap {
    var b = src
    if (e.rot % 4 != 0) {
        val m = Matrix().apply { postRotate(90f * (e.rot % 4)) }
        b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }
    if (e.ratio > 0f) {
        var w = b.width; var h = b.height
        if (w.toFloat() / h > e.ratio) w = (h * e.ratio).toInt() else h = (w / e.ratio).toInt()
        b = Bitmap.createBitmap(b, (b.width - w) / 2, (b.height - h) / 2, w.coerceAtLeast(1), h.coerceAtLeast(1))
    }
    val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val cm = ColorMatrix().apply { setSaturation(e.sat) }
    val t = 128f * (1 - e.contrast) + e.bright
    cm.postConcat(ColorMatrix(floatArrayOf(e.contrast, 0f, 0f, 0f, t, 0f, e.contrast, 0f, 0f, t, 0f, 0f, e.contrast, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    filterMatrix(e.filter)?.let { cm.postConcat(it) }
    p.colorFilter = ColorMatrixColorFilter(cm)
    c.drawBitmap(b, 0f, 0f, p)
    if (e.text.isNotBlank()) {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; textSize = out.width * e.textSize; textAlign = Paint.Align.CENTER
            isFakeBoldText = true; setShadowLayer(8f, 0f, 0f, android.graphics.Color.BLACK)
        }
        c.drawText(e.text, out.width / 2f, out.height * 0.9f, tp)
    }
    return out
}

@Composable
fun EditorScreen(env: Env, m: Media) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var src by remember { mutableStateOf<Bitmap?>(null) }
    var shown by remember { mutableStateOf<Bitmap?>(null) }
    var e by remember { mutableStateOf(Edit()) }
    val undo = remember { mutableStateListOf<Edit>() }
    val redo = remember { mutableStateListOf<Edit>() }
    var dragStart by remember { mutableStateOf<Edit?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(m) { src = withContext(Dispatchers.IO) { runCatching { decode(ctx, m.uri, 1400) }.getOrNull() } }
    LaunchedEffect(src, e) { val s = src; if (s != null) shown = withContext(Dispatchers.Default) { render(s, e) } }

    fun commit(n: Edit) { undo.add(e); redo.clear(); e = n }
    fun live(n: Edit) { if (dragStart == null) dragStart = e; e = n }
    fun finish() { dragStart?.let { undo.add(it); redo.clear() }; dragStart = null }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(env.pop) { Icon(Icons.Rounded.Close, "إغلاق") }
            Text("تعديل الصورة", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton({ redo.removeLastOrNull()?.let { r -> undo.add(e); e = r } }, enabled = redo.isNotEmpty()) { Icon(Icons.Rounded.Redo, "إعادة") }
            IconButton({ undo.removeLastOrNull()?.let { u -> redo.add(e); e = u } }, enabled = undo.isNotEmpty()) { Icon(Icons.Rounded.Undo, "تراجع") }
            Button(enabled = !saving && src != null, onClick = {
                saving = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            val out = render(decode(ctx, m.uri, 4096), e)
                            val cv = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, "Lamha_${System.currentTimeMillis()}.jpg")
                                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Lamha")
                            }
                            val u = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)!!
                            ctx.contentResolver.openOutputStream(u)!!.use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                        }.isSuccess
                    }
                    saving = false
                    env.ops.toast(if (ok) "حُفظت نسخة معدلة والأصل كما هو" else "تعذر الحفظ")
                    if (ok) { env.vm.reload(); env.pop() }
                }
            }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ نسخة") }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            val b = shown
            if (b == null) CircularProgressIndicator() else Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            when (tab) {
                0 -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ commit(e.copy(rot = (e.rot + 1) % 4)) }) { Icon(Icons.Rounded.RotateRight, "تدوير") }
                    listOf("الأصل" to 0f, "1:1" to 1f, "4:3" to 4f / 3, "3:4" to 3f / 4, "16:9" to 16f / 9).forEach { (t, r) ->
                        FilterChip(e.ratio == r, { commit(e.copy(ratio = r)) }, { Text(t) })
                    }
                }
                1 -> Column {
                    Text("الإضاءة"); Slider(e.bright, { live(e.copy(bright = it)) }, valueRange = -80f..80f, onValueChangeFinished = { finish() })
                    Text("التباين"); Slider(e.contrast, { live(e.copy(contrast = it)) }, valueRange = 0.6f..1.5f, onValueChangeFinished = { finish() })
                    Text("التشبع"); Slider(e.sat, { live(e.copy(sat = it)) }, valueRange = 0f..2f, onValueChangeFinished = { finish() })
                }
                2 -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FILTER_NAMES.forEachIndexed { i, n -> FilterChip(e.filter == i, { commit(e.copy(filter = i)) }, { Text(n) }) }
                }
                else -> Column {
                    OutlinedTextField(e.text, { live(e.copy(text = it)) }, Modifier.fillMaxWidth(), label = { Text("النص على الصورة") }, singleLine = true)
                    Slider(e.textSize, { live(e.copy(textSize = it)) }, valueRange = 0.03f..0.15f, onValueChangeFinished = { finish() })
                }
            }
        }
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            listOf("قص" to Icons.Rounded.Crop, "ضبط" to Icons.Rounded.Tune, "فلاتر" to Icons.Rounded.AutoAwesome, "نص" to Icons.Rounded.TextFields)
                .forEachIndexed { i, (l, ic) -> NavigationBarItem(tab == i, { tab = i }, { Icon(ic, l) }, label = { Text(l) }) }
        }
    }
}
