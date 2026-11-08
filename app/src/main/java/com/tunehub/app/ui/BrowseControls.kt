package com.tunehub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun BrowseTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
}

@Composable
internal fun BrowseSection(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        HorizontalDivider(Modifier.weight(1f), color = tuneColors.rule)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
internal fun BrowseChip(text: String, selected: Boolean? = null, onClick: () -> Unit) {
    val highlighted = selected == true
    val action = if (selected == null) Modifier.clickable(role = Role.Button, onClick = onClick)
    else Modifier.selectable(selected, role = Role.Tab, onClick = onClick)
    Box(
        Modifier.heightIn(min = 48.dp)
            .panel(RoundedCornerShape(10.dp), fill = if (highlighted) tuneColors.accent else tuneColors.surface)
            .then(action)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (highlighted) tuneColors.onAccent else tuneColors.text)
    }
}
