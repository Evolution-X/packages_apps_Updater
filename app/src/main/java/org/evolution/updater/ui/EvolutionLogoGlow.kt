/*
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.updater.ui

import android.animation.ValueAnimator
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.graphics.PathParser
import com.android.settingslib.spa.debug.UiModePreviews
import com.android.settingslib.spa.framework.theme.SettingsTheme
import org.evolution.updater.R

private const val VIEWPORT_W = 1080f
private const val VIEWPORT_H = 240f

private val PINK = 0xFFFF2E9A.toInt()
private val VIOLET = 0xFF8B5CF6.toInt()
private val CYAN = 0xFF22D3EE.toInt()

// Gradient vector (viewport units). One sweep moves it by its own length,
// so the repeating gradient loops seamlessly.
private const val GRAD_X1 = 190f
private const val GRAD_Y1 = 60f
private const val GRAD_X2 = 890f
private const val GRAD_Y2 = 180f
private const val SWEEP_MS = 5000L

private const val MAIN_STROKE = 2.4f
private const val FILL_ALPHA = 38 // 15%
private const val GLOW_STROKE = 9f
private const val GLOW_SIGMA = 7f
private const val GLOW_ALPHA = 140 // 55%
private const val FRINGE_STROKE = 3f
private const val FRINGE_SIGMA = 2.5f
private const val FRINGE_SHIFT = 2f
private const val FRINGE_ALPHA = 128 // 50%

// Shine: a skewed band crossing the logo, then a pause.
private const val SHINE_MS = 5000L
private const val SHINE_ACTIVE = 0.3f // fraction of the period spent moving
private const val SHINE_FROM = 120f
private const val SHINE_TO = 900f
private const val SHINE_WIDTH = 90f
private const val SHINE_SKEW = -0.36397f // tan(-20 deg)
private const val SHINE_REACH = 5000f
private const val SHINE_FILL_ALPHA = 64 // 25%

// Sparkles: x, y, size (viewport units), then twinkle start/peak/end as a
// fraction of SPARKLE_MS.
private const val SPARKLE_MS = 3200L
private val SPARKLES = arrayOf(
    floatArrayOf(880f, 66f, 9f, 0.00f, 0.20f, 0.40f),
    floatArrayOf(792f, 176f, 7f, 0.35f, 0.50f, 0.65f),
    floatArrayOf(548f, 80f, 6f, 0.60f, 0.75f, 0.90f),
)

/**
 * The Evolution X wordmark with a holographic treatment: a pink, violet and
 * cyan gradient sweeping across the outline, a soft neon glow with a chromatic
 * fringe, a periodic shine streak and a few twinkling sparkles. Shine and
 * sparkles follow the theme's on-surface color. Mirrors GlowView in Settings.
 */
@Composable
fun EvolutionLogoGlow(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val renderer = remember {
        GlowRenderer(context.resources.getStringArray(R.array.evolution_logo_paths))
    }
    val ink = MaterialTheme.colorScheme.onSurface.toArgb() or 0xFF000000.toInt()

    // Respect "remove animations": draw one still frame instead of looping.
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    var time by remember { mutableLongStateOf(0L) }
    if (animate) {
        LaunchedEffect(Unit) {
            val start = withFrameMillis { it }
            while (true) {
                withFrameMillis { time = it - start }
            }
        }
    }

    Canvas(
        modifier = modifier
            .aspectRatio(VIEWPORT_W / VIEWPORT_H)
            .semantics { contentDescription?.let { this.contentDescription = it } },
    ) {
        val t = time
        drawIntoCanvas { canvas ->
            renderer.draw(canvas.nativeCanvas, size.width, size.height, t, ink)
        }
    }
}

private class GlowRenderer(pathData: Array<String>) {
    private val logoUnits = Path().apply {
        pathData.forEach { addPath(PathParser.createPathFromPathData(it)) }
    }
    private val logo = Path()
    private val star = Path().apply {
        moveTo(0f, -1f)
        quadTo(0f, 0f, 1f, 0f)
        quadTo(0f, 0f, 0f, 1f)
        quadTo(0f, 0f, -1f, 0f)
        quadTo(0f, 0f, 0f, -1f)
        close()
    }
    private val matrix = Matrix()
    private val shaderMatrix = Matrix()

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        alpha = GLOW_ALPHA
    }
    private val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = CYAN
        alpha = FRINGE_ALPHA
    }
    private val pinkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = PINK
        alpha = FRINGE_ALPHA
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = FILL_ALPHA
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val shineFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val shineStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val band = LinearGradient(
        0f, 0f, SHINE_WIDTH, 0f,
        intArrayOf(0x00FFFFFF, 0xFFFFFFFF.toInt(), 0x00FFFFFF), null,
        Shader.TileMode.CLAMP,
    )
    private var holo: LinearGradient? = null

    private var width = 0f
    private var height = 0f
    private var scale = 0f
    private var ox = 0f
    private var oy = 0f
    private var ink = 0

    private fun layout(w: Float, h: Float) {
        width = w
        height = h
        scale = minOf(w / VIEWPORT_W, h / VIEWPORT_H)
        ox = (w - VIEWPORT_W * scale) / 2f
        oy = (h - VIEWPORT_H * scale) / 2f

        matrix.setScale(scale, scale)
        matrix.postTranslate(ox, oy)
        logoUnits.transform(matrix, logo)

        val glowBlur = BlurMaskFilter(blurRadius(GLOW_SIGMA * scale), BlurMaskFilter.Blur.NORMAL)
        val fringeBlur = BlurMaskFilter(blurRadius(FRINGE_SIGMA * scale), BlurMaskFilter.Blur.NORMAL)

        glowPaint.strokeWidth = GLOW_STROKE * scale
        glowPaint.maskFilter = glowBlur
        cyanPaint.strokeWidth = FRINGE_STROKE * scale
        cyanPaint.maskFilter = fringeBlur
        pinkPaint.strokeWidth = FRINGE_STROKE * scale
        pinkPaint.maskFilter = fringeBlur
        strokePaint.strokeWidth = MAIN_STROKE * scale
        shineStroke.strokeWidth = MAIN_STROKE * scale

        holo = LinearGradient(
            ox + GRAD_X1 * scale, oy + GRAD_Y1 * scale,
            ox + GRAD_X2 * scale, oy + GRAD_Y2 * scale,
            intArrayOf(PINK, VIOLET, CYAN, PINK),
            floatArrayOf(0f, 0.33f, 0.66f, 1f),
            Shader.TileMode.REPEAT,
        )
    }

    private fun applyInk(color: Int) {
        ink = color
        shineFill.color = color
        shineFill.alpha = SHINE_FILL_ALPHA
        shineStroke.color = color
        sparklePaint.color = color
    }

    fun draw(canvas: android.graphics.Canvas, w: Float, h: Float, t: Long, inkColor: Int) {
        if (w != width || h != height || holo == null) layout(w, h)
        if (inkColor != ink) applyInk(inkColor)
        val holo = holo ?: return
        if (scale <= 0f) return

        // Sweep the gradient along its own vector, which loops seamlessly.
        val sweep = (t % SWEEP_MS) / SWEEP_MS.toFloat()
        shaderMatrix.setTranslate(
            sweep * (GRAD_X2 - GRAD_X1) * scale,
            sweep * (GRAD_Y2 - GRAD_Y1) * scale,
        )
        holo.setLocalMatrix(shaderMatrix)
        glowPaint.shader = holo
        fillPaint.shader = holo
        strokePaint.shader = holo

        // Neon glow, then the cyan/pink chromatic fringe
        canvas.drawPath(logo, glowPaint)

        canvas.save()
        canvas.translate(-FRINGE_SHIFT * scale, 0f)
        canvas.drawPath(logo, cyanPaint)
        canvas.restore()

        canvas.save()
        canvas.translate(FRINGE_SHIFT * scale, 0f)
        canvas.drawPath(logo, pinkPaint)
        canvas.restore()

        // Glassy fill and gradient outline
        canvas.drawPath(logo, fillPaint)
        canvas.drawPath(logo, strokePaint)

        drawShine(canvas, t)
        drawSparkles(canvas, t)
    }

    private fun drawShine(canvas: android.graphics.Canvas, t: Long) {
        val p = (t % SHINE_MS) / SHINE_MS.toFloat()
        if (p >= SHINE_ACTIVE) return

        val bx = SHINE_FROM + (SHINE_TO - SHINE_FROM) * (p / SHINE_ACTIVE)

        val layer = canvas.saveLayer(0f, 0f, width, height, null)
        canvas.drawPath(logo, shineFill)
        canvas.drawPath(logo, shineStroke)

        // Keep only the part of the logo that sits under the band
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(scale, scale)
        canvas.skew(SHINE_SKEW, 0f)
        shaderMatrix.setTranslate(bx, 0f)
        band.setLocalMatrix(shaderMatrix)
        bandPaint.shader = band
        canvas.drawRect(-SHINE_REACH, -SHINE_REACH, SHINE_REACH, SHINE_REACH, bandPaint)
        canvas.restore()
        canvas.restoreToCount(layer)
    }

    private fun drawSparkles(canvas: android.graphics.Canvas, t: Long) {
        val p = (t % SPARKLE_MS) / SPARKLE_MS.toFloat()
        for (s in SPARKLES) {
            val k = pulse(p, s[3], s[4], s[5]) * s[2] * scale
            if (k <= 0f) continue
            canvas.save()
            canvas.translate(ox + s[0] * scale, oy + s[1] * scale)
            canvas.scale(k, k)
            canvas.drawPath(star, sparklePaint)
            canvas.restore()
        }
    }

    // 0 at start and end, 1 at peak, linear in between
    private fun pulse(p: Float, start: Float, peak: Float, end: Float): Float {
        if (p <= start || p >= end) return 0f
        return if (p < peak) (p - start) / (peak - start) else (end - p) / (end - peak)
    }

    // BlurMaskFilter takes a radius, Skia converts it with sigma = 0.57735 * r + 0.5
    private fun blurRadius(sigma: Float): Float = maxOf(1f, (sigma - 0.5f) / 0.57735f)
}

@UiModePreviews
@Composable
private fun EvolutionLogoGlowPreview() {
    SettingsTheme {
        EvolutionLogoGlow()
    }
}
