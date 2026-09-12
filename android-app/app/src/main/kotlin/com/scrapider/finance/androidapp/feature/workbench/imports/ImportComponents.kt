package com.scrapider.finance.androidapp.feature.workbench.imports

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.scrapider.finance.androidapp.designsystem.LocalFinanceDimensions
import com.scrapider.finance.androidapp.designsystem.financeChromeColor
import com.scrapider.finance.androidapp.designsystem.LocalFinanceSpacing
import com.scrapider.finance.androidapp.designsystem.rememberFinanceSignalColors

@Composable
internal fun ImportActionBar(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(financeChromeColor()),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Button(
            onClick = onClick,
            enabled = enabled && !busy,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.md)
                .fillMaxWidth().heightIn(min = dimensions.controlHeight)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = spacing.sm).size(dimensions.iconSize),
                    color = rememberFinanceSignalColors().onNeutralContainer,
                    strokeWidth = dimensions.outlineWidth,
                )
            }
            Text(label)
        }
    }
}

@Composable
internal fun ImportActionRow(
    title: String,
    description: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spacing = LocalFinanceSpacing.current
    val dimensions = LocalFinanceDimensions.current
    Row(
        modifier = modifier.fillMaxWidth()
            .heightIn(min = dimensions.compactRowHeight)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(dimensions.iconSize))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = rememberFinanceSignalColors().onNeutralContainer)
        }
    }
}

@Composable
internal fun ImportHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(top = LocalFinanceSpacing.current.lg, bottom = LocalFinanceSpacing.current.sm)
            .semantics { heading() },
        style = MaterialTheme.typography.headlineSmall,
    )
}
