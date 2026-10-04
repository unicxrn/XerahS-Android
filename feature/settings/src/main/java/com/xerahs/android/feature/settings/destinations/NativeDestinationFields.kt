package com.xerahs.android.feature.settings.destinations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.xerahs.android.core.domain.model.UploadDestination
import com.xerahs.android.core.ui.lumen.LumenSwitch

@Composable
fun NativeDestinationFields(
    destination: UploadDestination,
    values: Map<String, String>,
    onChange: (key: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NativeDestinationForms.fields(destination).forEach { field ->
            val value = values[field.key] ?: field.default
            if (field.kind == FormField.Kind.SWITCH) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(field.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    LumenSwitch(checked = value.toBoolean(), onCheckedChange = { onChange(field.key, it.toString()) })
                }
            } else {
                OutlinedTextField(
                    value = value,
                    onValueChange = { onChange(field.key, it) },
                    label = { Text(field.label) },
                    supportingText = field.help?.let { { Text(it) } },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    visualTransformation = if (field.kind == FormField.Kind.SECRET) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (field.kind) {
                            FormField.Kind.URL -> KeyboardType.Uri
                            FormField.Kind.SECRET -> KeyboardType.Password
                            else -> KeyboardType.Text
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
