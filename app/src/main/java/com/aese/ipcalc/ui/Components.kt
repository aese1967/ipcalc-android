package com.aese.ipcalc.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
            }
            content()
        }
    }
}

/** Fila etiqueta/valor; al tocarla copia el valor al portapapeles. */
@Composable
fun ResultRow(label: String, value: String, mono: Boolean = true) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable {
                clipboard.setText(AnnotatedString(value))
                Toast.makeText(ctx, "Copiado: $value", Toast.LENGTH_SHORT).show()
            }
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                label,
                modifier = Modifier.weight(0.42f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                modifier = Modifier.weight(0.58f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                    fontWeight = FontWeight.Medium,
                ),
                textAlign = TextAlign.End,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
fun MonoText(text: String, modifier: Modifier = Modifier, size: Int = 12) {
    Text(text, modifier = modifier, fontFamily = FontFamily.Monospace, fontSize = size.sp)
}

@Composable
fun InputField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    numeric: Boolean = false,
    error: String? = null,
    onDone: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Uri,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
