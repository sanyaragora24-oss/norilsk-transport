package com.example.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

private const val TAG = "SafePreferenceStores"

/**
 * Единственные экземпляры DataStore приложения (по одному на файл — иначе
 * DataStore падает), с защитой пользовательских данных:
 *  - повреждённый файл не удаляется молча: перед заменой на пустой он
 *    копируется рядом в `*.corrupt-<время>.bak`, чтобы избранное можно было
 *    восстановить;
 *  - ошибки чтения не роняют приложение (см. [safeData]).
 *
 * Имена файлов совпадают с прежними `preferencesDataStore(name = …)`, поэтому
 * сохранённые данные пользователей подхватываются без миграции.
 */
internal object SafePreferenceStores {
    private val lock = Any()
    private val stores = HashMap<String, DataStore<Preferences>>()

    fun get(context: Context, name: String): DataStore<Preferences> = synchronized(lock) {
        stores.getOrPut(name) {
            val app = context.applicationContext
            createSafePreferencesStore { app.preferencesDataStoreFile(name) }
        }
    }
}

internal fun createSafePreferencesStore(
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> File
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = ReplaceFileCorruptionHandler { error ->
        val backup = backupCorruptFile(produceFile())
        Log.w(TAG, "Corrupt preferences replaced; original kept at ${backup?.name}", error)
        emptyPreferences()
    },
    scope = scope,
    produceFile = produceFile
)

/** Копия повреждённого файла; храним не больше трёх последних. */
internal fun backupCorruptFile(file: File): File? = try {
    if (!file.isFile) {
        null
    } else {
        val dir = file.parentFile ?: return null
        val backup = File(dir, "${file.name}.corrupt-${System.currentTimeMillis()}.bak")
        file.copyTo(backup, overwrite = false)
        dir.listFiles { f -> f.name.startsWith("${file.name}.corrupt-") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(3)
            ?.forEach { it.delete() }
        backup
    }
} catch (e: Exception) {
    Log.w(TAG, "Could not back up corrupt preferences", e)
    null
}

/**
 * Поток настроек, который не роняет приложение при ошибке чтения диска:
 * экран получает пустые значения, а файл остаётся нетронутым.
 */
internal fun DataStore<Preferences>.safeData(): Flow<Preferences> = data.catch { error ->
    if (error is IOException) {
        Log.w(TAG, "Preferences read failed; showing defaults", error)
        emit(emptyPreferences())
    } else {
        throw error
    }
}

/**
 * Читает JSON-список из настроек. Если сохранённое значение не разбирается,
 * оно один раз копируется в резервный ключ `<key>_unreadable_backup` — следующая
 * запись не должна безвозвратно затереть группы или поездки пользователя.
 */
internal fun <T> MutablePreferences.decodeListPreservingUnreadable(
    key: Preferences.Key<String>,
    decode: (String) -> List<T>
): List<T> {
    val raw = this[key]
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        decode(raw)
    } catch (e: Exception) {
        val backupKey = stringPreferencesKey("${key.name}_unreadable_backup")
        if (this[backupKey] == null) this[backupKey] = raw
        Log.w(TAG, "Unreadable ${key.name}; original kept in ${backupKey.name}", e)
        emptyList()
    }
}
