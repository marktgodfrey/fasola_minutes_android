package org.fasola.minutes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.fasola.minutes.data.SongSummary
import kotlin.math.roundToInt

internal data class SongFilter(
    val pageLow: Int = MIN_PAGE,
    val pageHigh: Int = MAX_2025_PAGE,
    val positions: Set<String> = emptySet(),
    val sides: Set<String> = emptySet(),
    val times: Set<String> = emptySet(),
    val modes: Set<String> = emptySet(),
    val keys: Set<String> = emptySet(),
    val meters: Set<String> = emptySet(),
) {
    fun isActive(maximumPage: Int): Boolean =
        pageLow > MIN_PAGE || pageHigh < maximumPage ||
            positions.isNotEmpty() || sides.isNotEmpty() || times.isNotEmpty() ||
            modes.isNotEmpty() || keys.isNotEmpty() || meters.isNotEmpty()

    fun forMaximumPage(maximumPage: Int): SongFilter = copy(pageHigh = pageHigh.coerceAtMost(maximumPage))

    fun matches(song: SongSummary): Boolean {
        val pageNumber = song.page.takeWhile(Char::isDigit).toIntOrNull() ?: return false
        if (pageNumber !in pageLow..pageHigh) return false
        if (positions.isNotEmpty() && song.orientation.lowercase() !in positions) return false
        val side = if (pageNumber % 2 == 0) "Left" else "Right"
        if (sides.isNotEmpty() && side !in sides) return false

        val songKeys = splitValues(song.keys).map(::normalizeKey)
        val songTimes = splitValues(song.times)
        if (times.isNotEmpty() && songTimes.none(times::contains)) return false
        val songKeyNames = songKeys.map { it.removeSuffix(" Minor").removeSuffix(" Major") }
        val selectedKeys = keys.map(::normalizeKey)
        if (keys.isNotEmpty() && songKeyNames.none { it in selectedKeys }) return false
        if (meters.isNotEmpty() && song.meter !in meters) return false

        val hasMinor = songKeys.any { it.endsWith(" Minor") }
        val hasMajor = songKeys.any { !it.endsWith(" Minor") }
        if (modes == setOf("Major") && !hasMajor) return false
        if (modes == setOf("Minor") && !hasMinor) return false
        return true
    }

    companion object {
        const val MIN_PAGE = 26
        const val MAX_2025_PAGE = 575
        const val MAX_1991_PAGE = 573
    }
}

private fun splitValues(value: String) = value.split(',', ';').map(String::trim).filter(String::isNotEmpty)
private fun normalizeKey(value: String) = value.replace("♭", "b").replace("♯", "#")

@Composable
internal fun SongFilterDialog(
    current: SongFilter,
    maximumPage: Int,
    dismiss: () -> Unit,
    apply: (SongFilter) -> Unit,
) {
    var draft by remember(current, maximumPage) { mutableStateOf(current.forMaximumPage(maximumPage)) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Filter Songs") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Page Range")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(draft.pageLow.toString())
                    Text(draft.pageHigh.toString())
                }
                RangeSlider(
                    value = draft.pageLow.toFloat()..draft.pageHigh.toFloat(),
                    onValueChange = { draft = draft.copy(pageLow = it.start.roundToInt(), pageHigh = it.endInclusive.roundToInt()) },
                    valueRange = SongFilter.MIN_PAGE.toFloat()..maximumPage.toFloat(),
                    steps = maximumPage - SongFilter.MIN_PAGE - 1,
                )
                FilterGroup("Page Position", listOf("Full" to "full", "Top" to "top", "Bottom" to "bottom"), draft.positions) {
                    draft = draft.copy(positions = it)
                }
                FilterGroup("Page Side", listOf("Left" to "Left", "Right" to "Right"), draft.sides) {
                    draft = draft.copy(sides = it)
                }
                FilterGroup("Time", listOf("2/2", "4/4", "2/4", "6/4", "6/8", "3/2", "3/4").map { it to it }, draft.times) {
                    draft = draft.copy(times = it)
                }
                FilterGroup("Mode", listOf("Major", "Minor").map { it to it }, draft.modes) {
                    draft = draft.copy(modes = it)
                }
                FilterGroup("Key", listOf("A", "Bb", "B", "C", "C#", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab").map { displayKey(it) to it }, draft.keys) {
                    draft = draft.copy(keys = it)
                }
                FilterGroup("Meter", meterOptions, draft.meters) { draft = draft.copy(meters = it) }
            }
        },
        confirmButton = { TextButton({ apply(draft); dismiss() }) { Text("Apply") } },
        dismissButton = {
            Row {
                TextButton({ draft = SongFilter(pageHigh = maximumPage) }) { Text("Reset") }
                TextButton(dismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun FilterGroup(label: String, options: List<Pair<String, String>>, selected: Set<String>, update: (Set<String>) -> Unit) {
    Column {
        Text(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (display, value) ->
                FilterChip(
                    selected = value in selected,
                    onClick = { update(if (value in selected) selected - value else selected + value) },
                    label = { Text(display) },
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

private fun displayKey(key: String) = key.replace("b", "♭").replace("#", "♯")

private val meterOptions = listOf(
    "C.M." to "Common Meter", "C.M.D" to "Common Meter Double",
    "L.M." to "Long Meter", "L.M.D" to "Long Meter Double", "L.M.H" to "Long Meter Half",
    "S.M." to "Short Meter", "8s&7s" to "8s & 7s.", "8s&7sD." to "8s & 7s D.",
    "7s" to "7s.", "11s" to "11s.",
)
