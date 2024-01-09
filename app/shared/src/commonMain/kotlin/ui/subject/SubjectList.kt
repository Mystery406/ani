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

package me.him188.ani.app.ui.subject

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.kamel.image.asyncPainterResource
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.AniKamelImage
import me.him188.ani.app.ui.foundation.BrokenImagePlaceholder
import me.him188.ani.app.ui.foundation.LoadingIndicator
import me.him188.ani.app.ui.home.LocalContentPaddings
import moe.tlaster.precompose.flow.collectAsStateWithLifecycle

/**
 * 番剧预览列表, 双列模式
 */
@Composable
fun SubjectPreviewColumn(
    viewModel: SubjectListViewModel,
    modifier: Modifier = Modifier,
) {
    val items by viewModel.list.collectAsStateWithLifecycle()

    val state = rememberLazyGridState()
    val context by rememberUpdatedState(LocalContext.current)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier.background(MaterialTheme.colorScheme.background),
        state = state,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 用这个来让 (初始化时) 增加新的元素时, 保持滚动位置在最开始, 而不是到最后
        item("dummy", span = { GridItemSpan(maxLineSpan) }) {}

        items(items, key = { it.id }) { subject ->
            SubjectPreviewCard(
                title = remember(subject.id) {
                    subject.chineseName.takeIf { it.isNotBlank() } ?: subject.originalName
                },
                imageUrl = remember(subject.id) { subject.images.landscapeCommon },
                onClick = { viewModel.navigateToSubjectDetails(context, subject.id) },
                Modifier.animateItemPlacement().height(180.dp),
            )
        }

        item("loading", span = { GridItemSpan(maxLineSpan) }, contentType = "loading") {
            val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
            val loading by viewModel.loading.collectAsStateWithLifecycle()
            if (loading || hasMore) {
                LaunchedEffect(true) {
                    viewModel.loadMore()
                }
                Row(
                    Modifier.padding(vertical = 8.dp).fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 1.5.dp)
                    Text(
                        "加载中",
                        Modifier.height(IntrinsicSize.Max)
                            .padding(start = 8.dp)
                            .align(Alignment.CenterVertically)
                    )
                }
                return@item
//                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }

            // no more items 
        }

        item("footer") {
            Spacer(Modifier.fillMaxWidth().padding(bottom = LocalContentPaddings.current.calculateBottomPadding()))
        }
    }
}

/**
 * 一个番剧预览卡片, 一行显示两个的那种, 只有图片和名称
 */
@Composable
fun SubjectPreviewCard(
    title: String,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    ElevatedCard(
        modifier
            .shadow(2.dp, shape)
            .clip(shape)
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(),
                onClick = onClick,
            ),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            AniKamelImage(
                asyncPainterResource(imageUrl),
                Modifier.fillMaxWidth().height(120.dp).background(Color.LightGray),
                title,
                contentScale = ContentScale.Crop,
                onLoading = { LoadingIndicator(it) },
                onFailure = { BrokenImagePlaceholder() },
                animationSpec = tween(500),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                Modifier.padding(all = 8.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}
