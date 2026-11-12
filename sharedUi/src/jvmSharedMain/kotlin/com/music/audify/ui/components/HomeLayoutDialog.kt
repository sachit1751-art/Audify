// Adapted from Zenith's GPL-3.0 Home layout editor.
// Original project: https://github.com/rezapranata089-bit/Zenith
// See THIRD_PARTY_NOTICES.md for attribution and licence terms.
package com.music.audify.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.audify.data.model.HOME_SLOT_MOODS
import com.music.audify.data.model.HomeLayout
import com.music.audify.data.model.HomeShelf
import com.music.audify.data.model.HomeSlot
import com.music.audify.data.model.homeShelfKey
import com.music.audify.ui.AppUi

@Composable
fun HomeLayoutDialog(
    shelves: List<HomeShelf>,
    showMoods: Boolean,
    onDismiss: () -> Unit,
) {
    val saved by AppUi.host.homeLayout.collectAsStateWithLifecycle()
    val defaults = remember(shelves, showMoods) {
        buildList {
            shelves.forEach { shelf ->
                add(homeShelfKey(shelf.title))
                if (showMoods && shelf.title.equals("Quick picks", ignoreCase = true)) add(HOME_SLOT_MOODS)
            }
            if (showMoods && HOME_SLOT_MOODS !in this) add(HOME_SLOT_MOODS)
        }.distinct()
    }
    val initialOrder = remember(saved, defaults) { saved.editorOrder(defaults) }
    var order by remember(initialOrder) { mutableStateOf(initialOrder) }
    var hidden by remember(saved, initialOrder) {
        mutableStateOf<Set<String>>(
            initialOrder.filterTo(mutableSetOf()) { saved.isHidden(it) },
        )
    }

    fun persist(nextOrder: List<String> = order, nextHidden: Set<String> = hidden) {
        val unseen = saved.slots.filter { it.key !in nextOrder }
        AppUi.host.setHomeLayout(HomeLayout(nextOrder.map { HomeSlot(it, it in nextHidden) } + unseen))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Home") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "Changes apply immediately. Move rows with the arrows, or uncheck a row to hide it from Home.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                order.forEachIndexed { index, key ->
                    val title = if (key == HOME_SLOT_MOODS) "Find Your Mood" else {
                        shelves.firstOrNull { homeShelfKey(it.title) == key }?.title
                            ?: key.removePrefix("shelf:").replace('-', ' ').replaceFirstChar { it.uppercase() }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.bodyMedium)
                            if (key == HOME_SLOT_MOODS) {
                                Text("Mood shortcuts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    val next = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                                    order = next
                                    persist(nextOrder = next)
                                }
                            },
                            enabled = index > 0,
                        ) { Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move $title up") }
                        IconButton(
                            onClick = {
                                if (index < order.lastIndex) {
                                    val next = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                                    order = next
                                    persist(nextOrder = next)
                                }
                            },
                            enabled = index < order.lastIndex,
                        ) { Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move $title down") }
                        Checkbox(
                            checked = key !in hidden,
                            onCheckedChange = { visible ->
                                val next = if (visible) hidden - key else hidden + key
                                hidden = next
                                persist(nextHidden = next)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = {
                order = defaults
                hidden = emptySet<String>()
                AppUi.host.setHomeLayout(HomeLayout())
            }) { Text("Reset") }
        },
    )
}
