package com.haziz.lamha

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Coil.setImageLoader(
            ImageLoader.Builder(this).components { add(VideoFrameDecoder.Factory()) }.crossfade(true).build()
        )
        setContent { LamhaApp() }
    }
}

sealed class Screen {
    data class Viewer(val list: List<Media>, val index: Int) : Screen()
    data class Player(val m: Media) : Screen()
    data class Editor(val m: Media) : Screen()
    data class ListS(val title: String, val kind: String, val arg: String = "") : Screen()
    data class Pick(val name: String) : Screen()
    data class Memory(val items: List<Media>, val title: String) : Screen()
    object Search : Screen()
    object Trash : Screen()
    object Private : Screen()
    object Settings : Screen()
    object About : Screen()
}

class Env(
    val vm: Vm, val ops: Ops, val prefs: Preferences, val albums: List<Album>,
    val push: (Screen) -> Unit, val pop: () -> Unit
) {
    val favs: Set<String> get() = prefs[K.fav] ?: emptySet()
    val priv: Set<String> get() = prefs[K.priv] ?: emptySet()
    val cols: Int get() = prefs[K.cols] ?: 4
}

class Ops(
    val act: ComponentActivity, val vm: Vm,
    private val launcher: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>
) {
    private fun go(pi: PendingIntent) = launcher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
    fun toast(s: String) = Toast.makeText(act, s, Toast.LENGTH_SHORT).show()
    fun trash(items: List<Media>, on: Boolean = true) {
        if (items.isNotEmpty()) go(MediaStore.createTrashRequest(act.contentResolver, items.map { it.uri }, on))
    }
    fun deleteForever(items: List<Media>) {
        if (items.isNotEmpty()) go(MediaStore.createDeleteRequest(act.contentResolver, items.map { it.uri }))
    }
    fun move(items: List<Media>, rel: String) {
        if (items.isEmpty()) return
        vm.pending = items to rel
        go(MediaStore.createWriteRequest(act.contentResolver, items.map { it.uri }))
    }
    fun share(items: List<Media>) {
        if (items.isEmpty()) return
        val uris = ArrayList(items.map { it.uri })
        val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        i.type = when { items.all { !it.video } -> "image/*"; items.all { it.video } -> "video/*"; else -> "*/*" }
        val clip = ClipData.newRawUri("", uris[0]); uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        i.clipData = clip
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        act.startActivity(Intent.createChooser(i, "مشاركة"))
    }
    /** قفل الألبوم الخاص: بصمة الجهاز أو رمز القفل. */
    fun auth(onOk: () -> Unit) {
        val bp = BiometricPrompt.Builder(act).setTitle("الألبوم الخاص").setSubtitle("أكّد هويتك للمتابعة")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        bp.authenticate(CancellationSignal(), act.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { onOk() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL) toast("فعّل قفل الشاشة أولاً لحماية الألبوم الخاص")
            }
        })
    }
}

@Composable
fun LamhaApp(vm: Vm = viewModel()) {
    val prefs by vm.prefs.collectAsState()
    val dark = when (prefs[K.theme] ?: 0) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    LamhaTheme(dark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Gate(vm, prefs) }
        }
    }
}

@Composable
fun Gate(vm: Vm, prefs: Preferences) {
    val act = LocalContext.current as ComponentActivity
    val perms = remember {
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    var granted by remember { mutableStateOf(perms.all { ContextCompat.checkSelfPermission(act, it) == PackageManager.PERMISSION_GRANTED }) }
    val permL = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> granted = r.values.all { it } }
    LaunchedEffect(granted) { if (granted) vm.reload() }
    if (granted) Root(vm, act, prefs) else Column(
        Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("مرحباً بك في لمحة", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("نحتاج إذن قراءة الصور والفيديوهات لعرضها. تبقى كل ملفاتك على جهازك ولا تُرسل إلى أي جهة.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { permL.launch(perms) }) { Text("السماح بالوصول") }
    }
}

fun resolve(kind: String, arg: String, all: List<Media>, favs: Set<String>): List<Media> = when (kind) {
    "album" -> all.filter { it.bucket == arg }
    "fav" -> all.filter { it.key in favs }
    "videos" -> all.filter { it.video }
    "shots" -> all.filter { it.isShot() }
    "downloads" -> all.filter { it.path.startsWith("Download", true) }
    "apps" -> all.filter { m -> APPS.any { a -> m.bucket.contains(a, true) || m.path.contains(a, true) } }
    "recent" -> { val t = System.currentTimeMillis() - 30L * 86400000; all.filter { it.date >= t } }
    "large" -> all.sortedByDescending { it.size }.take(60)
    else -> emptyList()
}
val APPS = listOf("WhatsApp", "Telegram", "Instagram", "Messenger", "Snapchat", "Viber", "Signal", "Facebook", "TikTok", "Twitter")

@Composable
fun Root(vm: Vm, act: ComponentActivity, prefs: Preferences) {
    val all by vm.media.collectAsState()
    val trash by vm.trash.collectAsState()
    val loading by vm.loading.collectAsState()
    val priv = prefs[K.priv] ?: emptySet()
    val visible = remember(all, priv) { all.filter { it.key !in priv } }
    val albums = remember(visible) { visible.groupBy { it.bucket }.map { (n, l) -> Album(n, l, l.first().path) } }
    val stack = remember { mutableStateListOf<Screen>() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        vm.afterWrite(r.resultCode == Activity.RESULT_OK)
    }
    val ops = remember { Ops(act, vm, launcher) }
    val env = Env(vm, ops, prefs, albums, { stack.add(it) }, { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) })
    BackHandler(stack.isNotEmpty()) { env.pop() }

    AnimatedContent(
        targetState = stack.lastOrNull(),
        transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f)) togetherWith fadeOut(tween(150)) },
        label = "nav"
    ) { top ->
        if (top == null) {
            val shown = remember(visible, filter) { when (filter) { 1 -> visible.filter { !it.video }; 2 -> visible.filter { it.video }; else -> visible } }
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> Browser(
                        env, "الصور", shown, loading = loading, state = gridState, bottomPad = 110.dp,
                        emptyText = "لا توجد صور أو فيديوهات على هذا الجهاز",
                        headerActions = {
                            IconButton({ env.push(Screen.Search) }) { Icon(Icons.Rounded.Search, "بحث") }
                            IconButton({ vm.edit { this[K.cols] = if (env.cols >= 5) 3 else env.cols + 1 } }) { Icon(Icons.Rounded.Apps, "تغيير العرض") }
                        },
                        headerExtra = {
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("الكل", "الصور", "الفيديو").forEachIndexed { i, t -> FilterChip(filter == i, { filter = i }, { Text(t) }) }
                            }
                        }
                    )
                    1 -> AlbumsTab(env, visible, trash.size, 110.dp)
                    2 -> CollectionsTab(env, visible, 110.dp)
                    else -> StudioTab(env, all, trash.size, 110.dp, onPrivate = { ops.auth { env.push(Screen.Private) } })
                }
                NavBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter))
            }
        } else when (top) {
            is Screen.Viewer -> Viewer(env, top.list, top.index)
            is Screen.Player -> PlayerScreen(env, top.m)
            is Screen.Editor -> EditorScreen(env, top.m)
            is Screen.ListS -> Browser(
                env, top.title, resolve(top.kind, top.arg, visible, env.favs), onBack = env.pop,
                grouped = top.kind != "large", albumName = if (top.kind == "album") top.arg else null,
                emptyText = "لا توجد عناصر هنا بعد"
            )
            is Screen.Pick -> Browser(env, "اختر صوراً للألبوم «${top.name}»", visible, onBack = env.pop, pick = top.name)
            is Screen.Memory -> MemoryScreen(env, top.items, top.title)
            Screen.Search -> SearchScreen(env, visible)
            Screen.Trash -> Browser(env, "سلة المحذوفات", trash, onBack = env.pop, mode = 2, emptyText = "السلة فارغة. تُحذف العناصر تلقائياً بعد 30 يوماً")
            Screen.Private -> {
                DisposableEffect(Unit) {
                    act.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    onDispose { act.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
                }
                Browser(env, "الألبوم الخاص", all.filter { it.key in priv }, onBack = env.pop, mode = 1, emptyText = "الألبوم الخاص فارغ. حدّد صوراً ثم اختر «إخفاء»")
            }
            Screen.Settings -> SettingsScreen(env)
            Screen.About -> AboutScreen(env)
        }
    }
}

@Composable
fun NavBar(tab: Int, onTab: (Int) -> Unit, modifier: Modifier) {
    val items = listOf("الصور" to Icons.Rounded.PhotoLibrary, "الألبومات" to Icons.Rounded.Folder, "المجموعات" to Icons.Rounded.AutoAwesome, "الاستديو" to Icons.Rounded.Tune)
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        shape = RoundedCornerShape(30.dp), color = cs.surface.copy(alpha = 0.93f), shadowElevation = 14.dp
    ) {
        Row(Modifier.padding(6.dp)) {
            items.forEachIndexed { i, (label, icon) ->
                val sel = tab == i
                val bg by animateColorAsState(if (sel) cs.primary.copy(alpha = 0.15f) else Color.Transparent, label = "nb")
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(bg).clickable { onTab(i) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(icon, label, tint = if (sel) cs.primary else cs.onSurfaceVariant)
                    Text(label, fontSize = 11.sp, color = if (sel) cs.primary else cs.onSurfaceVariant)
                }
            }
        }
    }
}
