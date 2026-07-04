package com.opentonex.controller.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentonex.controller.ui.theme.KnobBodyDark
import com.opentonex.controller.ui.theme.KnobBodyLight
import com.opentonex.controller.ui.theme.KnobBodyMid
import com.opentonex.controller.ui.theme.KnobTrack
import androidx.compose.ui.draw.drawWithCache
import com.opentonex.controller.ui.theme.MonoLabelStyle
import com.opentonex.controller.ui.theme.ToneXAccent
import com.opentonex.controller.ui.theme.ToneXBackgroundBottom
import com.opentonex.controller.ui.theme.ToneXBackgroundTop
import com.opentonex.controller.ui.theme.ToneXOnSurfaceMuted
import com.opentonex.controller.ui.theme.ToneXSurfaceVariant
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Tamanhos de knob do design TUSB (outer/body em dp). */
enum class KnobSize(val outer: Dp, val body: Dp, val indicator: Dp) {
    LARGE(92.dp, 74.dp, 4.dp),
    MEDIUM(72.dp, 58.dp, 3.5.dp),
    SMALL(58.dp, 46.dp, 3.dp)
}

/**
 * Knob do design TUSB: arco conico de 300 graus comecando em 210 graus (CSS, a partir
 * do topo), corpo escuro com gradiente radial e indicador na borda superior do corpo.
 * Arraste vertical ajusta o valor (0..1).
 */
@Composable
fun TusbKnob(
    label: String,
    value: Float,
    valueText: String,
    modifier: Modifier = Modifier,
    size: KnobSize = KnobSize.MEDIUM,
    accent: Color = ToneXAccent,
    onValueChange: ((Float) -> Unit)? = null
) {
    val normalized = value.coerceIn(0f, 1f)
    val latestValue = rememberUpdatedState(normalized)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(text = label.uppercase(), style = MonoLabelStyle, color = ToneXOnSurfaceMuted)
        Box(
            modifier = Modifier
                .size(size.outer)
                .let { base ->
                    if (onValueChange == null) base else base.pointerInput(label) {
                        var startValue = 0f
                        var accumulated = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                startValue = latestValue.value
                                accumulated = 0f
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            accumulated -= dragAmount / 220f
                            onValueChange((startValue + accumulated).coerceIn(0f, 1f))
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Desenho unico: sombra projetada, trilha, arco de valor, corpo 3D com aro
            // de luz/oclusao e indicador. CSS "from 210deg" (a partir do topo) equivale
            // a startAngle 120 no Canvas (0 = 3h). Sweep total de 300 graus.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = (size.outer - size.body).toPx() / 2f
                val inset = stroke / 2f
                val arcSize = androidx.compose.ui.geometry.Size(
                    this.size.width - stroke,
                    this.size.height - stroke
                )
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val bodyR = size.body.toPx() / 2f

                // 1. Sombra projetada (luz vinda de cima): camadas concentricas translucidas
                //    deslocadas para baixo simulam o desfoque da sombra sobre o painel.
                val shadowCenter = Offset(center.x, center.y + bodyR * 0.16f)
                for (layer in 4 downTo 1) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.10f),
                        radius = bodyR + stroke * 0.30f * layer,
                        center = shadowCenter
                    )
                }

                // 2. Trilha e arco de valor
                drawArc(
                    color = KnobTrack,
                    startAngle = 120f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                drawArc(
                    color = accent,
                    startAngle = 120f,
                    sweepAngle = 300f * normalized,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )

                // 3. Corpo metalico: gradiente radial com highlight deslocado (luz 10-11h)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(KnobBodyLight, KnobBodyMid, KnobBodyDark),
                        center = Offset(center.x - bodyR * 0.40f, center.y - bodyR * 0.48f),
                        radius = bodyR * 2.0f
                    ),
                    radius = bodyR,
                    center = center
                )
                // Vinheta inferior: reforca o volume esferico do corpo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                        center = Offset(center.x - bodyR * 0.30f, center.y - bodyR * 0.35f),
                        radius = bodyR * 1.55f
                    ),
                    radius = bodyR,
                    center = center
                )
                // Reflexo glossy: hotspot suave no topo-esquerda (acabamento preto brilhante)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(center.x - bodyR * 0.38f, center.y - bodyR * 0.48f),
                        radius = bodyR * 0.85f
                    ),
                    radius = bodyR,
                    center = center
                )

                // 4. Aro do corpo: brilho especular no topo-esquerda + oclusao embaixo-direita
                val rimRect = Offset(center.x - bodyR, center.y - bodyR)
                val rimSize = androidx.compose.ui.geometry.Size(bodyR * 2f, bodyR * 2f)
                val rimStroke = (bodyR * 0.055f).coerceAtLeast(1f)
                drawArc(
                    color = Color.White.copy(alpha = 0.22f),
                    startAngle = 165f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = rimRect,
                    size = rimSize,
                    style = Stroke(width = rimStroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color.Black.copy(alpha = 0.55f),
                    startAngle = 0f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = rimRect,
                    size = rimSize,
                    style = Stroke(width = rimStroke * 1.4f, cap = StrokeCap.Round)
                )

                // 5. Tampa central rebaixada: sugere o encaixe usinado do knob
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(KnobBodyMid, KnobBodyDark),
                        center = Offset(center.x - bodyR * 0.18f, center.y - bodyR * 0.22f),
                        radius = bodyR * 0.55f
                    ),
                    radius = bodyR * 0.34f,
                    center = center
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.30f),
                    radius = bodyR * 0.34f,
                    center = center,
                    style = Stroke(width = rimStroke * 0.8f)
                )

                // 6. Indicador com sombra propria (leve deslocamento escuro atras da linha)
                val angleDeg = 120f + 300f * normalized
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val dirX = cos(angleRad).toFloat()
                val dirY = sin(angleRad).toFloat()
                drawLine(
                    color = Color.Black.copy(alpha = 0.45f),
                    start = Offset(center.x + dirX * bodyR * 0.42f, center.y + dirY * bodyR * 0.42f + rimStroke),
                    end = Offset(center.x + dirX * bodyR * 0.90f, center.y + dirY * bodyR * 0.90f + rimStroke),
                    strokeWidth = size.indicator.toPx() * 1.3f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = accent,
                    start = Offset(center.x + dirX * bodyR * 0.42f, center.y + dirY * bodyR * 0.42f),
                    end = Offset(center.x + dirX * bodyR * 0.90f, center.y + dirY * bodyR * 0.90f),
                    strokeWidth = size.indicator.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        Text(
            text = valueText,
            fontSize = 11.sp,
            color = ToneXOnSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}

/** Controle segmentado do design (fundo #2c2c2e, segmento ativo colorido). */
@Composable
fun TusbSegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = ToneXAccent,
    activeTextColor: Color = Color(0xFF111111),
    enabled: Boolean = true,
    onSelect: (Int) -> Unit
) {
    Surface(color = ToneXSurfaceVariant, shape = RoundedCornerShape(12.dp), modifier = modifier) {
        Row(modifier = Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Surface(
                    color = if (selected) activeColor else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = enabled) { onSelect(index) }
                ) {
                    Text(
                        text = option.uppercase(),
                        modifier = Modifier.padding(vertical = 9.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        color = if (selected) activeTextColor else ToneXOnSurfaceMuted
                    )
                }
            }
        }
    }
}

/**
 * Fundo padrao das telas TUSB: gradiente vertical escuro com textura granulada sutil
 * (ruido deterministico, pre-computado no cache de desenho — nada e' realocado por frame).
 * Substitui o background chapado para dar profundidade de painel de equipamento.
 */
fun Modifier.tusbBackground(): Modifier = this.drawWithCache {
    val gradient = Brush.verticalGradient(
        colors = listOf(ToneXBackgroundTop, ToneXBackgroundBottom),
        startY = 0f,
        endY = size.height
    )
    // Grao deterministico: mesma semente -> mesma textura, sem cintilar entre frames.
    val random = Random(42)
    val grainCount = ((size.width * size.height) / 3500f).toInt().coerceIn(200, 5000)
    val grains = List(grainCount) {
        Triple(
            Offset(random.nextFloat() * size.width, random.nextFloat() * size.height),
            0.6f + random.nextFloat() * 0.9f,          // raio px
            0.012f + random.nextFloat() * 0.030f        // alpha
        )
    }
    onDrawBehind {
        drawRect(brush = gradient)
        grains.forEach { (position, radius, alpha) ->
            drawCircle(color = Color.White.copy(alpha = alpha), radius = radius, center = position)
        }
        // Vinheta lateral leve para afunilar o foco no centro
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 0.25f),
                0.12f to Color.Transparent,
                0.88f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.25f)
            )
        )
    }
}

/** Chip de bloco de efeito (AMP/CAB/...) com as cores do design. */
@Composable
fun EffectChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color, shape = RoundedCornerShape(4.dp), modifier = modifier) {
        Text(
            text = text,
            style = MonoLabelStyle.copy(fontSize = 9.sp, letterSpacing = 0.4.sp),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}
