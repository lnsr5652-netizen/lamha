package com.haziz.lamha

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Calendar

class CardData(
    val title: String, val count: Int, val cover: Media?, val icon: ImageVector, val onClick: () -> Unit,
    val pinned: Boolean = false, val menu: List<Pair<String, () -> Unit>> = emptyList()
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(c: CardData) {
    var menu by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Box {
        Column(Modifier.clip(RoundedCornerShape(22.dp)).combinedClickable(onClick = c.onClick, onLongClick = { if (c.menu.isNotEmpty()) menu = true })) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(22.dp)).background(cs.surfaceVariant)) {
                if (c.cover != null) {
                    val req = remember(c.cover.uri) { ImageRequest.Builder(ctx).data(c.cover.uri).size(480).build() }
                    AsyncImage(req, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else Icon(c.icon, null, Modifier.align(Alignment.Center).size(42.dp), tint = cs.onSurfaceVariant)
                if (c.pinned) Icon(Icons.Rounded.Star, null, Modifier.align(Alignment.TopEnd).padding(10.dp).size(20.dp), tint = cs.secondary)
            }
            Text(c.title, Modifier.padding(top = 6.dp, start = 4.dp), fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("${c.count}", Modifier.padding(start = 4.dp, bottom = 4.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        DropdownMenu(menu, { menu = false }) {
            c.menu.forEach { (t, a) -> DropdownMenuItem({ Text(t) }, { menu = false; a() }) }
        }
    }
}

@Composable
fun CardsGrid(cards: List<CardData>, bottomPad: Dp, header: @Composable () -> Unit) {
    LazyVerticalGrid(
        GridCells.Fixed(2), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        items(cards, key = { it.title }) { AlbumCard(it) }
    }
}

@Composable
fun NameDialog(title: String, initial: String, ok: String, onOk: (String) -> Unit, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, singleLine = true) },
        confirmButton = { TextButton({ onOk(t.trim().replace("/", " ")) }, enabled = t.isNotBlank()) { Text(ok) } },
        dismissButton = { TextButton(onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun AlbumsTab(env: Env, visible: List<Media>, trashCount: Int, bottomPad: Dp) {
    val pinned = env.prefs[K.pinned] ?: emptySet()
    val hidden = env.prefs[K.hiddenAlbums] ?: emptySet()
    val covers = env.prefs[K.covers] ?: emptySet()
    val sort = env.prefs[K.sort] ?: 0
    var showHidden by remember { mutableStateOf(false) }
    var newDlg by remember { mutableStateOf(false) }
    var renameFor by remember { mutableStateOf<Album?>(null) }
    var sortMenu by remember { mutableStateOf(false) }

    val list = remember(env.albums, pinned, hidden, showHidden, sort) {
        val base = compareByDescending<Album> { it.name in pinned }
        val cmp = when (sort) {
            0 -> base.thenBy { it.name.lowercase() }
            1 -> base.thenByDescending { it.items.size }
            else -> base.thenByDescending { it.items.first().date }
        }
        env.albums.filter { showHidden || it.name !in hidden }.sortedWith(cmp)
    }
    val smart = listOf(
        Triple("الأحدث", "recent", Icons.Rounded.AutoAwesome), Triple("المفضلة", "fav", Icons.Rounded.Favorite),
        Triple("مقاطع الفيديو", "videos", Icons.Rounded.Videocam)
    ).map { (t, k, ic) ->
        val items = resolve(k, "", visible, env.favs)
        CardData(t, items.size, items.firstOrNull(), ic, { env.push(Screen.ListS(t, k)) })
    }
    val real = list.map { a ->
        val coverKey = covers.firstOrNull { it.startsWith("${a.name}\u0001") }?.substringAfter('\u0001')
        CardData(
            a.name, a.items.size, a.items.firstOrNull { it.key == coverKey } ?: a.items.first(), Icons.Rounded.Folder,
            { env.push(Screen.ListS(a.name, "album", a.name)) }, a.name in pinned,
            listOf(
                (if (a.name in pinned) "إلغاء التثبيت" else "تثبيت في الأعلى") to { env.vm.toggleIn(K.pinned, listOf(a.name), a.name !in pinned) },
                (if (a.name in hidden) "إظهار الألبوم" else "إخفاء الألبوم") to { env.vm.toggleIn(K.hiddenAlbums, listOf(a.name), a.name !in hidden) },
                "إعادة تسمية" to { renameFor = a }
            )
        )
    }
    val tail = listOf(
        CardData("الألبوم الخاص", env.priv.size, null, Icons.Rounded.Lock, { env.ops.auth { env.push(Screen.Private) } }),
        CardData("سلة المحذوفات", trashCount, null, Icons.Rounded.Delete, { env.push(Screen.Trash) })
    )
    CardsGrid(smart + real + tail, bottomPad) {
        Header("الألبومات") {
            IconButton({ newDlg = true }) { Icon(Icons.Rounded.Add, "ألبوم جديد") }
            Box {
                IconButton({ sortMenu = true }) { Icon(Icons.Rounded.Tune, "فرز") }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    listOf("بالاسم", "بعدد الصور", "بالأحدث").forEachIndexed { i, t ->
                        DropdownMenuItem({ Text(t) }, { env.vm.edit { this[K.sort] = i }; sortMenu = false }, leadingIcon = { if (sort == i) Icon(Icons.Rounded.Check, null) })
                    }
                }
            }
            IconButton({ showHidden = !showHidden }) { Icon(if (showHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, "المخفية") }
        }
    }
    if (newDlg) NameDialog("ألبوم جديد", "", "التالي", { newDlg = false; env.push(Screen.Pick(it)) }, { newDlg = false })
    renameFor?.let { a ->
        NameDialog("إعادة تسمية الألبوم", a.name, "حفظ", {
            env.ops.move(a.items, "${a.relPath.substringBefore('/')}/$it/"); renameFor = null
        }, { renameFor = null })
    }
}

fun memories(all: List<Media>): List<Pair<Int, List<Media>>> {
    val now = Calendar.getInstance()
    val md = now.get(Calendar.MONTH) * 100 + now.get(Calendar.DAY_OF_MONTH)
    val y = now.get(Calendar.YEAR)
    val c = Calendar.getInstance()
    return all.filter { c.timeInMillis = it.date; c.get(Calendar.MONTH) * 100 + c.get(Calendar.DAY_OF_MONTH) == md && c.get(Calendar.YEAR) < y }
        .groupBy { c.timeInMillis = it.date; y - c.get(Calendar.YEAR) }.toList().sortedBy { it.first }
}

@Composable
fun CollectionsTab(env: Env, visible: List<Media>, bottomPad: Dp) {
    val mem = remember(visible) { memories(visible) }
    val defs = listOf(
        Triple("لقطات الشاشة", "shots", Icons.Rounded.Image), Triple("التنزيلات", "downloads", Icons.Rounded.Download),
        Triple("من التطبيقات", "apps", Icons.Rounded.Share), Triple("مقاطع الفيديو", "videos", Icons.Rounded.Videocam),
        Triple("المفضلة", "fav", Icons.Rounded.Favorite), Triple("الأكبر حجماً", "large", Icons.Rounded.SdStorage)
    )
    val cards = defs.map { (t, k, ic) ->
        val items = resolve(k, "", visible, env.favs)
        CardData(t, items.size, items.firstOrNull(), ic, { env.push(Screen.ListS(t, k)) })
    }
    CardsGrid(cards, bottomPad) {
        Column {
            Header("المجموعات")
            Text("في مثل هذا اليوم", Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium)
            if (mem.isEmpty()) Text("ستظهر هنا ذكرياتك من السنوات السابقة عندما تتوفر صور في مثل هذا اليوم.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else LazyRow(contentPadding = PaddingValues(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(mem, key = { it.first }) { (years, list) ->
                    val label = if (years == 1) "قبل سنة" else "قبل $years سنوات"
                    Box(Modifier.width(220.dp).height(140.dp).clip(RoundedCornerShape(22.dp)).clickable { env.push(Screen.Memory(list, label)) }) {
                        AsyncImage(list.first().uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
                        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                            Text(label, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${list.size} عنصر", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                        }
                    }
                }
            }
            Text("مجموعات", Modifier.padding(start = 20.dp, top = 18.dp, bottom = 10.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun StudioRow(icon: ImageVector, title: String, sub: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun StudioTab(env: Env, all: List<Media>, trashCount: Int, bottomPad: Dp, onPrivate: () -> Unit) {
    val photos = all.count { !it.video }
    val videos = all.count { it.video }
    val total = all.sumOf { it.size }
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad)) {
        item { Header("الاستديو") }
        item {
            Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("$photos" to "صورة", "$videos" to "فيديو", fmtSize(total) to "المساحة").forEach { (v, l) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(v, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(l, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        item { StudioRow(Icons.Rounded.SdStorage, "الأكبر حجماً", "حرّر المساحة بحذف أكبر الملفات") { env.push(Screen.ListS("الأكبر حجماً", "large")) } }
        item { StudioRow(Icons.Rounded.Lock, "الألبوم الخاص", "${env.priv.size} عنصر • محمي ببصمتك أو قفل الشاشة", onPrivate) }
        item { StudioRow(Icons.Rounded.Delete, "سلة المحذوفات", "$trashCount عنصر قابل للاستعادة") { env.push(Screen.Trash) } }
        item { StudioRow(Icons.Rounded.Settings, "الإعدادات") { env.push(Screen.Settings) } }
        item { StudioRow(Icons.Rounded.Person, "عن المطور", Dev.NAME_AR) { env.push(Screen.About) } }
        item { Text(Dev.COPYRIGHT, Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun SettingsScreen(env: Env) {
    val theme = env.prefs[K.theme] ?: 0
    val resume = env.prefs[K.resume] ?: true
    val hidden = env.prefs[K.hiddenAlbums] ?: emptySet()
    Column(Modifier.fillMaxSize()) {
        Header("الإعدادات", env.pop)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("المظهر", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("النظام", "فاتح", "داكن").forEachIndexed { i, t -> FilterChip(theme == i, { env.vm.edit { this[K.theme] = i } }, { Text(t) }) }
            }
            Text("عرض الصور (أعمدة الشبكة)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 4, 5).forEach { n -> FilterChip(env.cols == n, { env.vm.edit { this[K.cols] = n } }, { Text("$n") }) }
            }
            Text("تشغيل الفيديو", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("استئناف المشاهدة من آخر موضع", Modifier.weight(1f))
                Switch(resume, { env.vm.edit { this[K.resume] = it } })
            }
            Text("إدارة الألبومات", style = MaterialTheme.typography.titleMedium)
            env.albums.sortedBy { it.name.lowercase() }.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${a.name}  (${a.items.size})", Modifier.weight(1f))
                    Switch(a.name !in hidden, { env.vm.toggleIn(K.hiddenAlbums, listOf(a.name), !it) })
                }
            }
            Text("الخصوصية", style = MaterialTheme.typography.titleMedium)
            Text("كل الصور تُعالج على جهازك فقط، والتطبيق لا يطلب إذن الإنترنت. يُحمى الألبوم الخاص ببصمتك أو قفل الشاشة، وتُمنع لقطات الشاشة داخله.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Text("الإصدار ${Dev.VERSION}", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 12.sp)
            Text(Dev.COPYRIGHT, Modifier.fillMaxWidth().padding(bottom = 32.dp), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AboutScreen(env: Env) {
    val bio = env.prefs[K.bio] ?: Dev.DEFAULT_BIO
    val contact = env.prefs[K.contact] ?: "ستُضاف معلومات التواصل قريباً"
    var editing by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize()) {
        Header("عن المطور", env.pop)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Text("لمحة", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("الإصدار ${Dev.VERSION} • ${Dev.RELEASE}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("المطوّر", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(Dev.NAME_AR, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(Dev.NAME_EN, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Divider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("نبذة", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        IconButton({ editing = 0 }) { Icon(Icons.Rounded.Edit, "تعديل") }
                    }
                    Text(bio)
                    Text("رسالة المطور", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(Dev.MESSAGE)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("التواصل", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        IconButton({ editing = 1 }) { Icon(Icons.Rounded.Edit, "تعديل") }
                    }
                    Text(contact)
                }
            }
            Text("حقوق الملكية الفكرية", style = MaterialTheme.typography.labelLarge)
            Text(Dev.COPYRIGHT, textAlign = TextAlign.Center, fontSize = 12.sp)
            Spacer(Modifier.height(32.dp))
        }
    }
    editing?.let { which ->
        var t by remember { mutableStateOf(if (which == 0) bio else contact) }
        AlertDialog(
            onDismissRequest = { editing = null }, title = { Text(if (which == 0) "تعديل النبذة" else "معلومات التواصل") },
            text = { OutlinedTextField(t, { t = it }, minLines = 3) },
            confirmButton = { TextButton({ env.vm.edit { this[if (which == 0) K.bio else K.contact] = t }; editing = null }) { Text("حفظ") } },
            dismissButton = { TextButton({ editing = null }) { Text("إلغاء") } }
        )
    }
}
