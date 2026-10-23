package dev.voice.offline.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

class AssistantRoleManager(context: Context) {
    private val roles = context.getSystemService(RoleManager::class.java)
    val isAvailable: Boolean get() = roles?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true
    val isSelected: Boolean get() = isAvailable && roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)

    fun requestIntent(): Intent = if (isAvailable) {
        roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
    } else settingsIntent()

    fun settingsIntent(): Intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
}
