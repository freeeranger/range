package dev.freeranger.range.lift.ui.common

import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.ProgressMetric

/** What a [ProgressMetric] is called: "Estimated 1RM", "Most reps". */
val ProgressMetric.title: Int
    get() = when (this) {
        ProgressMetric.OneRepMax -> R.string.progress_one_rep_max
        ProgressMetric.Weight -> R.string.progress_weight
        ProgressMetric.Reps -> R.string.progress_reps
        ProgressMetric.Time -> R.string.progress_time
        ProgressMetric.Assistance -> R.string.progress_assistance
    }

/** A line on what a [ProgressMetric] measures, under its chart. */
val ProgressMetric.hint: Int
    get() = when (this) {
        ProgressMetric.OneRepMax -> R.string.progress_one_rep_max_hint
        ProgressMetric.Weight -> R.string.progress_weight_hint
        ProgressMetric.Reps -> R.string.progress_reps_hint
        ProgressMetric.Time -> R.string.progress_time_hint
        ProgressMetric.Assistance -> R.string.progress_assistance_hint
    }
