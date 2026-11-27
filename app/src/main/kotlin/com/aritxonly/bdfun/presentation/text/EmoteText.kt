package com.aritxonly.bdfun.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.InlineTextContent
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.aritxonly.bdfun.core.application.*

@Composable
internal fun EmoteText(content: EmoteTextContent, style: EmoteTextStyle, modifier: Modifier = Modifier,
    decorations: EmoteTextDecorations = EmoteTextDecorations(), onMentionClick: ((Long) -> Unit)? = null,
    /** 非空时正文里的「1:00」染色可点；动态/私信这类没有播放器的入口保持 null。 */
    onTimestampClick: ((Long) -> Unit)? = null) {
    val dependencies = LocalRichTextDependencies.current
    // 动态和独立评论页也会直接渲染正文，不应依赖先打开播放器或表情面板。
    androidx.compose.runtime.LaunchedEffect(dependencies.emotes) { dependencies.emotes.ensure() }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val linkColor = MiuixTheme.colorScheme.primary
    val text = content.text
    val biliTitles = rememberBiliLinkTitles(text)
    val map = remember(dependencies.emotes.byText, content.extraEmotes) {
        if (content.extraEmotes.isEmpty()) dependencies.emotes.byText else dependencies.emotes.byText + content.extraEmotes
    }
    val analysis = remember(text, map, content.mentions, onMentionClick != null, onTimestampClick != null) {
        analyzeRichText(text, map, content.mentions, onMentionClick != null, onTimestampClick != null)
    }
    if (analysis.plainText(decorations.trailingContent != null)) {
        PlainEmoteText(text, style, modifier)
        return
    }
    val inline = remember(map, text, style.fontSize) { mutableMapOf<String, InlineTextContent>() }
    val runtime = RichTextRenderContext(content, style, decorations,
        RichTextActions({ dependencies.openVideo(context, it) }, { uriHandler.openUri(it) }, onMentionClick,
            onTimestampClick),
        linkColor, biliTitles, analysis)
    val annotated = remember(map, text, content.prefix, style.prefixColor, style.prefixWeight,
        decorations.prefixBadge != null, decorations.prefixColon, style.fontSize, decorations.trailingContent != null,
        analysis.mentionList, style.mentionColor, style.replyTargetMentionColor, style.vipMentionColor,
        content.vipMentions, onMentionClick != null, onTimestampClick != null, analysis.hasUrl, linkColor, biliTitles) {
        buildRichText(runtime, inline)
    }
    AnnotatedEmoteText(annotated, inline, style, modifier)
}
