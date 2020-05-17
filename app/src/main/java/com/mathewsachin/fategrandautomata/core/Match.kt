package com.mathewsachin.fategrandautomata.core

/**
 * Represents an image search match, containing the match area and the matching score.
 */
data class Match(val Region: Region, val score: Double)