package com.scrapider.finance.androidapp.feature.workbench.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.financeChromeColor
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.designsystem.rememberFinanceSignalColors

@Composable
internal fun ChatComposer(
    draft: String,
    canSend: Boolean,
    isSending: Boolean,
    maxLength: Int,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    val colors = MaterialTheme.colorScheme
    val secondary = rememberFinanceSignalColors().onNeutralContainer
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val overLimit = draft.length > maxLength
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = financeChromeColor(),
        contentColor = colors.onSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = spacing.xl, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.Bottom,
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large,
                    color = colors.surface,
                    contentColor = colors.onSurface,
                    border = BorderStroke(dimensions.outlineWidth, when {
                        overLimit -> colors.error
                        focused -> colors.primary
                        else -> colors.outlineVariant
                    }),
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChanged,
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(min = dimensions.minTouchTarget)
                            .semantics {
                                contentDescription = "研究问题"
                                if (overLimit) error("消息不能超过 $maxLength 个字符")
                            },
                        interactionSource = interactionSource,
                        minLines = 1,
                        maxLines = COMPOSER_MAX_LINES,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default,
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.primary),
                        decorationBox = { innerTextField ->
                            Box(
                                Modifier.padding(spacing.md),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (draft.isEmpty()) Text(
                                    if (isSending) "可以先写下一问…" else "输入研究问题…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = secondary,
                                )
                                innerTextField()
                            }
                        },
                    )
                }
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier
                        .size(dimensions.minTouchTarget)
                        .clip(CircleShape)
                        .background(if (canSend) colors.primary else colors.outlineVariant)
                        .semantics {
                            contentDescription = if (isSending) "等待当前回答完成后发送" else "发送问题"
                        },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phosphor_paper_plane_tilt_fill),
                        contentDescription = null,
                        modifier = Modifier.size(dimensions.iconSize),
                        tint = if (canSend) colors.onPrimary else secondary,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    if (overLimit) "消息不能超过 $maxLength 个字符" else "回答仅供研究参考",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overLimit) colors.error else secondary,
                )
                if (draft.isNotEmpty()) Text(
                    "${draft.length}/$maxLength",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overLimit) colors.error else secondary,
                )
            }
        }
    }
}

private const val COMPOSER_MAX_LINES = 5
