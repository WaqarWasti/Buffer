package app.buffer.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.buffer.data.Attempts
import app.buffer.data.Store
import app.buffer.data.formatMinutes
import app.buffer.data.startOfToday
import app.buffer.ui.theme.Accent
import app.buffer.ui.theme.Bg
import app.buffer.ui.theme.Ink
import app.buffer.ui.theme.Line
import app.buffer.ui.theme.Muted
import app.buffer.ui.theme.OnAccent
import app.buffer.ui.theme.Surface
import app.buffer.ui.theme.Warn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class Summary(
    val attemptsToday: Int = 0,
    val declinedToday: Int = 0,
    val savedMinutes: Int = 0,
    val perApp24h: Map<String, Int> = emptyMap(),
)

@Composable
fun Dashboard(apps: List<AppInfo>) {
    val context = LocalContext.current
    val config by Store.config.collectAsStateWithLifecycle()
    val changes by Attempts.changes.collectAsStateWithLifecycle()
    val health = rememberHealth()
    val summary by produceState(Summary(), changes, config.minutesPerOpen) {
        value = withContext(Dispatchers.IO) {
            val start = startOfToday()
            Summary(
                attemptsToday = Attempts.count(null, start),
                declinedToday = Attempts.declined(null, start),
                savedMinutes = Attempts.declined(null, 0) * config.minutesPerOpen,
                perApp24h = Attempts.countsByPackage(System.currentTimeMillis() - Attempts.DAY_MS),
            )
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    val matching = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    val blocked = matching.filter { it.pkg in config.enabled }
    val others = matching.filter { it.pkg !in config.enabled }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .systemBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Buffer", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (health.accessibility) Accent else Warn, CircleShape),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (health.accessibility) "On" else "Off",
                        color = Muted,
                        fontSize = 14.sp,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(summary.declinedToday.toString(), color = Ink, fontSize = 72.sp, fontWeight = FontWeight.Bold)
            Text(
                if (summary.declinedToday == 1) "time you chose not to open something today."
                else "times you chose not to open something today.",
                color = Ink,
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${summary.attemptsToday} attempts today · ${formatMinutes(summary.savedMinutes)} kept in total",
                color = Muted,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(24.dp))
        }

        if (!health.ok) {
            item {
                SetupCard(health)
                Spacer(Modifier.height(24.dp))
            }
        }

        item {
            SectionLabel("Buffer length")
            LabeledSlider(
                title = "Default wait before any app opens",
                valueText = { "$it s" },
                value = config.defaultSeconds,
                range = 3..60,
                onCommit = Store::setDefaultSeconds,
            )
            Spacer(Modifier.height(8.dp))
            LabeledSlider(
                title = "Time kept per refusal",
                valueText = { "$it min" },
                value = config.minutesPerOpen,
                range = 1..30,
                onCommit = Store::setMinutesPerOpen,
            )
            Text(
                "Used for the “kept” totals. Each time you walk away, this many minutes are counted as saved.",
                color = Muted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(28.dp))
        }

        item {
            SectionLabel("Apps")
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search apps", color = Muted) },
                shape = RoundedCornerShape(16.dp),
            )
            TextButton(onClick = {
                Store.setEnabled(apps.map { it.pkg }.filter { it in SOCIAL_PACKAGES }, true)
            }) {
                Text("Add social media apps", color = Accent)
            }
        }

        if (blocked.isNotEmpty()) {
            item { SectionLabel("Buffered (${blocked.size})") }
            items(blocked, key = { "b_" + it.pkg }) { app ->
                AppRow(
                    app = app,
                    on = true,
                    subtitle = "${config.secondsFor(app.pkg)} s · ${summary.perApp24h[app.pkg] ?: 0} in 24 h",
                    expanded = expanded == app.pkg,
                    onToggleExpand = { expanded = if (expanded == app.pkg) null else app.pkg },
                    onSwitch = { Store.setEnabled(listOf(app.pkg), it) },
                ) {
                    val override = config.perApp[app.pkg]
                    LabeledSlider(
                        title = "Buffer length for ${app.label}",
                        valueText = { "$it s" },
                        value = config.secondsFor(app.pkg),
                        range = 3..60,
                        onCommit = { Store.setAppSeconds(app.pkg, it) },
                    )
                    if (override != null) {
                        TextButton(onClick = { Store.setAppSeconds(app.pkg, null) }) {
                            Text("Use default (${config.defaultSeconds} s)", color = Accent)
                        }
                    }
                }
            }
        }

        item { SectionLabel("All apps") }
        items(others, key = { "o_" + it.pkg }) { app ->
            AppRow(
                app = app,
                on = false,
                subtitle = null,
                expanded = false,
                onToggleExpand = {},
                onSwitch = { Store.setEnabled(listOf(app.pkg), it) },
            ) {}
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Muted,
        fontSize = 12.sp,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun SetupCard(health: Health) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Text("Finish setup", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        if (!health.accessibility) {
            SetupRow("Accessibility service", "Required. Without it nothing is blocked.") {
                context.startActivity(accessibilityIntent())
            }
        }
        if (!health.overlay) {
            SetupRow("Display over other apps", "Recommended. Lets the buffer appear reliably.") {
                context.startActivity(overlayIntent(context))
            }
        }
        if (!health.battery) {
            SetupRow("Unrestricted battery", "Recommended. Stops the system from killing Buffer.") {
                context.startActivity(batteryIntent())
            }
        }
    }
}

@Composable
private fun SetupRow(title: String, detail: String, onFix: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 15.sp)
            Text(detail, color = Muted, fontSize = 13.sp)
        }
        TextButton(onClick = onFix) { Text("Open", color = Accent) }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    on: Boolean,
    subtitle: String?,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSwitch: (Boolean) -> Unit,
    expandedContent: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = on, onClick = onToggleExpand)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, color = Ink, fontSize = 16.sp)
                if (subtitle != null) Text(subtitle, color = Muted, fontSize = 13.sp)
            }
            Switch(
                checked = on,
                onCheckedChange = onSwitch,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnAccent,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = Muted,
                    uncheckedTrackColor = Surface,
                    uncheckedBorderColor = Line,
                ),
            )
        }
        if (expanded) {
            Column(Modifier.padding(start = 56.dp, bottom = 8.dp)) { expandedContent() }
        }
    }
}

@Composable
private fun LabeledSlider(
    title: String,
    valueText: (Int) -> String,
    value: Int,
    range: IntRange,
    onCommit: (Int) -> Unit,
) {
    var live by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(valueText(live.roundToInt()), color = Accent, fontSize = 15.sp)
        }
        Slider(
            value = live,
            onValueChange = { live = it },
            onValueChangeFinished = { onCommit(live.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent,
                inactiveTrackColor = Line,
            ),
        )
    }
}
