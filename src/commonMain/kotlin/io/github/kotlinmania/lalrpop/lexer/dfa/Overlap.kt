// port-lint: source src/lexer/dfa/overlap.rs
/**
 * When we are combining two NFAs, we will grab all the outgoing
 * edges from a set of nodes and wind up with a bunch of potentially
 * overlapping character ranges like:
 *
 * ```text
 *     a-z
 *     c-l
 *     0-9
 * ```
 *
 * This module contains code to turn those into non-overlapping ranges like:
 *
 * ```text
 *     a-b
 *     c-l
 *     m-z
 *     0-9
 * ```
 *
 * Specifically, we want to ensure that the same set of characters is
 * covered when we started, and that each of the input ranges is
 * covered precisely by some set of ranges in the output.
 */
package io.github.kotlinmania.lalrpop.lexer.dfa

import io.github.kotlinmania.lalrpop.collections.set.Set
import io.github.kotlinmania.lalrpop.lexer.nfa.Test

fun removeOverlap(ranges: Set<Test>): List<Test> {
    // We will do this in the dumbest possible way to start. :)
    // Maintain a result vector that contains disjoint ranges.  To
    // insert a new range, we walk over this vector and split things
    // up as we go. This algorithm is so naive as to be exponential, I
    // think. Sue me.

    val disjointRanges: MutableList<Test> = mutableListOf()

    for (range in ranges) {
        addRange(range, 0, disjointRanges)
    }

    // the algorithm above leaves some empty ranges in for simplicity;
    // prune them out.
    disjointRanges.removeAll { it.isEmpty() }

    disjointRanges.sort()

    return disjointRanges
}

private fun addRange(range: Test, startIndex: Int, disjointRanges: MutableList<Test>) {
    if (range.isEmpty()) {
        return
    }

    // Find first overlapping range in `disjoint_ranges`, if any.
    val relativeIndex = (startIndex until disjointRanges.size).firstOrNull { i ->
        disjointRanges[i].intersects(range)
    }

    if (relativeIndex != null) {
        val index = relativeIndex
        val overlappingRange = disjointRanges[index]

        // If the range we are trying to add already exists, we're all done.
        if (overlappingRange == range) {
            return
        }

        // Otherwise, we want to create three ranges (some of which may
        // be empty). e.g. imagine one range is `a-z` and the other
        // is `c-l`, we want `a-b`, `c-l`, and `m-z`.
        val minMin = minOf(range.start(), overlappingRange.start())
        val midMin = maxOf(range.start(), overlappingRange.start())
        val midMax = minOf(range.end(), overlappingRange.end())
        val maxMax = maxOf(range.end(), overlappingRange.end())
        // When working with inclusive ranges, we need to be sure to not double count
        // the meeting points of low-mid_range and mid-max_range.
        // So we adjust the end of the low_range and start of max_range as these elements are already included in the start of their corresponding next ranges.

        val lowRange = if (midMin == 0u) {
            // This is an edgecase where both ranges start at the null character
            // In this case we don't want to create a range from 0 to -1
            // Thus we create an empty range
            Test.new(1u, 0u)
        } else {
            Test.new(minMin, midMin - 1u)
        }
        val midRange = Test.new(midMin, midMax)
        val maxRange = Test.new(midMax + 1u, maxMax)

        check(lowRange.isDisjoint(midRange))
        check(lowRange.isDisjoint(maxRange))
        check(midRange.isDisjoint(maxRange))

        // Replace the existing range with the low range, and then
        // add the mid and max ranges in. (The low range may be
        // empty, but we'll prune that out later.)
        disjointRanges[index] = lowRange
        addRange(midRange, index + 1, disjointRanges)
        addRange(maxRange, index + 1, disjointRanges)
    } else {
        // no overlap -- easy case.
        disjointRanges.add(range)
    }
}
