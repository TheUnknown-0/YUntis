package sh.lsx.untisx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sh.lsx.untisx.ui.theme.SUBJECT_PALETTE
import sh.lsx.untisx.ui.util.Haptics
import sh.lsx.untisx.ui.util.tapHaptic

@Composable
fun ColorPickerDialog(
    title: String,
    currentColor: Int?,
    allowUseDefault: Boolean,
    onPick: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var hex by remember(title) { mutableStateOf("") }
    var hexError by remember(title) { mutableStateOf(false) }

    var useDefault by remember(title) { mutableStateOf(currentColor == null) }

    fun applyHex() {
        val cleaned = hex.trim().removePrefix("#")

        val argb = runCatching {
            require(cleaned.length == 6)
            require(cleaned.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' })
            (0xFF000000L or cleaned.toLong(16)).toInt()
        }.getOrNull()

        if (argb == null) {
            hexError = true
        } else {
            onPick(argb)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                if (allowUseDefault) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Use default color",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )

                        Switch(
                            checked = useDefault,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    onPick(null)
                                } else {
                                    useDefault = false
                                }
                            },
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.height(170.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(SUBJECT_PALETTE) { color ->
                        val argb = color.toArgb()
                        val selected = currentColor == argb

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(color, CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .alpha(if (useDefault) 0.35f else 1f)
                                .tapHaptic(!useDefault) { onPick(argb) },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = hex,
                    onValueChange = {
                        hex = it
                        hexError = false
                    },
                    label = { Text("Custom hex") },
                    placeholder = { Text("#RRGGBB") },
                    singleLine = true,
                    isError = hexError,
                    enabled = !useDefault,
                    supportingText = if (hexError) {
                        { Text("Invalid hex color.") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = Haptics.haptic(::applyHex), enabled = !useDefault) {
                Text("Apply")
            }
        },
        dismissButton = {
            Row {
                if (!allowUseDefault) {
                    TextButton(onClick = Haptics.haptic { onPick(null) }) {
                        Text("Reset")
                    }
                }

                TextButton(onClick = Haptics.haptic(onDismiss)) {
                    Text("Cancel")
                }
            }
        },
    )
}
