package dev.tara.app

import android.content.Context
import android.os.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.tara.androidactions.AndroidEntityResolver
import dev.tara.androidactions.AndroidPlatformActions
import dev.tara.audio.AndroidSpeechController
import dev.tara.audio.SpeechController
import dev.tara.domain.*
import dev.tara.permissions.AndroidPermissionManager
import dev.tara.settingsdata.SettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TaraModule {
    @Provides @Singleton fun permissions(@ApplicationContext context: Context) = AndroidPermissionManager(context)
    @Provides fun permissionRepository(manager: AndroidPermissionManager): PermissionStatusRepository = manager
    @Provides @Singleton fun actions(@ApplicationContext context: Context) = AndroidPlatformActions(context)
    @Provides fun resolver(@ApplicationContext context: Context): EntityResolver = AndroidEntityResolver(context)
    @Provides fun parser(): CommandParser = DeterministicCommandParser()
    @Provides fun executor(actions: AndroidPlatformActions, permissions: PermissionStatusRepository) =
        ActionExecutor(ActionValidator(), ActionPolicy(permissions), ActionRegistry(actions))
    @Provides fun session(parser: CommandParser, resolver: EntityResolver, executor: ActionExecutor) =
        AssistantSessionManager(parser, resolver, executor, SystemClock::elapsedRealtime)
    @Provides fun speech(@ApplicationContext context: Context): SpeechController = AndroidSpeechController(context)
    @Provides @Singleton fun settings(@ApplicationContext context: Context) = SettingsRepository(context)
}
