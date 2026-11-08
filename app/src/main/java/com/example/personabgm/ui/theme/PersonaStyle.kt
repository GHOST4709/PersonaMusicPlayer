package com.example.personabgm.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.personabgm.R
import com.example.personabgm.domain.model.GameTheme

@Immutable
data class PersonaShapes(
    val card: Shape,
    val button: Shape,
    val badge: Shape,
)

/** Everything that is not a color animation: identity, copy, type, and shapes for one theme. */
@Immutable
data class PersonaStyle(
    val theme: GameTheme,
    val displayName: String,
    val tagline: String,
    /** Full-screen copy shown while the track manifest loads (spec 3.2). */
    val loadingMessage: String,
    val palette: PersonaPalette,
    val typography: Typography,
    val shapes: PersonaShapes,
)

object PersonaStyles {

    // Fonts are bundled (SIL Open Font License; see assets/licenses). Only headings, titles, and labels use them;
    // body text stays on the system font so long copy stays easy to read at any font size.
    private val rajdhani = FontFamily(
        Font(R.font.rajdhani_medium, FontWeight.Medium),
        Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
        Font(R.font.rajdhani_bold, FontWeight.Bold),
    )
    private val lilitaOne = FontFamily(Font(R.font.lilita_one_regular, FontWeight.Normal))
    private val anton = FontFamily(Font(R.font.anton_regular, FontWeight.Normal))

    // Persona 3 Reload: sleek liquid cutouts. Asymmetric corners suggest a water-cut panel.
    private val persona3 = PersonaStyle(
        theme = GameTheme.PERSONA_3_RELOAD,
        displayName = "Persona 3 Reload",
        tagline = "Liquid cutouts, dark aquatic gradients",
        loadingMessage = "Connecting to Metaverse...",
        palette = PersonaPalettes.Persona3Reload,
        typography = personaTypography(
            headingFamily = rajdhani,
            headingWeight = FontWeight.SemiBold,
            emphasisWeight = FontWeight.Bold,
            headingStyle = FontStyle.Normal,
            headingSpacing = 1.sp,
            bodySpacing = 0.3.sp,
        ),
        shapes = PersonaShapes(
            card = RoundedCornerShape(topStart = 28.dp, topEnd = 8.dp, bottomEnd = 28.dp, bottomStart = 8.dp),
            button = RoundedCornerShape(percent = 50),
            badge = CircleShape,
        ),
    )

    // Persona 4 Revival: pop-art badges, CRT lines, halftone dots. Lilita One has a single heavy weight, so every
    // heading weight is Normal (asking for Bold would make Compose fake a second, smeared bold).
    private val persona4 = PersonaStyle(
        theme = GameTheme.PERSONA_4_REVIVAL,
        displayName = "Persona 4 Revival",
        tagline = "CRT lines, pop badges, halftone dots",
        loadingMessage = "Tuning TV...",
        palette = PersonaPalettes.Persona4Revival,
        typography = personaTypography(
            headingFamily = lilitaOne,
            headingWeight = FontWeight.Normal,
            emphasisWeight = FontWeight.Normal,
            headingStyle = FontStyle.Normal,
            headingSpacing = 0.sp,
            bodySpacing = 0.sp,
        ),
        shapes = PersonaShapes(
            card = RoundedCornerShape(18.dp),
            button = RoundedCornerShape(14.dp),
            badge = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
        ),
    )

    // Persona 5 Royal: slanted diagonal type, comic cutouts. Anton is condensed and heavy; the slant is the
    // standard synthetic italic.
    private val persona5 = PersonaStyle(
        theme = GameTheme.PERSONA_5_ROYAL,
        displayName = "Persona 5 Royal",
        tagline = "Slanted type, comic cutouts",
        loadingMessage = "Connecting to Metaverse...",
        palette = PersonaPalettes.Persona5Royal,
        typography = personaTypography(
            headingFamily = anton,
            headingWeight = FontWeight.Normal,
            emphasisWeight = FontWeight.Normal,
            headingStyle = FontStyle.Italic,
            headingSpacing = 0.5.sp,
            bodySpacing = 0.2.sp,
        ),
        shapes = PersonaShapes(
            card = SlantedShape(slant = 0.10f),
            button = SlantedShape(slant = 0.35f),
            badge = SlantedShape(slant = 0.30f),
        ),
    )

    val all: List<PersonaStyle> = listOf(persona3, persona4, persona5)

    fun of(theme: GameTheme): PersonaStyle = when (theme) {
        GameTheme.PERSONA_3_RELOAD -> persona3
        GameTheme.PERSONA_4_REVIVAL -> persona4
        GameTheme.PERSONA_5_ROYAL -> persona5
    }
}

/**
 * Builds a Typography whose display, headline, title, and label styles use [headingFamily] and whose body
 * styles keep the system font.
 */
private fun personaTypography(
    headingFamily: FontFamily,
    headingWeight: FontWeight,
    emphasisWeight: FontWeight,
    headingStyle: FontStyle,
    headingSpacing: TextUnit,
    bodySpacing: TextUnit,
): Typography {
    val base = Typography()

    fun TextStyle.heading() = copy(
        fontFamily = headingFamily,
        fontWeight = headingWeight,
        fontStyle = headingStyle,
        letterSpacing = headingSpacing,
    )

    fun TextStyle.emphasis() = copy(
        fontFamily = headingFamily,
        fontWeight = emphasisWeight,
        letterSpacing = bodySpacing,
    )

    fun TextStyle.body() = copy(fontFamily = FontFamily.SansSerif, letterSpacing = bodySpacing)

    return base.copy(
        displaySmall = base.displaySmall.heading(),
        headlineLarge = base.headlineLarge.heading(),
        headlineMedium = base.headlineMedium.heading(),
        headlineSmall = base.headlineSmall.heading(),
        titleLarge = base.titleLarge.heading(),
        titleMedium = base.titleMedium.emphasis(),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.emphasis(),
        labelMedium = base.labelMedium.emphasis(),
    )
}
