package com.ledger.app.ui.theme

import androidx.compose.ui.graphics.Color

// Primary — Forest Green
val Primary = Color(0xFF00513F)
val PrimaryContainer = Color(0xFF006B54)
val OnPrimary = Color(0xFFFFFFFF)
val OnPrimaryContainer = Color(0xFF94E8CB)
val PrimaryFixed = Color(0xFF9EF3D6)
val PrimaryFixedDim = Color(0xFF82D7BA)
val InversePrimary = Color(0xFF82D7BA)

// Secondary
val Secondary = Color(0xFF5C5F5D)
val SecondaryContainer = Color(0xFFE1E3E1)
val OnSecondary = Color(0xFFFFFFFF)
val OnSecondaryContainer = Color(0xFF626563)

// Tertiary — Alert Red (surgical use only)
val Tertiary = Color(0xFF920009)
val TertiaryContainer = Color(0xFFB91919)
val OnTertiary = Color(0xFFFFFFFF)
val OnTertiaryContainer = Color(0xFFFFCBC5)

// Error
val Error = Color(0xFFBA1A1A)
val ErrorContainer = Color(0xFFFFDAD6)
val OnError = Color(0xFFFFFFFF)
val OnErrorContainer = Color(0xFF93000A)

// Surface hierarchy (The "No-Line" Rule)
val Background = Color(0xFFF8FAF5)          // Level 0
val Surface = Color(0xFFF8FAF5)
val SurfaceContainerLowest = Color(0xFFFFFFFF)  // Level 3 floating
val SurfaceContainerLow = Color(0xFFF2F4EF)     // Level 1 sections
val SurfaceContainer = Color(0xFFECEFEA)
val SurfaceContainerHigh = Color(0xFFE7E9E4)
val SurfaceContainerHighest = Color(0xFFE1E3DE)  // Level 2 interactive cards
val SurfaceBright = Color(0xFFF8FAF5)
val SurfaceDim = Color(0xFFD8DBD6)
val SurfaceTint = Color(0xFF016B54)
val SurfaceVariant = Color(0xFFE1E3DE)
val InverseSurface = Color(0xFF2E312E)
val InverseOnSurface = Color(0xFFEFF1EC)

// On surfaces
val OnBackground = Color(0xFF191C1A)
val OnSurface = Color(0xFF191C1A)       // Never pure black
val OnSurfaceVariant = Color(0xFF3E4944)

// Outline
val Outline = Color(0xFF6F7A74)
val OutlineVariant = Color(0xFFBEC9C3)  // Ghost border at 15% opacity

// Income / Expense pill indicators
val IncomePill = Primary
val ExpensePill = Tertiary
