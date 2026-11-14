package com.haziz.lamha

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.*

fun fmtDur(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }
fun fmtSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f غ.ب".format(b / (1024.0 * 1024 * 1024))
    b >= 1L shl 20 -> "%.1f م.ب".format(b / (1024.0 * 1024))
    else -> "%d ك.ب".format(b / 1024)
}

fun groupByDay(items: List<Media>): List<Any> {
    val tz = TimeZone.getDefault()
    fun day(t: Long) = (t + tz.getOffset(t)) / 86400000L
    val today = day(System.currentTimeMillis())
    val fmt = SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar"))
    val out = ArrayList<Any>(items.size + 64)
    var last = Long.MIN_VALUE
    for (m in items) {
        val d = day(m.date)
        if (d != last) {
            last = d
            out.add(when (today - d) { 0L -> "اليوم"; 1L -> "أمس"; else -> fmt.format(Date(m.date)) })
        }
        out.add(m)
    }
    return out
}

@Composable
fun Header(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = if (onBack == null) 20.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun EmptyState(text: String) {
    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Image, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** mode: 0 عادي، 1 الألبوم الخاص، 2 سلة المحذوفات */
@Composable
fun Browser(
    env: Env, title: String?, items: List<Media>, grouped: Boolean = true, loading: Boolean = false,
    emptyText: String = "لا توجد عناصر", state: LazyGridState = rememberLazyGridState(), bottomPad: Dp = 0.dp,
    onBack: (() -> Unit)? = null, albumName: String? = null, pick: String? = null, mode: Int = 0,
    headerActions: @Composable RowScope.() -> Unit = {}, headerExtra: @Composable () -> Unit = {}
) {
    var sel by remember { mutableStateOf(setOf<String>()) }
    var moveDlg by remember { mutableStateOf(false) }
    val chosen = items.filter { it.key in sel }
    val ops = env.ops
    BackHandler(sel.isNotEmpty()) { sel = emptySet() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (sel.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ sel = emptySet() }) { Icon(Icons.Rounded.Close, "إلغاء التحديد") }
                    Text("${sel.size} محدد", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (mode == 2) {
                        IconButton({ ops.trash(chosen, false); sel = emptySet() }) { Icon(Icons.Rounded.Restore, "استعادة") }
                        IconButton({ ops.deleteForever(chosen); sel = emptySet() }) { Icon(Icons.Rounded.DeleteForever, "حذف نهائي") }
                    } else if (pick == null) {
                        IconButton({ ops.share(chosen) }) { Icon(Icons.Rounded.Share, "مشاركة") }
                        val allFav = chosen.all { it.key in env.favs }
                        IconButton({ env.vm.toggleIn(K.fav, chosen.map { it.key }, !allFav); sel = emptySet() }) {
                            Icon(if (allFav) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite, "مفضلة")
                        }
                        if (mode == 0) {
                            IconButton({ moveDlg = true }) { Icon(Icons.Rounded.Folder, "نقل") }
                            IconButton({
                                env.vm.toggleIn(K.priv, chosen.map { it.key }, true)
                                ops.toast("نُقلت ${chosen.size} إلى الألبوم الخاص"); sel = emptySet()
                            }) { Icon(Icons.Rounded.VisibilityOff, "إخفاء") }
                            if (albumName != null && sel.size == 1) IconButton({ env.vm.setCover(albumName, chosen[0].key); ops.toast("تم تعيين الغلاف"); sel = emptySet() }) {
                                Icon(Icons.Rounded.Image, "تعيين كغلاف")
                            }
                        } else IconButton({ env.vm.toggleIn(K.priv, chosen.map { it.key }, false); sel = emptySet() }) { Icon(Icons.Rounded.Visibility, "إظهار") }
                        IconButton({ ops.trash(chosen); sel = emptySet() }) { Icon(Icons.Rounded.Delete, "حذف") }
                    }
                }
            } else if (title != null) Header(title, onBack, headerActions)
            headerExtra()
            Box(Modifier.weight(1f)) {
                when {
                    loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    items.isEmpty() -> EmptyState(emptyText)
                    else -> MediaGrid(items, grouped, env.favs, env.cols, sel, state, bottomPad,
                        onTap = { m ->
                            if (sel.isNotEmpty() || pick != null || mode == 2) sel = if (m.key in sel) sel - m.key else sel + m.key
                            else env.push(Screen.Viewer(items, items.indexOf(m)))
                        },
                        onLong = { m -> sel = if (m.key in sel) sel - m.key else sel + m.key })
                }
            }
        }
        if (pick != null && sel.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { ops.move(chosen, "Pictures/${pick.replace("/", " ")}/"); env.pop() },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp)
            ) { Text("إنشاء الألبوم ونقل ${sel.size}") }
        }
    }
    if (moveDlg) MoveDialog(env, { rel -> ops.move(chosen, rel); sel = emptySet(); moveDlg = false }, { moveDlg = false })
}

@Composable
fun MoveDialog(env: Env, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نقل إلى ألبوم") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("ألبوم جديد") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    items(env.albums, key = { it.name }) { a ->
                        Text(a.name, Modifier.fillMaxWidth().clickable { onPick(a.relPath) }.padding(vertical = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton({ onPick("Pictures/${name.trim().replace("/", " ")}/") }, enabled = name.isNotBlank()) { Text("إنشاء ونقل") } },
        dismissButton = { TextButton(onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun MediaGrid(
    items: List<Media>, grouped: Boolean, favs: Set<String>, cols: Int, sel: Set<String>,
    state: LazyGridState, bottomPad: Dp, onTap: (Media) -> Unit, onLong: (Media) -> Unit
) {
    val rows: List<Any> = remember(items, grouped) { if (grouped) groupByDay(items) else items }
    LazyVerticalGrid(
        GridCells.Fixed(cols), state = state,
        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(
            count = rows.size,
            key = { i -> rows[i].let { if (it is Media) it.key else "h$it" } },
            span = { i -> if (rows[i] is Media) GridItemSpan(1) else GridItemSpan(maxLineSpan) }
        ) { i ->
            when (val r = rows[i]) {
                is Media -> Cell(r, r.key in favs, r.key in sel, sel.isNotEmpty(), { onTap(r) }, { onLong(r) })
                else -> Text(r.toString(), Modifier.padding(start = 14.dp, top = 16.dp, bottom = 6.dp), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Cell(m: Media, fav: Boolean, selected: Boolean, anySel: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val sc by animateFloatAsState(if (selected) 0.86f else 1f, label = "sel")
    val req = remember(m.uri) { ImageRequest.Builder(ctx).data(m.uri).size(360).build() }
    Box(Modifier.aspectRatio(1f).combinedClickable(onClick = onTap, onLongClick = onLong)) {
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = sc; scaleY = sc }.clip(RoundedCornerShape(if (selected) 10.dp else 3.dp))) {
            AsyncImage(req, null, Modifier.fillMaxSize().background(cs.surfaceVariant), contentScale = ContentScale.Crop)
            if (m.video) Row(
                Modifier.align(Alignment.BottomStart).padding(4.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(12.dp), tint = Color.White)
                Text(fmtDur(m.duration), color = Color.White, fontSize = 10.sp)
            }
            if (fav) Icon(Icons.Rounded.Favorite, null, Modifier.align(Alignment.BottomEnd).padding(4.dp).size(13.dp), tint = Color.White)
            if (selected) Box(Modifier.fillMaxSize().background(cs.primary.copy(alpha = 0.25f)))
        }
        if (anySel) Icon(
            if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
            Modifier.align(Alignment.TopStart).padding(6.dp).size(20.dp), tint = if (selected) cs.primary else Color.White
        )
    }
}

fun filterSearch(all: List<Media>, q: String, f: Int, favs: Set<String>): List<Media> {
    val toks = q.trim().lowercase().split(" ").filter { it.isNotBlank() }
    if (toks.isEmpty() && f == 0) return emptyList()
    val ar = SimpleDateFormat("d MMMM yyyy EEEE", Locale("ar"))
    val en = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    return all.filter { m ->
        val ok = when (f) { 1 -> !m.video; 2 -> m.video; 3 -> m.isShot(); 4 -> m.key in favs; else -> true }
        ok && (toks.isEmpty() || run {
            val d = Date(m.date)
            val hay = m.name.lowercase() + " " + m.bucket.lowercase() + " " + ar.format(d) + " " + en.format(d)
            toks.all { t ->
                hay.contains(t) || (t.startsWith("فيديو") && m.video) || (t.startsWith("لقط") && m.isShot()) || ((t == "صورة" || t == "صور") && !m.video)
            }
        })
    }
}

@Composable
fun SearchScreen(env: Env, all: List<Media>) {
    var q by remember { mutableStateOf("") }
    var f by remember { mutableIntStateOf(0) }
    val res = remember(q, f, all, env.favs) { filterSearch(all, q, f, env.favs) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    Browser(
        env, null, res, onBack = env.pop,
        emptyText = if (q.isBlank() && f == 0) "ابحث بالاسم أو التاريخ أو الألبوم أو اكتب «فيديو» أو «لقطات»" else "لا توجد نتائج",
        headerExtra = {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(env.pop) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "رجوع") }
                    OutlinedTextField(
                        q, { q = it }, Modifier.weight(1f).focusRequester(fr), singleLine = true,
                        placeholder = { Text("ابحث في صورك") }, shape = RoundedCornerShape(26.dp),
                        leadingIcon = { Icon(Icons.Rounded.Search, null) }
                    )
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("الكل", "صور", "فيديو", "لقطات", "المفضلة").forEachIndexed { i, t -> FilterChip(f == i, { f = i }, { Text(t) }) }
                }
            }
        }
    )
}
