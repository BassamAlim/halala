package bassamalim.halala.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** The radius tokens. */
object Radius {
    /** radius-xs: the auto badge. */
    val xs = RoundedCornerShape(6.dp)
    /** radius-sm: avatars, inset evidence boxes, segmented control, inputs inside rows. */
    val sm = RoundedCornerShape(12.dp)
    /** radius-md: buttons, search field. */
    val md = RoundedCornerShape(14.dp)
    /** radius-lg: cards and the balance card. */
    val lg = RoundedCornerShape(16.dp)
    /** The selected segment inside a [sm] segmented control: its radius less the 3dp inset. */
    val segment = RoundedCornerShape(9.dp)
    /** The progress tracks. */
    val bar = RoundedCornerShape(percent = 50)
    /** radius-pill: chips, status pills. */
    val pill = RoundedCornerShape(percent = 50)
}

/** The same tokens on Material's slots, for the M3 components that read them (dialogs, sheets). */
val Shapes = Shapes(
    extraSmall = Radius.xs,
    small = Radius.sm,
    medium = Radius.md,
    large = Radius.lg,
    extraLarge = Radius.lg
)
