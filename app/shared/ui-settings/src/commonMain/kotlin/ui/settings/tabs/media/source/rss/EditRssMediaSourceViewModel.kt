package me.him188.ani.app.ui.settings.tabs.media.source.rss

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import io.ktor.client.plugins.BrowserUserAgent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.SettingsRepository
import me.him188.ani.app.data.source.media.fetch.MediaSourceManager
import me.him188.ani.app.data.source.media.fetch.toClientProxyConfig
import me.him188.ani.app.data.source.media.fetch.updateMediaSourceArguments
import me.him188.ani.app.data.source.media.source.DefaultRssMediaSourceEngine
import me.him188.ani.app.data.source.media.source.RssMediaSourceArguments
import me.him188.ani.app.data.source.media.source.RssSearchConfig
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.tools.rss.RssParser
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.settings.tabs.media.source.rss.test.RssTestPaneState
import me.him188.ani.datasources.api.source.HttpMediaSource
import me.him188.ani.datasources.api.source.createHttpClient
import me.him188.ani.datasources.api.source.deserializeArgumentsOrNull
import me.him188.ani.utils.coroutines.onReplacement
import me.him188.ani.utils.ktor.proxy
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class EditRssMediaSourceViewModel(
    initialInstanceId: String,
) : AbstractViewModel(), KoinComponent {
    private val mediaSourceManager: MediaSourceManager by inject()
    private val settingsRepository: SettingsRepository by inject()

    private val instanceId: MutableStateFlow<String> = MutableStateFlow(initialInstanceId)

    private val arguments = this.instanceId.flatMapLatest { instanceId ->
        mediaSourceManager.instanceConfigFlow(instanceId).map {
            it?.deserializeArgumentsOrNull(
                RssMediaSourceArguments.serializer(),
            ) ?: RssMediaSourceArguments.Default
        }
    }

    val state: Flow<EditRssMediaSourceState> = this.instanceId.transformLatest { instanceId ->
        coroutineScope {
            val saveTasker = MonoTasker(this)
            val arguments = mutableStateOf<RssMediaSourceArguments?>(null)
            launch {
                val persisted = mediaSourceManager.instanceConfigFlow(instanceId).first()
                    ?.deserializeArgumentsOrNull(RssMediaSourceArguments.serializer())
                    ?: RssMediaSourceArguments.Default
                withContext(Dispatchers.Main) {
                    arguments.value = persisted
                }
            }
            emit(
                EditRssMediaSourceState(
                    argumentsStorage = SaveableStorage(
                        arguments,
                        onSave = {
                            arguments.value = it
                            saveTasker.launch {
                                kotlinx.coroutines.delay(500)
                                mediaSourceManager.updateMediaSourceArguments(
                                    instanceId,
                                    RssMediaSourceArguments.serializer(),
                                    it,
                                )
                            }
                        },
                        isSavingState = derivedStateOf { saveTasker.isRunning },
                    ),
                    instanceId = instanceId,
                ),
            )
        }
    }.flowOn(Dispatchers.Default)

    private val client = settingsRepository.proxySettings.flow.map {
        HttpMediaSource.createHttpClient {
            proxy(it.default.toClientProxyConfig())
            BrowserUserAgent()
        }
    }.onReplacement {
        it.close()
    }.shareInBackground(started = SharingStarted.Lazily)

    val testState: RssTestPaneState = RssTestPaneState(
        // 这里用的是序列化之后的配置, 也就是只有保存成功之后, 才会更新测试 (和触发重新查询)
        searchConfigState = arguments.map { it.searchConfig }.produceState(RssSearchConfig.Empty),
        engine = DefaultRssMediaSourceEngine(client, parser = RssParser(includeOrigin = true)),
        backgroundScope,
    )

    override fun onCleared() {
        super.onCleared()
        client.replayCache.firstOrNull()?.close()
    }
}