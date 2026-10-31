package app.thermida.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.thermida.domain.SmartSchedule
import app.thermida.ui.components.ThermidaCard
import app.thermida.ui.components.format
import app.thermida.ui.theme.Numeric

/**
 * Says the smart target just changed the calorie target, by how much, and why, until it's been
 * seen. Without it the number on the Diary would move with no explanation.
 */
@Composable
fun SmartTargetNotice(change: SmartSchedule.Change, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    ThermidaCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Psychology, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Smart target updated", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${change.oldKcal.format()} → ${change.newKcal.format()} kcal a day",
                    style = MaterialTheme.typography.bodyMedium.merge(Numeric),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            if (change.up) {
                "Your meals and weigh-ins show you burn a little more than estimated, so your target went up."
            } else {
                "Your meals and weigh-ins show you burn a little less than estimated, so your target came down."
            } + " It changes at most once a week, in small steps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Got it") }
    }
}
