package com.haziz.lamha

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Context.store by preferencesDataStore("lamha_prefs")

/** هوية المطور: ثوابت في مكان واحد وتُعرض في صفحة «عن المطور» والإعدادات. */
object Dev {
    const val NAME_AR = "أحمد الحاج (حزيز)"
    const val NAME_EN = "Ahmed Al-Haj (Haziz)"
    const val COPYRIGHT = "Copyright © 2026 Ahmed Al-Haj (Haziz). All Rights Reserved."
    const val VERSION = "1.0.0"
    const val RELEASE = "نوفمبر 2026"
    const val DEFAULT_BIO = "مطوّر تطبيقات أندرويد شغوف بتصميم تجارب عربية أنيقة وسريعة تحترم خصوصية المستخدم."
    const val MESSAGE = "صُمّم «لمحة» ليحفظ لحظاتك بجمال وخصوصية كاملة على جهازك، دون إرسال أي صورة إلى أي خادم."
}

object K {
    val theme = intPreferencesKey("theme")
    val cols = intPreferencesKey("cols")
    val sort = intPreferencesKey("album_sort")
    val resume = booleanPreferencesKey("resume_video")
    val fav = stringSetPreferencesKey("fav")
    val priv = stringSetPreferencesKey("priv")
    val pinned = stringSetPreferencesKey("pinned_albums")
    val hiddenAlbums = stringSetPreferencesKey("hidden_albums")
    val covers = stringSetPreferencesKey("covers")
    val bio = stringPreferencesKey("bio")
    val contact = stringPreferencesKey("contact")
    fun pos(key: String) = longPreferencesKey("pos_$key")
}

data class Media(
    val id: Long, val uri: Uri, val name: String, val date: Long, val size: Long,
    val video: Boolean, val duration: Long, val bucket: String, val path: String, val w: Int, val h: Int
) {
    val key: String get() = (if (video) "v" else "i") + id
    fun isShot() = path.contains("screenshot", true) || bucket.contains("screenshot", true) ||
        name.startsWith("Screenshot", true) || bucket.contains("لقطات")
}

data class Album(val name: String, val items: List<Media>, val relPath: String)

object Repo {
    fun load(ctx: Context, trashed: Boolean): List<Media> {
        val out = ArrayList<Media>()
        for (video in listOf(false, true)) {
            val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val cols = mutableListOf("_id", "_display_name", "datetaken", "date_added", "_size", "bucket_display_name", "relative_path", "width", "height")
            if (video) cols.add("duration")
            val args = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE)
            }
            try {
                ctx.contentResolver.query(base, cols.toTypedArray(), args, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val taken = c.getLong(2)
                        val added = c.getLong(3) * 1000
                        out.add(
                            Media(
                                id, ContentUris.withAppendedId(base, id), c.getString(1) ?: "",
                                if (taken > 0) taken else added, c.getLong(4), video,
                                if (video) c.getLong(9) else 0L, c.getString(5) ?: "غير مصنّف",
                                c.getString(6) ?: "", c.getInt(7), c.getInt(8)
                            )
                        )
                    }
                }
            } catch (e: Exception) { /* نتجاهل خطأ مجموعة واحدة ونكمل */ }
        }
        out.sortByDescending { it.date }
        return out
    }
}

fun destFor(m: Media, rel: String): String {
    val root = rel.substringBefore('/')
    val rest = rel.substringAfter('/', "")
    return when {
        m.video && root == "Pictures" -> "Movies/$rest"
        !m.video && root == "Movies" -> "Pictures/$rest"
        else -> rel
    }
}

class Vm(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    val media = MutableStateFlow<List<Media>>(emptyList())
    val trash = MutableStateFlow<List<Media>>(emptyList())
    val loading = MutableStateFlow(true)
    val prefs = ctx.store.data.stateIn(viewModelScope, SharingStarted.Eagerly, emptyPreferences())
    var pending: Pair<List<Media>, String>? = null

    fun reload() {
        viewModelScope.launch {
            loading.value = true
            val (a, t) = withContext(Dispatchers.IO) { Repo.load(ctx, false) to Repo.load(ctx, true) }
            media.value = a; trash.value = t; loading.value = false
        }
    }

    fun edit(block: MutablePreferences.() -> Unit) {
        viewModelScope.launch { ctx.store.edit { it.block() } }
    }

    fun toggleIn(key: Preferences.Key<Set<String>>, items: Collection<String>, add: Boolean) = edit {
        val cur = this[key] ?: emptySet()
        this[key] = if (add) cur + items else cur - items.toSet()
    }

    fun savePos(k: String, p: Long) = edit { this[K.pos(k)] = p }

    fun setCover(album: String, mediaKey: String) = edit {
        val cur = this[K.covers] ?: emptySet()
        this[K.covers] = cur.filterNot { it.startsWith("$album\u0001") }.toSet() + "$album\u0001$mediaKey"
    }

    /** يُستدعى بعد نتيجة نافذة النظام (حذف/سلة/كتابة). */
    fun afterWrite(ok: Boolean) {
        val p = pending; pending = null
        viewModelScope.launch {
            if (ok && p != null) withContext(Dispatchers.IO) {
                p.first.forEach { m ->
                    try {
                        val cv = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, destFor(m, p.second)) }
                        ctx.contentResolver.update(m.uri, cv, null, null)
                    } catch (e: Exception) { }
                }
            }
            reload()
        }
    }
}
