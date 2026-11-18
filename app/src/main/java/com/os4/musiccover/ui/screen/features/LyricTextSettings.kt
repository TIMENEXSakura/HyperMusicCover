package com.os4.musiccover.ui.screen.features

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.os4.musiccover.R
import com.os4.musiccover.app.ModuleBridge
import com.os4.musiccover.app.SettingsStore
import com.os4.musiccover.lyrics.LyricAppearance
import kotlin.math.roundToInt
import org.json.JSONObject
import top.yukonga.miuix.kmp.preference.SwitchPreference

// The independent editor can change this JSON while the parent page is paused. Apply each
// field to the latest saved document, preserving the other voices and any offline edits.
private fun setAppearanceField(
    context: Context, module: ModuleBridge.State, onChange: (ModuleBridge.State) -> Unit,
    key: String, value: Any,
) {
    val latest = SettingsStore.overlay(module)
    val json = JSONObject(LyricAppearance.parse(latest.lyricAppearance).toJson()).put(key, value)
    val updated = LyricAppearance.parse(json.toString()).toJson()
    onChange(latest.copy(lyricAppearance = updated))
    ModuleBridge.setLyricAppearance(context, updated)
}

@Composable
internal fun DuetVoiceSwitch(
    enabled: Boolean, module: ModuleBridge.State, onChange: (ModuleBridge.State) -> Unit,
) {
    val context = LocalContext.current
    val appearance = remember(module.lyricAppearance) { LyricAppearance.parse(module.lyricAppearance) }
    SwitchPreference(
        title = stringResource(R.string.lyric_editor_duet_enabled),
        summary = stringResource(R.string.lyric_editor_duet_summary),
        checked = appearance.duetEnabled, enabled = enabled,
        onCheckedChange = { setAppearanceField(context, module, onChange, "duetEnabled", it) },
    )
}

/** Type and visibility remain on the lock-screen lyrics tab for all three voices. */
@Composable
internal fun LyricTextSettings(
    enabled: Boolean,
    module: ModuleBridge.State,
    onChange: (ModuleBridge.State) -> Unit,
) {
    val context = LocalContext.current
    val appearance = remember(module.lyricAppearance) { LyricAppearance.parse(module.lyricAppearance) }

    fun opacity(role: String, percent: Float) {
        setAppearanceField(context, module, onChange, "${role}Opacity", percent / 100.0)
    }

    VoiceTextSliders(
        enabled, stringResource(R.string.lyrics_style_main), module.lyricSizeSp, 18f..36f,
        module.lyricWeight, appearance.mainOpacity, 25f, 1f,
        onSize = {
            onChange(module.copy(lyricSizeSp = it))
            ModuleBridge.setLyricStyle(context, "size", it)
        },
        onWeight = {
            onChange(module.copy(lyricWeight = it))
            ModuleBridge.setLyricWeight(context, "weight", it)
        },
        onOpacity = { opacity("main", it) },
    )
    VoiceTextSliders(
        enabled, stringResource(R.string.lyrics_style_backing), module.lyricBgSizeSp, 10f..30f,
        module.lyricBgWeight, appearance.bgOpacity, 17f, LyricAppearance.DEFAULT.bgOpacity,
        onSize = {
            onChange(module.copy(lyricBgSizeSp = it))
            ModuleBridge.setLyricStyle(context, "bgsize", it)
        },
        onWeight = {
            onChange(module.copy(lyricBgWeight = it))
            ModuleBridge.setLyricWeight(context, "bgweight", it)
        },
        onOpacity = { opacity("bg", it) },
    )
    VoiceTextSliders(
        enabled, stringResource(R.string.lyrics_style_trans), module.lyricTransSizeSp, 10f..30f,
        module.lyricTransWeight, appearance.transOpacity, 15f, LyricAppearance.DEFAULT_TRANS_OPACITY,
        onSize = {
            onChange(module.copy(lyricTransSizeSp = it))
            ModuleBridge.setLyricStyle(context, "transsize", it)
        },
        onWeight = {
            onChange(module.copy(lyricTransWeight = it))
            ModuleBridge.setLyricWeight(context, "transweight", it)
        },
        onOpacity = { opacity("trans", it) },
    )
    if (appearance.duetEnabled) {
        VoiceOpacitySlider(enabled, stringResource(R.string.lyric_editor_duet_voice),
            appearance.duetOpacity, LyricAppearance.DEFAULT.duetOpacity) { opacity("duet", it) }
    }
}

private val fontWeights = listOf(300f, 400f, 500f, 600f, 700f)

@Composable
private fun VoiceTextSliders(
    enabled: Boolean, voice: String, size: Float, sizeRange: ClosedFloatingPointRange<Float>,
    weight: Int, opacity: Float, defaultSize: Float, defaultOpacity: Float,
    onSize: (Float) -> Unit, onWeight: (Int) -> Unit, onOpacity: (Float) -> Unit,
) {
    var sizeDraft by remember(size) { mutableFloatStateOf(size.coerceIn(sizeRange)) }
    var weightDraft by remember(weight) { mutableFloatStateOf(weight.toFloat()) }
    ValueSlider(
        title = stringResource(R.string.lyric_text_voice_size, voice), enabled = enabled,
        value = sizeDraft, valueRange = sizeRange, detent = defaultSize,
        label = { "${it.roundToInt()} sp" },
        onValueChange = { sizeDraft = it.roundToInt().toFloat() },
        onValueChangeFinished = { if (sizeDraft != size) onSize(sizeDraft) },
    )
    ValueSlider(
        title = stringResource(R.string.lyric_text_voice_weight, voice), enabled = enabled,
        value = weightDraft, valueRange = 300f..700f, keyPoints = fontWeights,
        label = { it.roundToInt().toString() },
        onValueChange = { weightDraft = (it / 100f).roundToInt() * 100f },
        onValueChangeFinished = { if (weightDraft.toInt() != weight) onWeight(weightDraft.toInt()) },
    )
    VoiceOpacitySlider(enabled, voice, opacity, defaultOpacity, onOpacity)
}

@Composable
private fun VoiceOpacitySlider(
    enabled: Boolean, voice: String, opacity: Float, defaultOpacity: Float, onOpacity: (Float) -> Unit,
) {
    val percent = (opacity * 100f).roundToInt().toFloat()
    var opacityDraft by remember(percent) { mutableFloatStateOf(percent) }
    ValueSlider(
        title = stringResource(R.string.lyric_text_voice_opacity, voice), enabled = enabled,
        value = opacityDraft, valueRange = 0f..100f, detent = defaultOpacity * 100f,
        label = { "${it.roundToInt()}%" },
        onValueChange = { opacityDraft = it.roundToInt().toFloat() },
        onValueChangeFinished = { if (opacityDraft != percent) onOpacity(opacityDraft) },
    )
}
