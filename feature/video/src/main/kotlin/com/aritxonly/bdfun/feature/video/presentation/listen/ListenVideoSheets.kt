package com.aritxonly.bdfun.feature.video.presentation.listen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aritxonly.bdfun.core.domain.AutoAudioQualityId
import com.aritxonly.bdfun.core.domain.PlayModeOption
import com.aritxonly.bdfun.core.domain.audioQualityDetail
import com.aritxonly.bdfun.core.domain.audioQualityLabel
import com.aritxonly.bdfun.core.domain.currentPlayMode
import com.aritxonly.bdfun.core.domain.playModeOptions
import com.aritxonly.bdfun.core.model.video.AudioQuality
import com.aritxonly.bdfun.core.model.video.VideoPage
import com.aritxonly.teak.common.modal.TeakActionSheet

/** 「更多」面板：只画当前真的可用的项，分组顺序与 [visibleListenMenuEntries] 一致。 */
@Composable
internal fun ListenVideoMoreSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    capabilities: ListenMenuCapabilities,
    state: ListenMenuState,
    accent: Color,
    onEntry: (ListenMenuEntry) -> Unit,
    sleepAtMs: Long = 0L,
    onSleepAtChanged: (Long) -> Unit = {},
    onPlayModePicked: (loopMode: Int, shuffle: Boolean) -> Unit = { _, _ -> },
    onLyricsStylePicked: (ListenLyricsStyle) -> Unit = {},
) {
    val entries = visibleListenMenuEntries(capabilities)
    // 这几项都在面板里就地展开，点一下只是展开选项，不会直接改值再收起面板。
    var expanded by remember { mutableStateOf<ListenMenuEntry?>(null) }
    TeakActionSheet(visible = visible, onDismiss = onDismiss, title = "更多") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            entries.forEach { entry ->
                ListenMenuRow(
                    entry = entry,
                    value = listenMenuValue(entry, state),
                    accent = accent,
                    onClick = {
                        when (entry) {
                            ListenMenuEntry.SLEEP_TIMER,
                            ListenMenuEntry.PLAY_MODE,
                            ListenMenuEntry.LYRICS_STYLE ->
                                expanded = if (expanded == entry) null else entry
                            else -> onEntry(entry)
                        }
                    },
                )
                when {
                    entry == ListenMenuEntry.SLEEP_TIMER && expanded == entry ->
                        SleepMinutesRow(sleepAtMs, accent, onSleepAtChanged)
                    entry == ListenMenuEntry.PLAY_MODE && expanded == entry ->
                        PlayModeOptions(state.loopMode, state.shuffle, accent) { option ->
                            onPlayModePicked(option.loopMode, option.shuffle)
                            onDismiss()
                        }
                    entry == ListenMenuEntry.LYRICS_STYLE && expanded == entry ->
                        InlineOptions(
                            options = ListenLyricsStyle.entries.map {
                                InlineOption(it.label, it.detail, it == state.lyricsStyle)
                            },
                            accent = accent,
                        ) { index ->
                            onLyricsStylePicked(ListenLyricsStyle.entries[index])
                            onDismiss()
                        }
                    else -> Unit
                }
            }
        }
    }
}

/** 播放模式四档：随机独占一档，选中即生效。 */
@Composable
private fun PlayModeOptions(
    loopMode: Int,
    shuffle: Boolean,
    accent: Color,
    onPick: (PlayModeOption) -> Unit,
) {
    val current = currentPlayMode(loopMode, shuffle)
    InlineOptions(
        options = playModeOptions.map { InlineOption(it.label, it.detail, it == current) },
        accent = accent,
    ) { index -> onPick(playModeOptions[index]) }
}

/** 面板内联展开的一行选项：label + 右侧说明，当前项高亮。 */
private data class InlineOption(val label: String, val detail: String, val selected: Boolean)

@Composable
private fun InlineOptions(options: List<InlineOption>, accent: Color, onPick: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        options.forEachIndexed { index, option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = if (option.selected) 0.10f else 0f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onPick(index) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = option.label,
                    color = if (option.selected) accent else Color.White.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    fontWeight = if (option.selected) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = option.detail,
                    color = Color.White.copy(alpha = 0.42f),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun SleepMinutesRow(currentAtMs: Long, accent: Color, onPick: (Long) -> Unit) {
    val options = listOf(0 to "关闭", 15 to "15 分钟", 30 to "30 分钟", 60 to "1 小时", 90 to "1.5 小时")
    val now = System.currentTimeMillis()
    val activeMinutes = if (currentAtMs > now) ((currentAtMs - now + 59_999L) / 60_000L).toInt() else 0
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (minutes, label) ->
            val selected = (minutes == 0 && activeMinutes == 0) || (minutes > 0 && activeMinutes in 1..minutes)
            Text(
                text = label,
                color = if (selected) accent else Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = if (selected) 0.18f else 0.07f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onPick(if (minutes == 0) 0L else now + minutes * 60_000L)
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun ListenMenuRow(
    entry: ListenMenuEntry,
    value: String?,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 13.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.title,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 播放队列：分 P / 合集条目，点一条切过去。 */
@Composable
internal fun ListenVideoQueueSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    pages: List<VideoPage>,
    currentCid: Long,
    accent: Color,
    onSelect: (Long) -> Unit,
) {
    TeakActionSheet(visible = visible, onDismiss = onDismiss, title = "播放列表") {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
            items(pages, key = { it.cid }) { page ->
                val current = page.cid == currentCid
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(page.cid) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = page.part,
                        color = if (current) accent else Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (current) {
                        Spacer(Modifier.height(0.dp))
                        Text("播放中", color = accent, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * 音质档位：听视频只出声音，这里列的是 dash.audio（64K / 132K / 192K / 杜比 / 无损），
 * 和 dash.video 的画质是两回事，选完立刻换音轨重起播。
 */
@Composable
internal fun ListenAudioQualitySheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    audios: List<AudioQuality>,
    selectedId: Int,
    accent: Color,
    onSelect: (Int) -> Unit,
) {
    TeakActionSheet(visible = visible, onDismiss = onDismiss, title = "音质") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            AudioQualityRow(
                label = "自动",
                detail = "跟随音源",
                selected = selectedId == AutoAudioQualityId,
                accent = accent,
                onClick = {
                    onSelect(AutoAudioQualityId)
                    onDismiss()
                },
            )
            audios.forEach { quality ->
                AudioQualityRow(
                    label = audioQualityLabel(quality),
                    detail = audioQualityDetail(quality),
                    selected = quality.id == selectedId,
                    accent = accent,
                    onClick = {
                        onSelect(quality.id)
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun AudioQualityRow(
    label: String,
    detail: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) accent else Color.White.copy(alpha = 0.88f),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}