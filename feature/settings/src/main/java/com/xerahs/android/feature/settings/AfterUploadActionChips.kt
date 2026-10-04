package com.xerahs.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.domain.model.AfterUploadAction
import com.xerahs.android.core.ui.lumen.Lumen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AfterUploadActionChips(
    selected: Set<AfterUploadAction>,
    onToggle: (AfterUploadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AfterUploadAction.entries.forEach { action ->
            val checked = action in selected
            FilterChip(
                selected = checked,
                onClick = { onToggle(action) },
                label = { Text(action.label) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Lumen.tokens.tint,
                    selectedLabelColor = Lumen.tokens.ink
                ),
                border = if (!checked) {
                    FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = Lumen.tokens.hairline,
                        borderWidth = 1.dp
                    )
                } else null
            )
        }
    }
}
