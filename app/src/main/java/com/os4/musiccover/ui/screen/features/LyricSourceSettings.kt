package com.os4.musiccover.ui.screen.features

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.os4.musiccover.R
import com.os4.musiccover.app.ModuleBridge
import com.os4.musiccover.lyrics.LyricSourcePolicy
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LyricSourceSettings(
    enabled: Boolean,
    module: ModuleBridge.State,
    onChange: (ModuleBridge.State) -> Unit,
) {
    val context = LocalContext.current
    val currentModule by rememberUpdatedState(module)
    var sourcesOpen by remember { mutableStateOf(false) }
    var formatsOpen by remember { mutableStateOf(false) }
    fun update(next: ModuleBridge.State) {
        onChange(next)
        ModuleBridge.setLyricSourcePolicy(context, next)
    }
    SwitchPreference(
        title = stringResource(R.string.lyrics_network),
        summary = stringResource(R.string.lyrics_network_summary),
        checked = module.lyricNetwork,
        enabled = enabled,
        onCheckedChange = { update(currentModule.copy(lyricNetwork = it)) },
    )
    BasicComponent(
        title = stringResource(R.string.lyrics_priority),
        summary = stringResource(R.string.lyrics_priority_summary),
        enabled = enabled,
        endActions = {
            IconButton(
                modifier = Modifier.size(48.dp),
                enabled = enabled,
                onClick = { sourcesOpen = true },
            ) {
                Icon(
                    imageVector = MiuixIcons.Sort,
                    contentDescription = stringResource(R.string.lyrics_priority),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
    )
    BasicComponent(
        title = stringResource(R.string.lyrics_format_priority),
        summary = stringResource(R.string.lyrics_format_summary),
        enabled = enabled,
        endActions = {
            IconButton(
                modifier = Modifier.size(48.dp),
                enabled = enabled,
                onClick = { formatsOpen = true },
            ) {
                Icon(
                    imageVector = MiuixIcons.Settings,
                    contentDescription = stringResource(R.string.lyrics_format_settings),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
    )
    WindowDialog(
        show = sourcesOpen,
        title = stringResource(R.string.lyrics_priority),
        summary = stringResource(R.string.lyrics_priority_summary),
        onDismissRequest = { sourcesOpen = false },
    ) {
        Column {
            PriorityRows(
                order = if (module.lyricSourceCustom) module.lyricSourceOrder
                    else LyricSourcePolicy.DEFAULT_SOURCES,
                enabled = enabled,
                label = { sourceLabel(it) },
                dimmed = { !module.lyricNetwork && LyricSourcePolicy.network(it) },
                onCommit = { update(currentModule.copy(lyricSourceCustom = true, lyricSourceOrder = it)) },
            )
            TextButton(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                text = stringResource(R.string.lyrics_priority_reset),
                enabled = enabled,
                onClick = {
                    update(currentModule.copy(lyricSourceCustom = false,
                        lyricSourceOrder = LyricSourcePolicy.DEFAULT_SOURCES))
                },
            )
            TextButton(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                text = stringResource(R.string.lyrics_priority_done),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = { sourcesOpen = false },
            )
        }
    }
    WindowDialog(
        show = formatsOpen,
        title = stringResource(R.string.lyrics_format_priority),
        summary = stringResource(R.string.lyrics_format_summary),
        onDismissRequest = { formatsOpen = false },
    ) {
        Column {
            PriorityRows(
                order = module.lyricFormatOrder,
                enabled = enabled,
                label = { it.uppercase() },
                onCommit = { update(currentModule.copy(lyricFormatOrder = it)) },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.lyrics_priority_reset),
                    enabled = enabled,
                    onClick = { update(currentModule.copy(lyricFormatOrder = LyricSourcePolicy.DEFAULT_FORMATS)) },
                )
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.lyrics_priority_done),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = { formatsOpen = false },
                )
            }
        }
    }
}

@Composable
private fun sourceLabel(source: String): String = stringResource(when (source) {
    "metadata" -> R.string.lyrics_priority_metadata
    "local" -> R.string.lyrics_priority_local
    "lyricon" -> R.string.lyrics_source_lyricon
    "amll" -> R.string.lyrics_source_amll
    "ttmlhub" -> R.string.lyrics_source_ttmlhub
    "qq" -> R.string.lyrics_source_qq
    "netease" -> R.string.lyrics_source_netease
    "kugou" -> R.string.lyrics_source_kugou
    "kuwo" -> R.string.lyrics_source_kuwo
    else -> R.string.lyrics_source_lrclib
})

@Composable
private fun PriorityRows(
    order: String,
    enabled: Boolean,
    label: @Composable (String) -> String,
    onCommit: (String) -> Unit,
    dimmed: (String) -> Boolean = { false },
) {
    val currentOrder by rememberUpdatedState(order)
    val commit by rememberUpdatedState(onCommit)
    val items = order.split(',')
    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
        Text(
            text = items.mapIndexed { index, source -> "${index + 1}. ${label(source)}" }
                .joinToString(" → "),
            modifier = Modifier.padding(16.dp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        // Keep controls in a stable order while their positions change beneath the finger.
        items.sorted().forEach { source ->
            key(source) {
                ValueSlider(
                    title = label(source) + if (dimmed(source))
                        " · " + stringResource(R.string.lyrics_priority_skipped) else "",
                    value = (items.indexOf(source) + 1).toFloat(),
                    valueRange = 1f..items.size.toFloat(),
                    enabled = enabled,
                    label = { it.roundToInt().toString() },
                    onValueChange = {
                        val next = LyricSourcePolicy.move(currentOrder, source, it.roundToInt())
                        if (next != currentOrder) commit(next)
                    },
                )
            }
        }
    }
}
