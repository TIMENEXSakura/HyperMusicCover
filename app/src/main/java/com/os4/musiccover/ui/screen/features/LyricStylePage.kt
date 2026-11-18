package com.os4.musiccover.ui.screen.features

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.os4.musiccover.R
import com.os4.musiccover.app.ModuleBridge
import com.os4.musiccover.app.SettingsStore
import com.os4.musiccover.lyrics.HdrDisplay
import com.os4.musiccover.lyrics.LyricAppearance
import com.os4.musiccover.ui.component.SettingsNotices
import com.os4.musiccover.ui.util.FoldUnder
import com.os4.musiccover.ui.util.PageScaffold
import com.os4.musiccover.ui.util.nextCard
import kotlin.math.roundToInt
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** The lock-screen sample selects one voice. Its controls edit the same state the renderer reads. */
@Composable
internal fun LyricStylePageView(
    isBlurEnabled: Boolean,
    refreshKey: Int,
    extraBottomPadding: Dp = 0.dp,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // The app's copy first once it is the source (stage 5.3); see FeaturesPage.
    var module by remember { mutableStateOf(ModuleBridge.initialState()) }
    val migrated by SettingsStore.migratedState.collectAsState()
    val lost by ModuleBridge.lost.collectAsState()
    LaunchedEffect(lost) {
        if (lost > 0) {
            module = module.copy(alive = false)
        }
    }
    LaunchedEffect(refreshKey, lost) {
        val snapshot = ModuleBridge.queryAlive(context)
        if (snapshot.alive || SettingsStore.editable()) module = SettingsStore.overlay(snapshot)
    }
    // Editable on the app's copy alone once it is the source; see FeaturesPage.
    val canSave = module.alive || migrated
    val appearance = remember(module.lyricAppearance) { LyricAppearance.parse(module.lyricAppearance) }

    // One field or several of the appearance, written through LyricAppearance so what is shown
    // is what the module will hold.
    fun changeAppearance(vararg values: Pair<String, Any>, persist: Boolean = true) {
        val json = JSONObject(LyricAppearance.parse(module.lyricAppearance).toJson())
        for ((k, v) in values) json.put(k, v)
        val next = LyricAppearance.parse(json.toString()).toJson()
        module = module.copy(lyricAppearance = next)
        if (persist && canSave) ModuleBridge.setLyricAppearance(context, next)
    }

    // Everything on this page back to what the module ships with.
    fun reset() {
        val json = LyricAppearance.DEFAULT.toJson()
        module = module.copy(
            lyricsAlign = 0,
            lyricSizeSp = DEFAULT_SIZE,
            lyricBgSizeSp = DEFAULT_BG_SIZE,
            lyricTransSizeSp = DEFAULT_TRANS_SIZE,
            lyricWeight = DEFAULT_WEIGHT,
            lyricBgWeight = DEFAULT_WEIGHT,
            lyricTransWeight = DEFAULT_WEIGHT,
            lyricsTrans = true,
            lyricsRoma = false,
            lyricsHdr = false,
            lyricAppearance = json,
        )
        if (!canSave) return
        ModuleBridge.setLyricsAlign(context, 0)
        ModuleBridge.setLyricStyle(context, "size", DEFAULT_SIZE)
        ModuleBridge.setLyricStyle(context, "bgsize", DEFAULT_BG_SIZE)
        ModuleBridge.setLyricStyle(context, "transsize", DEFAULT_TRANS_SIZE)
        ModuleBridge.setLyricWeight(context, "weight", DEFAULT_WEIGHT)
        ModuleBridge.setLyricWeight(context, "bgweight", DEFAULT_WEIGHT)
        ModuleBridge.setLyricWeight(context, "transweight", DEFAULT_WEIGHT)
        ModuleBridge.setLyricsTrans(context, true)
        ModuleBridge.setLyricsRoma(context, false)
        ModuleBridge.setLyricsHdr(context, false)
        ModuleBridge.setLyricAppearance(context, json)
    }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    // When both secondary labels are hidden, the live editor belongs to the lead again.
    val voice = if (selected == 1 && !module.lyricsTrans && !module.lyricsRoma) 0 else selected
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var draftArgb by rememberSaveable { mutableIntStateOf(-1) }
    val draft = Color(draftArgb)
    var more by rememberSaveable { mutableStateOf(false) }
    fun openPicker(role: Int, ink: Color) {
        selected = role
        draftArgb = ink.toArgb()
        picking = voiceKey(role)
    }
    PageScaffold(
        title = stringResource(R.string.lyrics_style_title),
        isBlurEnabled = isBlurEnabled,
        extraBottomPadding = extraBottomPadding,
        onBack = onBack,
        actions = {
            IconButton(onClick = { confirmReset = true }, holdDownState = confirmReset) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.lyrics_style_reset),
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        },
    ) {
        item(key = "preview") {
            LyricStylePreview(module, appearance, voice, if (picking != null) draft else null, onPick = ::openPicker)
        }
        item(key = "status") {
            Column {
                if (!canSave) {
                    Card(modifier = Modifier.nextCard()) {
                        BasicComponent(summary = stringResource(R.string.lyric_editor_preview_only))
                    }
                }
                SettingsNotices(alive = module.alive)
            }
        }
        item(key = "voices") {
            val translating = voice == 1
            val backing = voice == 2
            val role = voiceKey(voice)
            val mode = when {
                backing -> appearance.bgMode
                translating -> appearance.transMode
                else -> appearance.mainMode
            }
            val rgb = when {
                backing -> appearance.bgColor
                translating -> appearance.transColor
                else -> appearance.mainColor
            }
            val opacity = when {
                backing -> appearance.bgOpacity
                translating -> appearance.transOpacity
                else -> appearance.mainOpacity
            }
            Column {
                SmallTitle(text = stringResource(voiceLabel(voice)))
                Card(modifier = Modifier.nextCard()) {
                    // The dropdown, and beside it the custom colour as a swatch that reopens the
                    // picker. miuix's dropdown row takes nothing at its end, so the swatch is its
                    // neighbour rather than part of it.
                    val custom = mode == LyricAppearance.CUSTOM
                    val pickCustom = { openPicker(voice, Color(rgb).copy(alpha = opacity)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WindowDropdownPreference(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.lyrics_color),
                            summary = if (mode == LyricAppearance.COVER) {
                                stringResource(R.string.lyrics_color_cover_summary)
                            } else {
                                null
                            },
                            items = listOf(
                                stringResource(R.string.lyrics_color_official),
                                stringResource(R.string.lyrics_color_cover),
                                stringResource(R.string.lyrics_color_custom),
                                stringResource(R.string.lyrics_color_system),
                            ),
                            selectedIndex = mode,
                            onSelectedIndexChange = {
                                if (it == LyricAppearance.CUSTOM) pickCustom()
                                else changeAppearance("${role}Mode" to it)
                            },
                        )
                        if (custom) {
                            val label = stringResource(R.string.lyric_editor_adjust_color, stringResource(voiceLabel(voice)))
                            Box(
                                Modifier.padding(end = 8.dp).size(48.dp)
                                    .clickable(role = Role.Button, onClick = pickCustom)
                                    .semantics { contentDescription = label },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(24.dp).background(Color(rgb), CircleShape))
                            }
                        }
                    }
                    if (mode == LyricAppearance.COVER || mode == LyricAppearance.SYSTEM) {
                        val intensity = when (voice) {
                            1 -> appearance.transIntensity
                            2 -> appearance.bgIntensity
                            else -> appearance.mainIntensity
                        }
                        ValueSlider(
                            enabled = true,
                            title = stringResource(R.string.lyrics_color_intensity),
                            value = intensity * 100f,
                            valueRange = 0f..100f,
                            detent = LyricAppearance.DEFAULT_INTENSITY * 100f,
                            label = { "${it.roundToInt()}%" },
                            onValueChange = {
                                changeAppearance("${role}Intensity" to it.roundToInt() / 100.0, persist = false)
                            },
                            onValueChangeFinished = { if (canSave) ModuleBridge.setLyricAppearance(context, module.lyricAppearance) },
                        )
                    }
                    ValueSlider(
                        enabled = true,
                        title = stringResource(R.string.lyrics_opacity),
                        value = opacity * 100f,
                        valueRange = 0f..100f,
                        detent = (when {
                            backing -> DEFAULT_BG_OPACITY
                            translating -> LyricAppearance.DEFAULT_TRANS_OPACITY
                            else -> 1f
                        }) * 100f,
                        label = { "${it.roundToInt()}%" },
                        onValueChange = { changeAppearance("${role}Opacity" to it.roundToInt() / 100.0, persist = false) },
                        onValueChangeFinished = { if (canSave) ModuleBridge.setLyricAppearance(context, module.lyricAppearance) },
                    )
                    when (voice) {
                        1 -> ValueSlider(
                            enabled = true,
                            title = stringResource(R.string.lyrics_size),
                            value = module.lyricTransSizeSp,
                            valueRange = 10f..30f,
                            detent = DEFAULT_TRANS_SIZE,
                            label = { "${it.roundToInt()} sp" },
                            onValueChange = { module = module.copy(lyricTransSizeSp = it.roundToInt().toFloat()) },
                            onValueChangeFinished = {
                                if (canSave) ModuleBridge.setLyricStyle(context, "transsize", module.lyricTransSizeSp)
                            },
                        )
                        2 -> ValueSlider(
                            enabled = true,
                            title = stringResource(R.string.lyrics_size),
                            value = module.lyricBgSizeSp,
                            valueRange = 10f..30f,
                            detent = DEFAULT_BG_SIZE,
                            label = { "${it.roundToInt()} sp" },
                            onValueChange = { module = module.copy(lyricBgSizeSp = it.roundToInt().toFloat()) },
                            onValueChangeFinished = {
                                if (canSave) ModuleBridge.setLyricStyle(context, "bgsize", module.lyricBgSizeSp)
                            },
                        )
                        else -> ValueSlider(
                            enabled = true,
                            title = stringResource(R.string.lyrics_size),
                            value = module.lyricSizeSp,
                            valueRange = 18f..36f,
                            detent = DEFAULT_SIZE,
                            label = { "${it.roundToInt()} sp" },
                            onValueChange = { module = module.copy(lyricSizeSp = it.roundToInt().toFloat()) },
                            onValueChangeFinished = {
                                if (canSave) ModuleBridge.setLyricStyle(context, "size", module.lyricSizeSp)
                            },
                        )
                    }
                    // The same voice's weight, on the font's own steps. A weight moves every
                    // glyph's width, so it lays the lines out again as a size does - sent on
                    // release for the same reason.
                    val weight = when (voice) {
                        1 -> module.lyricTransWeight
                        2 -> module.lyricBgWeight
                        else -> module.lyricWeight
                    }
                    ValueSlider(
                        enabled = true,
                        title = stringResource(R.string.lyrics_weight),
                        value = weight.toFloat(),
                        valueRange = 300f..700f,
                        keyPoints = WEIGHT_STEPS,
                        label = { "${roundWeight(it)}" },
                        onValueChange = {
                            val w = roundWeight(it)
                            module = when (voice) {
                                1 -> module.copy(lyricTransWeight = w)
                                2 -> module.copy(lyricBgWeight = w)
                                else -> module.copy(lyricWeight = w)
                            }
                        },
                        onValueChangeFinished = {
                            val key = when (voice) {
                                1 -> "transweight"
                                2 -> "bgweight"
                                else -> "weight"
                            }
                            val latest = when (voice) {
                                1 -> module.lyricTransWeight
                                2 -> module.lyricBgWeight
                                else -> module.lyricWeight
                            }
                            if (canSave) ModuleBridge.setLyricWeight(context, key, latest)
                        },
                    )
                }
            }
        }
        item(key = "general") {
            Column {
                SmallTitle(text = stringResource(R.string.lyrics_style_general))
                Card(modifier = Modifier.nextCard()) {
                    val alignModes = listOf(
                        stringResource(R.string.lyrics_align_left),
                        stringResource(R.string.lyrics_align_center),
                        stringResource(R.string.lyrics_align_right),
                    )
                    WindowDropdownPreference(
                        title = stringResource(R.string.lyrics_align),
                        items = alignModes,
                        selectedIndex = module.lyricsAlign.coerceIn(0, alignModes.lastIndex),
                        onSelectedIndexChange = {
                            module = module.copy(lyricsAlign = it)
                            if (canSave) ModuleBridge.setLyricsAlign(context, it)
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.lyrics_trans),
                        checked = module.lyricsTrans,
                        onCheckedChange = {
                            module = module.copy(lyricsTrans = it)
                            if (canSave) ModuleBridge.setLyricsTrans(context, it)
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.lyrics_roma),
                        checked = module.lyricsRoma,
                        onCheckedChange = {
                            module = module.copy(lyricsRoma = it)
                            if (canSave) ModuleBridge.setLyricsRoma(context, it)
                        },
                    )
                    // A screen with no HDR headroom gets nothing from the highlight and would still
                    // pay for its window; SystemUI ignores the switch there, and it is shown off here.
                    val hdrScreen = remember { HdrDisplay.supported(context) }
                    SwitchPreference(
                        title = stringResource(R.string.lyrics_hdr),
                        // No summary on a screen that can use it: the window it costs was measured
                        // and paid for (see LyricWindow). The one without headroom still says why.
                        summary = if (hdrScreen) null else stringResource(R.string.lyrics_hdr_unsupported),
                        checked = module.lyricsHdr && hdrScreen,
                        enabled = hdrScreen,
                        onCheckedChange = {
                            module = module.copy(lyricsHdr = it)
                            if (canSave) ModuleBridge.setLyricsHdr(context, it)
                        },
                    )
                }
            }
        }
        item(key = "playback-options") {
            Card(modifier = Modifier.nextCard()) {
                BasicComponent(
                    title = stringResource(R.string.lyric_editor_more),
                    onClick = { more = !more },
                    endActions = {
                        Icon(
                            imageVector = if (more) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = stringResource(if (more) R.string.lyric_editor_collapse else R.string.lyric_editor_expand),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    },
                )
                FoldUnder(more) {
                    Column { LyricPlaybackSettings(canSave, module) { module = it } }
                }
            }
        }
    }

    // Asked first: one tap in the corner would otherwise undo every choice on the page.
    WindowDialog(
        show = confirmReset,
        title = stringResource(R.string.lyrics_style_reset),
        summary = stringResource(R.string.lyric_editor_reset_summary),
        onDismissRequest = { confirmReset = false },
    ) {
        Row {
            TextButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.rules_cancel),
                onClick = { confirmReset = false },
            )
            Spacer(Modifier.width(12.dp))
            TextButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.lyrics_style_reset),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    reset()
                    confirmReset = false
                },
            )
        }
    }

    WindowDialog(
        show = picking != null,
        title = stringResource(R.string.lyric_editor_adjust_color, stringResource(voiceLabel(voice))),
        onDismissRequest = { picking = null },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // miuix's picker always has an alpha bar; it is the same value as the opacity row,
            // opened at it and written back to it.
            Box(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).background(Color(0xff20212a), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(voiceLabel(voice)), color = draft)
            }
            ColorPicker(color = draft, onColorChanged = { draftArgb = it.toArgb() }, showPreview = false)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.rules_cancel),
                    onClick = { picking = null },
                )
                Spacer(Modifier.width(12.dp))
                TextButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.lyrics_style_done),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        picking?.let {
                            changeAppearance(
                                "${it}Mode" to LyricAppearance.CUSTOM,
                                "${it}Color" to draft.copy(alpha = 1f).toArgb(),
                                "${it}Opacity" to (draft.alpha * 100f).roundToInt() / 100.0,
                            )
                        }
                        picking = null
                    },
                )
            }
        }
    }
}

/** The sizes the module ships with: LyricStyle.DEFAULT. */
private const val DEFAULT_SIZE = 25f
private const val DEFAULT_BG_SIZE = 17f
private const val DEFAULT_TRANS_SIZE = 15f
/** The weights, on the lock screen font's own axis: 300 thin .. 700 bold. */
private const val DEFAULT_WEIGHT = 500
private val WEIGHT_STEPS = listOf(300f, 400f, 500f, 600f, 700f)

/** The slider's position as the nearest hundred, which is the only value the font has. */
private fun roundWeight(value: Float): Int = (value / 100f).roundToInt() * 100
/** LyricAppearance.DEFAULT's backing vocal; the lead's is 1. */
private const val DEFAULT_BG_OPACITY = 0.72f
