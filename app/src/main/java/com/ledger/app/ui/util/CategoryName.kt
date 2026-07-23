package com.ledger.app.ui.util

// Categories display with a capitalized first letter and are de-duplicated case-insensitively.
//
// capitalizeFirst is for LIVE text input — it must NOT trim, otherwise a trailing space can never
// be typed (so multi-word names like "Fast food" stay possible). normalizeCategoryName is the
// authoritative form applied when a category is saved/created.

fun capitalizeFirst(s: String): String =
    s.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

fun normalizeCategoryName(raw: String): String = capitalizeFirst(raw.trim())
