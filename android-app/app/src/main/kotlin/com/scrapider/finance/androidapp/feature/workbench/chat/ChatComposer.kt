package com.scrapider.finance.androidapp.feature.workbench.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.scrapider.finance.androidapp.R
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
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
    val secondary = rememberFinanceSignalColors().onNeutralContainer
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.padding(horizontal = spacing.xl, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (isSending) "可以先写下一问…" else "输入研究问题…", color = secondary) },
                minLines = 1,
                maxLines = COMPOSER_MAX_LINES,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                shape = MaterialTheme.shapes.large,
                textStyle = MaterialTheme.typography.bodyLarge,
                isError = draft.length > maxLength,
            )
            FilledIconButton(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.heightIn(min = dimensions.minTouchTarget).size(dimensions.minTouchTarget),
            ) {
                Icon(painterResource(R.drawable.ic_phosphor_arrow_right),
                    contentDescription = if (isSending) "等待当前回答完成后发送" else "发送问题",
                    modifier = Modifier.size(dimensions.iconSize).rotate(-90f))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = spacing.xl, end = spacing.xl, bottom = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text("回答仅供研究参考", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = secondary)
            Text("${draft.length}/$maxLength", style = MaterialTheme.typography.bodySmall,
                color = if (draft.length > maxLength) MaterialTheme.colorScheme.error else secondary)
        }
    }
}

private const val COMPOSER_MAX_LINES = 5
