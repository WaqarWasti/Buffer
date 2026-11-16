package app.buffer.ui.buffer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.buffer.data.formatAgo
import app.buffer.data.formatMinutes
import app.buffer.ui.theme.Accent
import app.buffer.ui.theme.Bg
import app.buffer.ui.theme.Ink
import app.buffer.ui.theme.Line
import app.buffer.ui.theme.Muted
import app.buffer.ui.theme.OnAccent
import kotlinx.coroutines.launch

private const val HOLD_MS = 1500

/**
 * Timeline: breathing prompt, then the facts, then (only when the timer ends) the choice.
 * The good choice is big and bright. The bad choice is small, dim and needs a deliberate hold.
 */
@Composable
fun BufferScreen(
    appLabel: String,
    seconds: Int,
    stats: BufferStats?,
    onDecline: () -> Unit,
    onContinue: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(seconds) {
        progress.animateTo(1f, tween(durationMillis = seconds * 1000, easing = LinearEasing))
    }
    val breathing by remember { derivedStateOf { progress.value < 0.4f } }
    val done by remember { derivedStateOf { progress.value >= 1f } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(Line),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.value)
                    .background(Accent),
            )
        }

        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Crossfade(targetState = breathing, label = "phase") { isBreathing ->
                if (isBreathing) BreathPhase() else FactsPhase(appLabel, stats)
            }
        }

        AnimatedVisibility(
            visible = done,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onDecline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                ) {
                    Text("I don’t want to open $appLabel", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(12.dp))
                HoldToContinue(appLabel, onContinue)
            }
        }
    }
}

@Composable
private fun BreathPhase() {
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    Box(
        Modifier
            .size(150.dp)
            .scale(scale)
            .background(Accent.copy(alpha = 0.08f), CircleShape)
            .border(1.5.dp, Accent.copy(alpha = 0.6f), CircleShape),
    )
    Spacer(Modifier.height(56.dp))
    Text(
        "It’s time to take a deep breath…",
        color = Ink,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun FactsPhase(appLabel: String, stats: BufferStats?) {
    if (stats == null) return
    val n = stats.attempts24h
    Text(n.toString(), color = Ink, fontSize = 96.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(
        if (n == 1) "attempt to open $appLabel\nwithin the last 24 hours."
        else "attempts to open $appLabel\nwithin the last 24 hours.",
        color = Ink,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    val gap = if (n >= 2) 24 * 60 / n else 0
    if (gap in 1..119) {
        Text(
            "That’s about once every ${formatMinutes(gap)}.",
            color = Muted,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
    }
    Text(
        stats.lastOpenedMs?.let { "Last opened: ${formatAgo(it)}" } ?: "You haven’t opened it through Buffer yet.",
        color = Muted,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
    )
    if (stats.savedMinutes > 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            "You’ve kept ${formatMinutes(stats.savedMinutes)} from $appLabel so far.",
            color = Accent,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Deliberately effortful: press and hold, with a thin fill, release to cancel. */
@Composable
private fun HoldToContinue(appLabel: String, onComplete: () -> Unit) {
    val fill = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val job = scope.launch {
                            fill.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                            onComplete()
                        }
                        tryAwaitRelease()
                        job.cancel()
                        scope.launch { fill.snapTo(0f) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(fill.value)
                .height(2.dp)
                .background(Muted),
        )
        Text(
            if (fill.value > 0f) "Keep holding…" else "Hold to continue to $appLabel",
            color = Muted.copy(alpha = 0.8f),
            fontSize = 14.sp,
        )
    }
}
