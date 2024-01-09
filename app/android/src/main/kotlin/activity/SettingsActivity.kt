/*
 * Ani
 * Copyright (C) 2022-2024 Him188
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.him188.ani.android.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.AppTheme
import me.him188.ani.app.activity.BaseComponentActivity
import me.him188.ani.app.activity.CommonTopAppBar
import me.him188.ani.app.app.LocalAppSettingsManager
import me.him188.ani.app.i18n.LocalI18n
import me.him188.ani.app.ui.foundation.CommonAppScaffold
import me.him188.ani.app.ui.settings.ProxySettingsGroup
import me.him188.ani.app.ui.settings.SyncSettingsGroup
import moe.tlaster.precompose.flow.collectAsStateWithLifecycle

class SettingsActivity : BaseComponentActivity() {
    companion object {
        fun getIntent(context: Context): Intent = Intent(context, SettingsActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        setContent {
            MaterialTheme(currentColorScheme) {
                ImmerseStatusBar(AppTheme.colorScheme.primary)

                CommonAppScaffold(
                    topBar = {
                        CommonTopAppBar(
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.Default.ArrowBack, LocalI18n.current.getString("menu.back")
                                    )
                                }
                            },
                            title = {
                                Text(text = LocalI18n.current.getString("window.settings.title"))
                            },
                        )
                    },
                ) {
                    Box(modifier = Modifier.padding(vertical = 16.dp)) {
                        SettingsPage(snackbarHostState)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(snackbar: SnackbarHostState) {
    val manager = LocalAppSettingsManager.current
    val settings by manager.value.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProxySettingsGroup(
            settings,
            manager,
            disabledButtonText = { Text(LocalI18n.current.getString("preferences.proxy.mode.system")) },
            disabledContent = {
                Text(LocalI18n.current.getString("preferences.proxy.mode.system.content"))
            }
        )
        val i18n by rememberUpdatedState(LocalI18n.current)
        SyncSettingsGroup(settings, manager, onSaved = {
            scope.launch {
                snackbar.showSnackbar(
                    i18n.getString("preferences.sync.changes.apply.on.restart"),
                    withDismissAction = true
                )
            }
        })
    }
}
