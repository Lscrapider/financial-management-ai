package com.scrapider.finance.androidapp.feature.workbench.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.designsystem.rememberFinanceSignalColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KnowledgeChunkReader(chunk: KnowledgeChunk, onClose: () -> Unit) {
    val spacing = LocalFinanceSpacing.current
    val paragraphs = remember(chunk.text) { chunk.text.split(Regex("\\n\\s*\\n")) }
    ModalBottomSheet(onDismissRequest = onClose, containerColor = MaterialTheme.colorScheme.surface) {
        SelectionContainer {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = spacing.xl, end = spacing.xl, bottom = spacing.section),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                item(key = "source-title") {
                    Text(chunk.filename, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                }
                item(key = "source-context") {
                    Text("${chunk.sceneLabel} · 来源片段", style = MaterialTheme.typography.bodySmall,
                        color = rememberFinanceSignalColors().onNeutralContainer)
                    HorizontalDivider(Modifier.padding(top = spacing.md), color = MaterialTheme.colorScheme.outlineVariant)
                }
                itemsIndexed(paragraphs, key = { index, _ -> index }, contentType = { _, _ -> "source-paragraph" }) { _, text ->
                    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
