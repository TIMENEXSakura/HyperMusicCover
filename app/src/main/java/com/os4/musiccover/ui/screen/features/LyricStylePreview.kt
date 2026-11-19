package com.os4.musiccover.ui.screen.features

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.os4.musiccover.R
import com.os4.musiccover.app.ModuleBridge
import com.os4.musiccover.lyrics.LyricAppearance
import com.os4.musiccover.lyrics.LyricExitWipe
import com.os4.musiccover.lyrics.LyricPalette
import com.os4.musiccover.ui.util.firstCard
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class LyricEditorVoice(val key: String, val label: Int) {
    Lead("main", R.string.lyrics_style_main),
    Translation("trans", R.string.lyrics_style_trans),
    Backing("bg", R.string.lyrics_style_backing),
    Duet("duet", R.string.lyric_editor_duet_voice),
}

/** Both the picker and the simulated lock screen read the same colour channels. */
internal data class VoiceColour(val mode: Int, val rgb: Int, val opacity: Float, val intensity: Float)

internal fun LyricAppearance.voiceColour(voice: LyricEditorVoice) = when (voice) {
    LyricEditorVoice.Lead -> VoiceColour(mainMode, mainColor, mainOpacity, mainIntensity)
    LyricEditorVoice.Translation -> VoiceColour(transMode, transColor, transOpacity, transIntensity)
    LyricEditorVoice.Backing -> VoiceColour(bgMode, bgColor, bgOpacity, bgIntensity)
    LyricEditorVoice.Duet -> VoiceColour(duetMode, duetColor, duetOpacity, duetIntensity)
}

/** The clock and gesture bar frame the same lead / backing / translated lines as the lyric view. */
@Composable
internal fun LyricStylePreview(
    state: ModuleBridge.State,
    appearance: LyricAppearance,
    voice: LyricEditorVoice,
    draft: Color?,
    onPick: (LyricEditorVoice, Color) -> Unit,
) {
    val seed = state.lyricPreviewSeed.takeIf { it != 0 } ?: 0xff57507c.toInt()
    var palette by remember(seed) { mutableStateOf<LyricPalette.Colors?>(null) }
    LaunchedEffect(seed) { palette = withContext(Dispatchers.Default) { LyricPalette.colors(seed) } }
    val systemLit = colorResource(android.R.color.system_accent1_100).toArgb()
    val systemDim = colorResource(android.R.color.system_accent1_200).toArgb()
    // LyricView's unlit official ink is a pale version of the cover colour.
    val officialDim = remember(seed) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(seed, hsv)
        hsv[1] = minOf(0.28f, hsv[1] * 1.5f)
        hsv[2] = 1f
        android.graphics.Color.HSVToColor(hsv)
    }
    fun ink(role: LyricEditorVoice, lit: Boolean): Color {
        if (role == voice && draft != null) return draft
        val (mode, custom, alpha, strength) = appearance.voiceColour(role)
        val official = if (lit) -1 else officialDim
        val color = when (mode) {
            LyricAppearance.CUSTOM -> custom
            LyricAppearance.SYSTEM -> LyricAppearance.soften(if (lit) systemLit else systemDim, strength)
            LyricAppearance.COVER -> palette?.let {
                val base = when {
                    role == LyricEditorVoice.Lead && lit -> it.mainLit
                    role == LyricEditorVoice.Lead -> it.mainDim
                    lit -> it.bgLit
                    else -> it.bgDim
                }
                LyricAppearance.soften(base, strength)
            } ?: official
            else -> official
        }
        return Color(color).copy(alpha = alpha)
    }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val clock = remember(context, configuration) { DateFormat.getTimeFormat(context).format(Date()) }
    val progress = remember { Animatable(0f) }
    var replay by remember { mutableIntStateOf(0) }
    LaunchedEffect(replay) {
        if (replay == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(LyricExitWipe.DURATION_MS.toInt(), easing = LinearEasing))
        delay(260L)
        progress.snapTo(0f)
    }

    val lead = sampleFields(stringResource(R.string.lyric_editor_sample_lead), timed = true)
    val backing = sampleFields(stringResource(R.string.lyric_editor_sample_backing), timed = true)
    val roman = if (state.lyricsRoma) stringResource(R.string.lyric_editor_sample_roman) else ""
    val translated = if (state.lyricsTrans) stringResource(R.string.lyric_editor_sample_trans) else ""
    val below = sampleFields(listOf(roman, translated).filter { it.isNotEmpty() }.joinToString("\n"), timed = false)
    val count = lead.fields + backing.fields + below.fields
    val leadAlign = when (state.lyricsAlign) { 1 -> TextAlign.Center; 2 -> TextAlign.End; else -> TextAlign.Start }
    val backingAlign = when (state.lyricsAlign) { 1 -> TextAlign.Center; 2 -> TextAlign.Start; else -> TextAlign.End }
    val dark = MiuixTheme.colorScheme.background.luminance() < .4f
    Card(modifier = Modifier.firstCard()) {
        Column(Modifier.fillMaxWidth().lockScreenLandscape(Color(seed), dark)
            .padding(horizontal = 20.dp, vertical = 12.dp)) {
            Column(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White.copy(alpha = .60f), modifier = Modifier.size(15.dp))
                Text(clock, fontWeight = FontWeight.Light, fontSize = 46.sp, color = Color.White.copy(alpha = .94f))
            }
            Spacer(Modifier.height(18.dp))
            PreviewVoice(LyricEditorVoice.Lead, voice, onPick, lead, 0, count, ink(LyricEditorVoice.Lead, true),
                state.lyricSizeSp, state.lyricWeight, leadAlign, progress.value)
            PreviewVoice(LyricEditorVoice.Backing, voice, onPick, backing, lead.fields, count, ink(LyricEditorVoice.Backing, true),
                state.lyricBgSizeSp, state.lyricBgWeight, backingAlign, progress.value)
            if (below.fields > 0) {
                PreviewVoice(LyricEditorVoice.Translation, voice, onPick, below, lead.fields + backing.fields, count, ink(LyricEditorVoice.Translation, false),
                    state.lyricTransSizeSp, state.lyricTransWeight, leadAlign, progress.value)
            }
            // This is the next lead line. Its words stay on screen while the current group exits.
            val duet = sampleFields(stringResource(R.string.lyric_editor_sample_duet), timed = true)
            if (appearance.duetEnabled) {
                PreviewVoice(LyricEditorVoice.Duet, voice, onPick, duet, 0, duet.fields,
                    ink(LyricEditorVoice.Duet, true), state.lyricSizeSp, state.lyricWeight, backingAlign, 0f)
            } else {
                val next = ink(LyricEditorVoice.Lead, false)
                Text(duet.text, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.lyric_editor_adjust_color,
                        stringResource(R.string.lyrics_style_main))) {
                        onPick(LyricEditorVoice.Lead, ink(LyricEditorVoice.Lead, true))
                    }.heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 10.dp),
                    color = next.copy(alpha = next.alpha * .30f), textAlign = leadAlign,
                    fontSize = state.lyricSizeSp.sp, lineHeight = (state.lyricSizeSp * 1.35f).sp,
                    fontWeight = FontWeight(state.lyricWeight))
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.align(Alignment.CenterHorizontally).width(55.dp).height(3.dp)
                .background(Color.White.copy(alpha = .55f), RoundedCornerShape(50)))
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.lyric_editor_tap_hint), modifier = Modifier.weight(1f),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
            IconButton(onClick = { replay++ }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Replay, contentDescription = stringResource(R.string.lyric_editor_replay),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}

/** A dusk landscape behind the clock and lyrics. Paths and brushes depend only on the available
 * size and the current cover, so replaying the lyric animation reuses the same drawing. */
private fun Modifier.lockScreenLandscape(cover: Color, dark: Boolean) = drawWithCache {
    val w = size.width
    val h = size.height
    val sky = lerp(if (dark) Color(0xff28354c) else Color(0xff475c77), cover, .17f)
    val haze = lerp(if (dark) Color(0xff73637d) else Color(0xff957d88), cover, .10f)
    val dusk = if (dark) Color(0xff9c777a) else Color(0xffbd9386)
    val distant = lerp(if (dark) Color(0xff536279) else Color(0xff758298), cover, .12f)
    val middle = if (dark) Color(0xff344959) else Color(0xff485f70)
    val near = lerp(if (dark) Color(0xff233848) else Color(0xff344c5a), cover, .08f)
    val skyBrush = Brush.verticalGradient(0f to sky, .39f to haze, .67f to dusk, 1f to distant)
    val sun = Offset(.83f * w, .16f * h)
    val glow = Brush.radialGradient(listOf(dusk.copy(alpha = .59f), dusk.copy(alpha = 0f)),
        center = sun, radius = (minOf(w, h) * .34f).coerceAtLeast(1f))
    val sunRadius = minOf(w, h) * .037f
    val farRidge = Path().apply {
        moveTo(0f, .66f * h)
        cubicTo(.13f * w, .62f * h, .18f * w, .51f * h, .29f * w, .54f * h)
        cubicTo(.38f * w, .56f * h, .47f * w, .43f * h, .54f * w, .46f * h)
        cubicTo(.64f * w, .51f * h, .68f * w, .43f * h, .77f * w, .45f * h)
        cubicTo(.85f * w, .47f * h, .92f * w, .41f * h, w, .38f * h)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    val middleRidge = Path().apply {
        moveTo(0f, .51f * h)
        cubicTo(.13f * w, .52f * h, .22f * w, .65f * h, .33f * w, .65f * h)
        cubicTo(.48f * w, .66f * h, .55f * w, .75f * h, .66f * w, .69f * h)
        cubicTo(.76f * w, .66f * h, .9f * w, .57f * h, w, .59f * h)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    val nearRidge = Path().apply {
        moveTo(0f, .83f * h)
        cubicTo(.18f * w, .72f * h, .30f * w, .79f * h, .46f * w, .85f * h)
        cubicTo(.65f * w, .94f * h, .79f * w, .70f * h, w, .74f * h)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    val shade = Brush.verticalGradient(listOf(Color(0xff151e37).copy(alpha = .04f),
        Color(0xff182637).copy(alpha = if (dark) .28f else .32f),
        Color(0xff14212e).copy(alpha = .12f)))
    onDrawBehind {
        drawRect(skyBrush)
        drawRect(glow)
        drawCircle(Color(0xfff6d5ba).copy(alpha = .58f), sunRadius, sun)
        drawPath(farRidge, distant)
        drawPath(middleRidge, middle)
        drawPath(nearRidge, near)
        drawRect(shade)
    }
}

private class SampleFields(val text: String, val fieldAt: IntArray) {
    val fields = (fieldAt.lastOrNull() ?: -1) + 1
}

@Composable
private fun sampleFields(marked: String, timed: Boolean): SampleFields = remember(marked, timed) {
    val parts = marked.split('|')
    val words = parts.joinToString("")
    var end = 0
    val timedEnds = if (timed) {
        IntArray(parts.size) { index ->
            end += parts[index].length
            end
        }
    } else null
    SampleFields(words, LyricExitWipe.fields(words, 0, words.length, timedEnds))
}

/** Colour spans retain the measured advances as the words disappear, including wrapped lines. */
@Composable
private fun PreviewVoice(
    role: LyricEditorVoice, current: LyricEditorVoice, onPick: (LyricEditorVoice, Color) -> Unit,
    sample: SampleFields, first: Int, total: Int, ink: Color,
    size: Float, weight: Int, alignment: TextAlign, progress: Float,
) {
    val selectedRole = role == current
    val shape = RoundedCornerShape(12.dp)
    val painted = remember(sample, first, total, ink, progress) {
        buildAnnotatedString {
            var start = 0
            while (start < sample.text.length) {
                val field = sample.fieldAt[start]
                var end = start + 1
                while (end < sample.text.length && sample.fieldAt[end] == field) end++
                val opacity = LyricExitWipe.opacity(first + field, total, progress)
                withStyle(SpanStyle(color = ink.copy(alpha = ink.alpha * opacity))) {
                    append(sample.text, start, end)
                }
                start = end
            }
        }
    }
    val label = stringResource(role.label)
    Box(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (selectedRole) Color.White.copy(alpha = .065f) else Color.Transparent)
            .border(1.dp, if (selectedRole) Color.White.copy(alpha = .45f) else Color.Transparent, shape)
            .clickable(role = Role.Button,
                onClickLabel = stringResource(R.string.lyric_editor_adjust_color, label)) { onPick(role, ink) }
            .semantics { selected = selectedRole }
            .heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(painted, modifier = Modifier.fillMaxWidth(), color = ink, fontSize = size.sp,
            fontWeight = FontWeight(weight), lineHeight = (size * 1.35f).sp, textAlign = alignment)
        // Keep a fully transparent voice discoverable so the user can turn its opacity back up.
        if (ink.alpha < .04f) {
            Text(label, color = Color.White.copy(alpha = .65f), fontSize = 13.sp)
        }
    }
}
