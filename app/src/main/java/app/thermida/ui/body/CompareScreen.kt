package app.thermida.ui.body

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.thermida.domain.BodyReport
import app.thermida.domain.Metric
import app.thermida.ui.components.RoundIconButton
import app.thermida.ui.components.ThermidaCard
import app.thermida.ui.components.format
import app.thermida.ui.theme.Numeric
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** The measurements compared, in this order. Weight and BMI head the list, then composition. */
private val COMPARED = listOf(
    Metric.WEIGHT, Metric.BMI, Metric.FAT, Metric.FAT_MASS, Metric.MUSCLE_MASS, Metric.SKELETAL,
    Metric.WATER, Metric.PROTEIN, Metric.LEAN, Metric.BONE_MASS, Metric.VISCERAL, Metric.BMR, Metric.BODY_AGE,
)

/** Which way is better: +1 higher, -1 lower, 0 neither (BMR and bone mostly follow weight, or barely move). */
private fun betterWay(metric: Metric, losing: Boolean): Int = when (metric) {
    Metric.WEIGHT, Metric.BMI -> if (losing) -1 else 1
    Metric.FAT, Metric.FAT_MASS, Metric.VISCERAL, Metric.BODY_AGE -> -1
    Metric.MUSCLE, Metric.MUSCLE_MASS, Metric.SKELETAL, Metric.WATER, Metric.PROTEIN, Metric.LEAN -> 1
    else -> 0
}

private enum class Verdict { BETTER, WORSE, SAME }

private data class Compared(val metric: Metric, val then: Double, val now: Double, val verdict: Verdict)

private fun compare(then: BodyReport, now: BodyReport, losing: Boolean): List<Compared> = COMPARED.mapNotNull { metric ->
    val a = then.value(metric) ?: return@mapNotNull null
    val b = now.value(metric) ?: return@mapNotNull null
    val step = if (metric.decimals == 0) 0.5 else 0.05
    val way = betterWay(metric, losing)
    val verdict = when {
        abs(b - a) < step || way == 0 -> Verdict.SAME
        (b - a) * way > 0 -> Verdict.BETTER
        else -> Verdict.WORSE
    }
    Compared(metric, a, b, verdict)
}

/**
 * Two body-composition measurements side by side: every metric then and now, which ones got
 * better and which got worse. Either side can be any other measurement, picked on a calendar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompareScreen(scans: List<ScanEntry>, startThen: Int, startNow: Int, losing: Boolean, onClose: () -> Unit) {
    var thenIndex by rememberSaveable { mutableIntStateOf(startThen.coerceIn(scans.indices)) }
    var nowIndex by rememberSaveable { mutableIntStateOf(startNow.coerceIn(scans.indices)) }
    // Which side the calendar is choosing for, or null when it's closed.
    var picking by remember { mutableStateOf<Boolean?>(null) }
    // Keep "then" the older of the two, whichever side was changed.
    val older = scans[minOf(thenIndex, nowIndex)]
    val newer = scans[maxOf(thenIndex, nowIndex)]
    val rows = remember(older, newer, losing) { compare(older.report, newer.report, losing) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundIconButton(Icons.Rounded.Close, "Close", onClose)
                    Spacer(Modifier.width(14.dp))
                    Text("Compare", style = MaterialTheme.typography.headlineSmall)
                }
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ThermidaCard(padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SidePicker("Then", older, Modifier.weight(1f)) { picking = true }
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .size(20.dp),
                            )
                            SidePicker("Now", newer, Modifier.weight(1f)) { picking = false }
                        }
                        val a = older.report.score
                        val b = newer.report.score
                        if (a != null && b != null) {
                            Spacer(Modifier.height(14.dp))
                            ScoreLine(a, b)
                        }
                        Spacer(Modifier.height(14.dp))
                        Summary(rows)
                    }
                    ThermidaCard(padding = 0.dp) {
                        Row(
                            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Body composition", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("Then", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 64.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 76.dp))
                        }
                        rows.forEachIndexed { i, row ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 18.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            CompareRow(row)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        "Green got better and orange got worse for your goal. Bone and BMR mostly follow your weight, so they aren't judged. Single readings swing with water, so look for changes over a few measurements.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }

        picking?.let { forThen ->
            val current = if (forThen) older else newer
            val other = if (forThen) newer else older
            val days = remember(scans) { scans.map { it.date }.toSet() }
            val state = rememberDatePickerState(
                initialSelectedDateMillis = current.date.toUtcMillis(),
                yearRange = scans.first().date.year..scans.last().date.year,
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) =
                        Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() in days
                    override fun isSelectableYear(year: Int) = year in scans.first().date.year..scans.last().date.year
                },
            )
            DatePickerDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            // The newest measurement that day, other than the one on the other side.
                            val pick = scans.indices.lastOrNull { scans[it].date == day && scans[it] !== other }
                            if (pick != null) {
                                val otherIndex = scans.indexOf(other)
                                if (forThen) {
                                    thenIndex = pick; nowIndex = otherIndex
                                } else {
                                    nowIndex = pick; thenIndex = otherIndex
                                }
                            }
                        }
                        picking = null
                    }) { Text("Compare") }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
            ) {
                DatePicker(
                    state = state,
                    title = {
                        Text(
                            if (forThen) "Compare from" else "Compare to",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        )
                    },
                    showModeToggle = false,
                )
            }
        }
    }
}

private fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** One side of the comparison: its date and time, tapped to choose another day. */
@Composable
private fun SidePicker(label: String, entry: ScanEntry, modifier: Modifier, onClick: () -> Unit) {
    val time = Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).toLocalTime()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.CalendarMonth, "Choose a day", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(2.dp))
            Text(entry.date.format(DateTimeFormatter.ofPattern("EEE, MMM d")), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                time.format(DateTimeFormatter.ofPattern("HH:mm")) + " · " + entry.report.weightKg.format(1) + " kg",
                style = MaterialTheme.typography.labelSmall.merge(Numeric),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Body score 28 → 30  +2", coloured by which way it went. */
@Composable
private fun ScoreLine(then: Int, now: Int) {
    val change = now - then
    val color = when {
        change > 0 -> ToneColors.good
        change < 0 -> ToneColors.high
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text("Body score", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text("$then", style = MaterialTheme.typography.titleMedium.merge(Numeric), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("  →  ", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$now", style = MaterialTheme.typography.headlineSmall.merge(Numeric))
        Spacer(Modifier.width(8.dp))
        Text(
            if (change == 0) "same" else (if (change > 0) "+" else "−") + abs(change),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold).merge(Numeric),
            color = color,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
}

/** How many metrics got better, worse, or stayed put, as three soft tiles. */
@Composable
private fun Summary(rows: List<Compared>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryTile(rows.count { it.verdict == Verdict.BETTER }, "better", ToneColors.good, Modifier.weight(1f))
        SummaryTile(rows.count { it.verdict == Verdict.WORSE }, "worse", ToneColors.high, Modifier.weight(1f))
        SummaryTile(rows.count { it.verdict == Verdict.SAME }, "steady", MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryTile(count: Int, label: String, tint: Color, modifier: Modifier) {
    // An empty tile stays quiet so the counts that matter stand out.
    val color = if (count == 0) MaterialTheme.colorScheme.onSurfaceVariant else tint
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = if (count == 0) 0.06f else 0.12f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$count", style = MaterialTheme.typography.titleLarge.merge(Numeric), color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** One metric: its icon and name with the change under it, then the value before and now. */
@Composable
private fun CompareRow(row: Compared) {
    val metric = row.metric
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val color = when (row.verdict) {
        Verdict.BETTER -> ToneColors.good
        Verdict.WORSE -> ToneColors.high
        Verdict.SAME -> quiet
    }
    val change = row.now - row.then
    val step = if (metric.decimals == 0) 0.5 else 0.05
    val unit = if (metric.unit.isEmpty()) "" else " ${metric.unit}"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = MetricIcons.tint(metric)
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MetricIcons.of(metric), null, Modifier.size(17.dp), tint = tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(metric.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (abs(change) < step) "no change" else (if (change > 0) "▲ " else "▼ ") + abs(change).format(metric.decimals) + unit,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold).merge(Numeric),
                color = color,
            )
        }
        Text(
            row.then.format(metric.decimals),
            style = MaterialTheme.typography.bodyMedium.merge(Numeric),
            color = quiet,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 64.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            row.now.format(metric.decimals) + unit,
            style = MaterialTheme.typography.titleSmall.merge(Numeric),
            color = if (row.verdict == Verdict.SAME) MaterialTheme.colorScheme.onSurface else color,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 76.dp),
        )
    }
}
