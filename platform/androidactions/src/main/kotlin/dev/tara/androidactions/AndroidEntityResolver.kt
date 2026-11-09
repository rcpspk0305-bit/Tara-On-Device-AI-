package dev.tara.androidactions

import android.content.Context
import android.content.Intent
import dev.tara.domain.EntityResolver
import dev.tara.domain.TargetOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidEntityResolver(context: Context) : EntityResolver {
    private val application = context.applicationContext

    override suspend fun apps(name: String): List<TargetOption> = withContext(Dispatchers.IO) {
        val query = name.trim()
        if (query.isEmpty()) return@withContext emptyList()
        selectAppTargets(launchableApps(application), query)
    }

    // Contacts are granted one selected URI by Android's phone-number picker, never scanned wholesale.
    override suspend fun contacts(name: String): List<TargetOption> = emptyList()
}

internal fun launchableApps(context: Context): List<TargetOption> {
    val manager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return manager.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled && it.activityInfo.exported }
        .map { TargetOption(it.activityInfo.packageName, it.loadLabel(manager).toString()) }
        .distinctBy { it.id }
}
