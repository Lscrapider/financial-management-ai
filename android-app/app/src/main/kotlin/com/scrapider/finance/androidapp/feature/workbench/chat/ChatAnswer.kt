package com.scrapider.finance.androidapp.feature.workbench.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalDensity
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.designsystem.rememberFinanceSignalColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 原生文本阅读；不执行回答中的 HTML，也不加载远程图片。 */
@Composable
internal fun ChatAnswer(content: String, modifier: Modifier = Modifier) {
    val blocks by produceState<List<AnswerBlock>>(emptyList(), content) {
        value = withContext(Dispatchers.Default) { answerBlocks(content) }
    }
    val spacing = LocalFinanceSpacing.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        blocks.forEach { block ->
            when (block) {
                is AnswerBlock.Heading -> Text(
                    text = inlineAnswer(block.text),
                    modifier = Modifier.padding(top = spacing.sm).semantics { heading() },
                    style = if (block.level == 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                is AnswerBlock.Paragraph -> Text(
                    text = inlineAnswer(block.text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                is AnswerBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(block.marker, style = MaterialTheme.typography.bodyLarge)
                    Text(inlineAnswer(block.text), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                }
                is AnswerBlock.Quote -> Text(
                    inlineAnswer(block.text),
                    Modifier.padding(start = spacing.lg),
                    style = MaterialTheme.typography.bodyLarge,
                    color = rememberFinanceSignalColors().onNeutralContainer,
                )
                is AnswerBlock.Code -> Surface(
                    shape = MaterialTheme.shapes.small,
                    color = rememberFinanceSignalColors().neutralContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Text(block.text, Modifier.horizontalScroll(rememberScrollState()).padding(spacing.md),
                        style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                }
                is AnswerBlock.Table -> AnswerTable(block.rows)
                AnswerBlock.Divider -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun AnswerTable(rows: List<List<String>>) {
    val spacing = LocalFinanceSpacing.current
    val cellWidth = LocalFinanceDimensions.current.minTouchTarget * TABLE_CELL_TOUCH_UNITS * LocalDensity.current.fontScale
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        rows.forEachIndexed { index, row ->
            Row {
                row.forEach { cell ->
                    Text(inlineAnswer(cell), Modifier.width(cellWidth).padding(horizontal = spacing.sm, vertical = spacing.md),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun inlineAnswer(text: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(text, linkColor) {
        // 未闭合的 Markdown 原样保留，完整强调和代码片段才应用样式。
        buildAnnotatedString {
            var offset = 0
            INLINE.findAll(text).forEach { match ->
                append(text.substring(offset, match.range.first))
                if (match.value.startsWith('[')) {
                    val separator = match.value.indexOf("](")
                    val url = match.value.substring(separator + 2, match.value.length - 1)
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) {
                        append(match.value.substring(1, separator))
                    }
                } else {
                    val code = match.value.startsWith('`')
                    val delimiter = if (code) 1 else 2
                    withStyle(if (code) SpanStyle(fontFamily = FontFamily.Monospace) else SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(match.value.substring(delimiter, match.value.length - delimiter))
                    }
                }
                offset = match.range.last + 1
            }
            append(text.substring(offset))
        }
    }
}

private sealed interface AnswerBlock {
    data class Heading(val level: Int, val text: String) : AnswerBlock
    data class Paragraph(val text: String) : AnswerBlock
    data class Bullet(val marker: String, val text: String) : AnswerBlock
    data class Quote(val text: String) : AnswerBlock
    data class Code(val text: String) : AnswerBlock
    data class Table(val rows: List<List<String>>) : AnswerBlock
    data object Divider : AnswerBlock
}

private fun answerBlocks(content: String): List<AnswerBlock> {
    val result = mutableListOf<AnswerBlock>()
    val lines = content.lines()
    var index = 0
    while (index < lines.size) {
        val line = lines[index].trim()
        val heading = HEADING.matchEntire(line)
        val bullet = BULLET.matchEntire(line)
        when {
            line.isEmpty() -> Unit
            line.startsWith("```") -> {
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) code += lines[index++]
                result += AnswerBlock.Code(code.joinToString("\n"))
            }
            index + 1 < lines.size && line.contains('|') && TABLE_RULE.matches(lines[index + 1].trim()) -> {
                val rows = mutableListOf(tableCells(line))
                index += 2
                while (index < lines.size && lines[index].isNotBlank() && lines[index].contains('|')) rows += tableCells(lines[index++])
                result += AnswerBlock.Table(rows)
                continue
            }
            heading != null -> result += AnswerBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            bullet != null -> result += AnswerBlock.Bullet(if (bullet.groupValues[1].first().isDigit()) bullet.groupValues[1] else "•", bullet.groupValues[2])
            line.startsWith("> ") -> result += AnswerBlock.Quote(line.removePrefix("> "))
            line == "---" || line == "***" -> result += AnswerBlock.Divider
            else -> result += AnswerBlock.Paragraph(line)
        }
        index++
    }
    return result
}

private fun tableCells(line: String): List<String> = line.trim().trim('|').split('|').map(String::trim)
private const val TABLE_CELL_TOUCH_UNITS = 3
private val HEADING = Regex("^(#{1,6})\\s+(.+)$")
private val BULLET = Regex("^([-*+] |[0-9]+[.)] )(.+)$")
private val TABLE_RULE = Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$")
private val INLINE = Regex("\\*\\*[^*]+\\*\\*|`[^`]+`|\\[[^\\]]+\\]\\(https?://[^)\\s]+\\)")
