package android.com.crisbud

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun PageHeading(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        val style = MaterialTheme.typography.displaySmall
        if (trailing != null && maxWidth / LocalDensity.current.fontScale < 305.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, Modifier.semantics { heading() }, style = style)
                trailing()
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f).semantics { heading() }, style = style)
                trailing?.invoke()
            }
        }
    }
}

@Composable
internal fun SectionHeading(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.semantics { contentDescription = "$actionLabel ${title.lowercase()}" }
            ) {
                if (actionLabel == "Add") {
                    Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(actionLabel)
                if (actionLabel != "Add") {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
internal fun RecordActions(
    label: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable (actions: @Composable () -> Unit) -> Unit
) {
    var expanded by remember(label) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().pointerInput(label) {
        val threshold = 44.dp.toPx()
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onDragEnd = { if (distance < -threshold) expanded = true }
        ) { change, drag ->
            change.consume()
            distance += drag
        }
    }) {
        content {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Rounded.MoreHoriz, "Actions for $label", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = MaterialTheme.shapes.medium) {
                    DropdownMenuItem(
                        text = { Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { expanded = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { expanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntrySheet(
    title: String,
    onDismiss: () -> Unit,
    actionLabel: String,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp), content = content
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Cancel") }
                Button(onClick = onSave, enabled = saveEnabled, modifier = Modifier.weight(1.5f).heightIn(min = 48.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChoiceField(
    label: String,
    value: String,
    options: List<String>,
    onClear: (() -> Unit)? = null,
    optionLabel: (String) -> String = { it },
    optionIcon: (@Composable (String) -> Unit)? = null,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && options.isNotEmpty() }) {
        OutlinedTextField(
            value = optionLabel(value), onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            leadingIcon = optionIcon?.let { icon -> { icon(value) } },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = MaterialTheme.shapes.medium
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onClear != null) {
                DropdownMenuItem(
                    text = { Text("No category") },
                    trailingIcon = { if (value.isEmpty()) Icon(Icons.Rounded.Check, null) },
                    onClick = {
                        onClear()
                        expanded = false
                    }
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    leadingIcon = optionIcon?.let { icon -> { icon(option) } },
                    trailingIcon = { if (option == value) Icon(Icons.Rounded.Check, null) },
                    modifier = Modifier.semantics { selected = option == value },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

@Composable
internal fun ConfirmDelete(label: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    ConfirmDestructiveAction(
        title = "Delete $label?",
        message = "This will permanently remove this item.",
        actionLabel = "Delete",
        onDismiss = onDismiss,
        onConfirm = onDelete
    )
}

@Composable
internal fun ConfirmDestructiveAction(
    title: String,
    message: String,
    actionLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DeleteOutline, null, tint = colors.error) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = colors.error, contentColor = colors.onError)
            ) {
                Text(actionLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
