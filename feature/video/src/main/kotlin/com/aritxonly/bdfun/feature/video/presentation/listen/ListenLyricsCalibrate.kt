package com.aritxonly.bdfun.feature.video.presentation.listen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aritxonly.bdfun.core.domain.calibrateLyricsOffset
import com.aritxonly.bdfun.core.domain.lyricsOffsetLabel
import kotlinx.coroutines.delay

/**
 * 歌词校准条：微调整份歌词的时间轴，让导入的 lrc 和声音对上。
 *
 * 做成浮在歌词页底部的独立一层，而不是 Column 里的一行：它只在校准期间出现，
 * 塞进布局会把传输键和歌词区顶上去、退出时再弹回来，那就"影响到别的 UI"了。
 * 步进按钮支持按住连发，差几秒也能几下拖到位；点中间的数值直接归零。
 */
@Composable
internal fun LyricsCalibrateBar(
    offsetMs: Long,
    accent: Color,
    onOffset: (Long) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        StepChip("−0.5", -500L, offsetMs, onOffset)
        StepChip("−0.1", -100L, offsetMs, onOffset)
        OffsetReadout(offsetMs, accent, onReset = { onOffset(0L) })
        StepChip("+0.1", 100L, offsetMs, onOffset)
        StepChip("+0.5", 500L, offsetMs, onOffset)
        Text(
            text = "完成",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.26f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDone,
                )
                .padding(horizontal = 9.dp, vertical = 7.dp),
        )
    }
}

/** 一档步进：点一下走一格，按住 400ms 后连发。 */
@Composable
private fun StepChip(
    label: String,
    deltaMs: Long,
    currentMs: Long,
    onOffset: (Long) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 连发期间偏移一直在变，用最新值累加，不能锁住按下那一刻的旧值。
    val latestOffset by rememberUpdatedState(currentMs)
    val latestStep by rememberUpdatedState(onOffset)
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        delay(400)
        while (true) {
            latestStep(calibrateLyricsOffset(latestOffset, deltaMs))
            delay(80)
        }
    }
    Text(
        text = label,
        color = Color.White.copy(alpha = 0.86f),
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .clickable(interactionSource = interaction, indication = null) {
                onOffset(calibrateLyricsOffset(currentMs, deltaMs))
            }
            .padding(horizontal = 8.dp, vertical = 7.dp),
    )
}

/** 当前偏移读数；点一下归零，省掉一个「复位」按钮的位置。 */
@Composable
private fun OffsetReadout(offsetMs: Long, accent: Color, onReset: () -> Unit) {
    Text(
        text = lyricsOffsetLabel(offsetMs),
        color = if (offsetMs == 0L) Color.White.copy(alpha = 0.55f) else accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .width(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onReset,
            )
            .padding(vertical = 6.dp),
    )
}
