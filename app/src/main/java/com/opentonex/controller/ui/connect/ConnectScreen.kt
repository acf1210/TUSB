package com.opentonex.controller.ui.connect

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentonex.controller.R
import com.opentonex.controller.ui.components.tusbBackground
import com.opentonex.controller.ui.theme.MonoLabelStyle
import com.opentonex.controller.ui.theme.ToneXAccent
import com.opentonex.controller.ui.theme.ToneXBackground
import com.opentonex.controller.ui.theme.ToneXDivider
import com.opentonex.controller.ui.theme.ToneXOnSurfaceFaint
import com.opentonex.controller.ui.theme.ToneXOnSurfaceMuted
import com.opentonex.controller.ui.theme.ToneXSurface
import com.opentonex.controller.ui.theme.ToneXSurfaceHigh

/**
 * Tela de conexao no layout do design TUSB: logo com glow ambar pulsante, card de
 * status com os passos de pareamento e botao de acao ambar.
 */
@Composable
fun ConnectScreen(
    statusMessage: String,
    isBusy: Boolean,
    errorMessage: String?,
    onConnectReal: () -> Unit,
    onConnectFake: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize().tusbBackground(),
        contentAlignment = Alignment.Center
    ) {
    Column(
        modifier = Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo com glow pulsante
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            PulsingLogoCard()
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "TUSB",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.connect_subtitle).uppercase(),
                    style = MonoLabelStyle.copy(fontSize = 11.sp, letterSpacing = 1.4.sp),
                    color = ToneXOnSurfaceFaint
                )
            }
        }

        // Card de status com passos
        Surface(
            color = ToneXSurface,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isBusy) ToneXAccent else ToneXSurfaceHigh)
                    )
                    Text(
                        text = statusMessage,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = ToneXOnSurfaceFaint
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ToneXDivider))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConnectStep(number = "1", text = stringResource(R.string.connect_step1))
                    ConnectStep(number = "2", text = stringResource(R.string.connect_step2))
                    ConnectStep(number = "3", text = stringResource(R.string.connect_step3))
                }
            }
        }

        // Acoes
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            Surface(
                color = if (isBusy) ToneXAccent.copy(alpha = 0.5f) else ToneXAccent,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isBusy, onClick = onConnectReal)
            ) {
                Text(
                    text = stringResource(R.string.connect_real),
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = Color(0xFF111111)
                )
            }
            Text(
                text = stringResource(R.string.connect_fake),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ToneXOnSurfaceMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !isBusy, onClick = onConnectFake)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Text(
                text = stringResource(R.string.connect_footer).uppercase(),
                style = MonoLabelStyle.copy(fontSize = 10.sp),
                color = ToneXSurfaceHigh
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    }
}

@Composable
private fun ConnectStep(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$number —",
            style = MonoLabelStyle.copy(fontSize = 12.sp, letterSpacing = 0.5.sp),
            color = ToneXOnSurfaceFaint
        )
        Text(
            text = text,
            style = MonoLabelStyle.copy(fontSize = 12.sp, letterSpacing = 0.5.sp),
            color = ToneXOnSurfaceMuted
        )
    }
}

@Composable
private fun PulsingLogoCard() {
    val transition = rememberInfiniteTransition(label = "logo-glow")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow-alpha"
    )
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(160.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ToneXAccent.copy(alpha = glowAlpha), Color.Transparent),
                    radius = size.minDimension / 2f
                )
            )
        }
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(ToneXSurface)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.tusb_icon_original),
                contentDescription = "TUSB",
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}
