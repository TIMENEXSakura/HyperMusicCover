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
import com.os4.musiccover.lyrics.LyricAppearance
import com.os4.musiccover.ui.component.SettingsNotices
import com.os4.musiccover.ui.util.AppCard
import com.os4.musiccover.ui.util.AppDropdownPreference
import com.os4.musiccover.ui.util.AppDialog
import com.os4.musiccover.ui.util.AppDialogButton
import com.os4.musiccover.ui.util.PageScaffold
import com.os4.musiccover.ui.util.nextCard
import kotlin.math.roundToInt
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.BasicComponent
import com.os4.musiccover.ui.util.AppDialogButtonRole
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

    fun resetColors() {
        val defaults = LyricAppearance.DEFAULT
        changeAppearance(
            "mainMode" to defaults.mainMode, "mainColor" to defaults.mainColor, "mainIntensity" to defaults.mainIntensity,
            "bgMode" to defaults.bgMode, "bgColor" to defaults.bgColor, "bgIntensity" to defaults.bgIntensity,
            "duetMode" to defaults.duetMode, "duetColor" to defaults.duetColor, "duetIntensity" to defaults.duetIntensity,
            "transMode" to defaults.transMode, "transColor" to defaults.transColor, "transIntensity" to defaults.transIntensity,
        )
    }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(LyricEditorVoice.Lead) }
    val voice = when {
        selected == LyricEditorVoice.Duet && !appearance.duetEnabled -> LyricEditorVoice.Lead
        selected == LyricEditorVoice.Translation && !module.lyricsTrans && !module.lyricsRoma -> LyricEditorVoice.Lead
        else -> selected
    }
    var picking by rememberSaveable { mutableStateOf<LyricEditorVoice?>(null) }
    var draftArgb by rememberSaveable { mutableIntStateOf(-1) }
    val draft = Color(draftArgb)
    val showPicker = picking == voice
    val colour = appearance.voiceColour(voice)
    LaunchedEffect(voice) { if (picking != voice) picking = null }
    fun openPicker(role: LyricEditorVoice, ink: Color) {
        selected = role
        draftArgb = ink.toArgb()
        picking = role
    }
    val appearanceDialogs: @Composable () -> Unit = {
        // Asked first: one tap in the corner would otherwise undo every choice on the page.
        AppDialog(
            show = confirmReset,
            title = stringResource(R.string.lyrics_style_reset),
            summary = stringResource(R.string.lyric_editor_reset_summary),
            onDismissRequest = { confirmReset = false },
        ) {
            Row {
                AppDialogButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.rules_cancel),
                    onClick = { confirmReset = false },
                )
                Spacer(Modifier.width(12.dp))
                AppDialogButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.lyrics_style_reset),
                    role = AppDialogButtonRole.Primary,
                    onClick = {
                        resetColors()
                        confirmReset = false
                    },
                )
            }
        }

        AppDialog(
            show = showPicker,
            title = stringResource(R.string.lyric_editor_adjust_color, stringResource(voice.label)),
            onDismissRequest = { picking = null },
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // The picker's alpha is the opacity of this voice on the parent lyrics tab.
                Box(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp).background(Color(0xff20212a), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(voice.label), color = draft)
                }
                ColorPicker(color = draft, onColorChanged = { draftArgb = it.toArgb() }, showPreview = false)
                Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    AppDialogButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.rules_cancel),
                        onClick = { picking = null },
                    )
                    Spacer(Modifier.width(12.dp))
                    AppDialogButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.lyrics_style_done),
                        role = AppDialogButtonRole.Primary,
                        onClick = {
                            if (picking == voice) {
                                // Opening an existing colour rounds its alpha to an 8-bit swatch.
                                // Keep the original stored opacity unless that channel was edited.
                                val opacity = if ((draftArgb ushr 24) == (colour.opacity * 255f).roundToInt()) {
                                    colour.opacity.toDouble()
                                } else (draft.alpha * 100f).roundToInt() / 100.0
                                changeAppearance(
                                    "${voice.key}Mode" to LyricAppearance.CUSTOM,
                                    "${voice.key}Color" to draft.copy(alpha = 1f).toArgb(),
                                    "${voice.key}Opacity" to opacity,
                                )
                            }
                            picking = null
                        },
                    )
                }
            }
        }
    }

    PageScaffold(
        title = stringResource(R.string.lyrics_style_title),
        isBlurEnabled = isBlurEnabled,
        extraBottomPadding = extraBottomPadding,
        onBack = onBack,
        dialogs = appearanceDialogs,
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
            LyricStylePreview(module, appearance, voice, if (showPicker) draft else null, onPick = ::openPicker)
        }
        item(key = "status") {
            Column {
                if (!canSave) {
                    AppCard(modifier = Modifier.nextCard()) {
                        BasicComponent(summary = stringResource(R.string.lyric_editor_preview_only))
                    }
                }
                SettingsNotices(alive = module.alive)
            }
        }
        item(key = "voices") {
            val role = voice.key
            val mode = colour.mode
            val rgb = colour.rgb
            Column {
                SmallTitle(text = stringResource(voice.label))
                AppCard(modifier = Modifier.nextCard()) {
                    // The dropdown, and beside it the custom colour as a swatch that reopens the
                    // picker. miuix's dropdown row takes nothing at its end, so the swatch is its
                    // neighbour rather than part of it.
                    val custom = mode == LyricAppearance.CUSTOM
                    val pickCustom = { openPicker(voice, Color(rgb).copy(alpha = colour.opacity)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppDropdownPreference(
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
                            val label = stringResource(R.string.lyric_editor_adjust_color, stringResource(voice.label))
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
                        ValueSlider(
                            enabled = true,
                            title = stringResource(R.string.lyrics_color_intensity),
                            value = colour.intensity * 100f,
                            valueRange = 0f..100f,
                            detent = LyricAppearance.DEFAULT_INTENSITY * 100f,
                            label = { "${it.roundToInt()}%" },
                            onValueChange = {
                                changeAppearance("${role}Intensity" to it.roundToInt() / 100.0, persist = false)
                            },
                            onValueChangeFinished = { if (canSave) ModuleBridge.setLyricAppearance(context, module.lyricAppearance) },
                        )
                    }
                }
            }
        }
    }
}
