package me.brosssh.bundles.workers.config

import org.semver4j.Semver
import org.semver4j.range.Range
import org.semver4j.range.Range.RangeOperator
import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

internal fun parseVersionRange(source: String): RangeList =
    RangeListFactory.create(source, true).also { rangeList ->
        require(rangeList.get().isNotEmpty() && rangeList.get().all { it.isNotEmpty() }) {
            "Invalid semantic version range: '$source'"
        }
        require(rangeList.get().all(List<Range>::isSatisfiable)) {
            "Unsatisfiable semantic version range: '$source'"
        }
    }

internal fun RangeList.overlaps(other: RangeList): Boolean =
    get().any { left ->
        other.get().any { right ->
            (left + right).isSatisfiable()
        }
    }

private data class Bound(
    val version: Semver,
    val inclusive: Boolean
)

private data class Interval(
    val lower: Bound? = null,
    val upper: Bound? = null
) {
    fun constrain(range: Range): Interval = when (range.operator) {
        RangeOperator.EQ -> copy(
            lower = tighterLower(lower, Bound(range.rangeVersion, true)),
            upper = tighterUpper(upper, Bound(range.rangeVersion, true))
        )

        RangeOperator.GT -> copy(lower = tighterLower(lower, Bound(range.rangeVersion, false)))
        RangeOperator.GTE -> copy(lower = tighterLower(lower, Bound(range.rangeVersion, true)))
        RangeOperator.LT -> copy(upper = tighterUpper(upper, Bound(range.rangeVersion, false)))
        RangeOperator.LTE -> copy(upper = tighterUpper(upper, Bound(range.rangeVersion, true)))
    }

    fun isSatisfiable(): Boolean {
        val lower = lower ?: return true
        val upper = upper ?: return true
        val comparison = lower.version.compareTo(upper.version)
        return comparison < 0 || (comparison == 0 && lower.inclusive && upper.inclusive)
    }
}

private fun List<Range>.isSatisfiable(): Boolean =
    fold(Interval()) { interval, range -> interval.constrain(range) }.isSatisfiable()

// semver4j 6.0 documents Range.toString() as the operator followed by the normalized version,
// but does not yet expose its operator through a public getter.
private val Range.operator: RangeOperator
    get() {
        val versionText = rangeVersion.toString()
        val rangeText = toString()
        require(rangeText.endsWith(versionText)) { "Unexpected semver4j range representation: '$rangeText'" }
        return RangeOperator.value(rangeText.dropLast(versionText.length))
    }

private fun tighterLower(current: Bound?, candidate: Bound): Bound {
    if (current == null) return candidate
    val comparison = candidate.version.compareTo(current.version)
    return when {
        comparison > 0 -> candidate
        comparison < 0 -> current
        else -> Bound(current.version, current.inclusive && candidate.inclusive)
    }
}

private fun tighterUpper(current: Bound?, candidate: Bound): Bound {
    if (current == null) return candidate
    val comparison = candidate.version.compareTo(current.version)
    return when {
        comparison < 0 -> candidate
        comparison > 0 -> current
        else -> Bound(current.version, current.inclusive && candidate.inclusive)
    }
}
