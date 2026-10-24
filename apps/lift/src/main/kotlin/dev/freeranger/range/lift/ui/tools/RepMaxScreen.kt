package dev.freeranger.range.lift.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.RepMax
import dev.freeranger.range.lift.ui.common.rememberLiftFormat
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import kotlin.math.roundToInt

/**
 * From any set to any other: the weight and reps of one set give what could be lifted for 1 to
 * 12 reps, each with its share of the one-rep max. The reps typed in are highlighted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepMaxScreen(onBack: () -> Unit, initialWeight: String = "", initialReps: String = "") {
    val format = rememberLiftFormat()
    var weight by rememberSaveable { mutableStateOf(initialWeight) }
    var reps by rememberSaveable { mutableStateOf(initialReps) }
    val kg = format.parseWeight(weight)?.takeIf { it > 0 }
    val count = reps.toIntOrNull()?.takeIf { it > 0 }
    val max = if (kg != null && count != null) RepMax.oneRepMax(kg, count) else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rep_max_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(RangeIcons.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + RangeSpace.s4),
        ) {
            item(key = "set") {
                Row(
                    Modifier.padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2),
                    horizontalArrangement = Arrangement.spacedBy(RangeSpace.s3),
                ) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() || c == '.' || c == ',' }) weight = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.unit_kg_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) reps = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.measure_reps)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    )
                }
            }
            if (max != null) {
                item(key = "table") {
                    Card(Modifier.fillMaxWidth().padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2)) {
                        Column(Modifier.padding(vertical = RangeSpace.s2)) {
                            RepMax.REPS.forEach { n -> RepRow(n, RepMax.rounded(RepMax.weightFor(max, n)), RepMax.weightFor(max, n) / max, n == count, format::kg, format::reps) }
                        }
                    }
                }
            }
        }
    }
}

/** "5 reps   84 kg   86%", highlighted when it's the set typed in. */
@Composable
private fun RepRow(reps: Int, kg: Double, share: Double, typed: Boolean, weight: (Double) -> String, label: (Int) -> String) {
    val colors = MaterialTheme.colorScheme
    val text = if (typed) colors.onPrimaryContainer else colors.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = RangeSpace.s2)
            .background(if (typed) colors.primaryContainer else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(horizontal = RangeSpace.s3, vertical = RangeSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label(reps), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = text)
        Text(
            weight(kg),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = text,
            textAlign = TextAlign.End,
        )
        Text(
            "${(share * 100).roundToInt()}%",
            Modifier.width(56.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = if (typed) colors.onPrimaryContainer else colors.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}
