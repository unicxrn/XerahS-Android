package com.xerahs.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.domain.model.AfterUploadAction

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AfterUploadActionChips(
    selected: Set<AfterUploadAction>,
    onToggle: (AfterUploadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AfterUploadAction.entries.forEach { action ->
            FilterChip(selected = action in selected, onClick = { onToggle(action) }, label = { Text(action.label) })
        }
    }
}
