package dev.nextgen.mobile

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nextgen.mobile.design.resources.evidriloLogoDrawable
import dev.nextgen.mobile.design.resources.evidriloSourceSansBoldFont
import dev.nextgen.mobile.design.resources.evidriloSourceSansRegularFont
import dev.nextgen.mobile.design.resources.evidriloSourceSansSemiboldFont
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

/**
 * The full set of semantic color roles. Two instances exist — light and dark —
 * and the active one is published through [EvidriloColors] so every existing
 * `EvidriloColors.X` call site (including non-composable Canvas/DrawScope code)
 * keeps working without change.
 *
 * Role meaning:
 * - [canvas] page background; [card] the primary card surface; [surface] a
 *   subtle elevated fill; [white] literal on-accent white that stays white in
 *   both themes (used only on the Cobalt hero and gradients).
 */
public data class EvidriloColorScheme(
    val deepNavy: Color,
    val cobalt: Color,
    val cobaltBright: Color,
    val cobaltPressed: Color,
    val white: Color,
    val canvas: Color,
    val card: Color,
    val surface: Color,
    val paleBlue: Color,
    val atmosphere: Color,
    val patternBlue: Color,
    val patternCobalt: Color,
    val tint: Color,
    val teal: Color,
    val ink: Color,
    val slate: Color,
    val separator: Color,
    val outline: Color,
    val error: Color,
    val errorSurface: Color,
    val success: Color,
    val successSurface: Color,
    val warning: Color,
    val warningSurface: Color,
)

public val EvidriloLightColors: EvidriloColorScheme = EvidriloColorScheme(
    deepNavy = Color(0xFF0B1056),
    cobalt = Color(0xFF1558E8),
    cobaltBright = Color(0xFF2C86FF),
    cobaltPressed = Color(0xFF103EB4),
    white = Color(0xFFFFFFFF),
    // A clean white canvas with blue accents, matching the logo. Cards separate
    // from the canvas through 2dp borders and a pressable lip, not shadows.
    canvas = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    surface = Color(0xFFF6F8FC),
    paleBlue = Color(0xFFEAF3FF),
    atmosphere = Color(0xFFEFF6FF),
    patternBlue = Color(0xFFBFD7FF),
    patternCobalt = Color(0xFF2E7BFF),
    tint = Color(0xFFE2EEFF),
    teal = Color(0xFF1399B1),
    ink = Color(0xFF0B1056),
    slate = Color(0xFF536DA5),
    separator = Color(0xFFE0E6F0),
    outline = Color(0xFF8190A8),
    error = Color(0xFFB42318),
    errorSurface = Color(0xFFFFE9E7),
    success = Color(0xFF146C43),
    successSurface = Color(0xFFE7F5EC),
    warning = Color(0xFFC86D00),
    warningSurface = Color(0xFFFFF2D9),
)

// Dark palette: a deep navy canvas (not pure black) that stays in the Cobalt
// family, card surfaces lifted just enough to separate from the canvas, accents
// raised in lightness so they stay vivid without glare, and status surfaces
// desaturated so they read as tinted-dark rather than bright pastel.
public val EvidriloDarkColors: EvidriloColorScheme = EvidriloColorScheme(
    deepNavy = Color(0xFF0B1056),
    cobalt = Color(0xFF5B8DEF),
    cobaltBright = Color(0xFF7AA6F5),
    cobaltPressed = Color(0xFF3D6BC4),
    white = Color(0xFFFFFFFF),
    canvas = Color(0xFF0E1220),
    card = Color(0xFF1A2036),
    surface = Color(0xFF232B45),
    paleBlue = Color(0xFF26324F),
    atmosphere = Color(0xFF151B2E),
    patternBlue = Color(0xFF2B3A5E),
    patternCobalt = Color(0xFF3D6BC4),
    tint = Color(0xFF26324F),
    teal = Color(0xFF3FB6CE),
    ink = Color(0xFFEAF0FF),
    slate = Color(0xFF9DB0D8),
    separator = Color(0xFF2E3855),
    outline = Color(0xFF5A6890),
    error = Color(0xFFFF6B5E),
    errorSurface = Color(0xFF3A1D1B),
    success = Color(0xFF4ED08A),
    successSurface = Color(0xFF13351F),
    warning = Color(0xFFF0A94B),
    warningSurface = Color(0xFF3A2C12),
)

// The active scheme is a plain global so both composable and DrawScope code can
// read EvidriloColors.X. EvidriloTheme sets this before rendering children, and
// a theme switch recomposes the whole tree (keyed on the scheme) to re-read it.
internal var evidriloActiveColors: EvidriloColorScheme = EvidriloLightColors

public object EvidriloColors {
    val DeepNavy: Color get() = evidriloActiveColors.deepNavy
    val Cobalt: Color get() = evidriloActiveColors.cobalt
    val CobaltBright: Color get() = evidriloActiveColors.cobaltBright
    val CobaltPressed: Color get() = evidriloActiveColors.cobaltPressed
    val White: Color get() = evidriloActiveColors.white
    val Canvas: Color get() = evidriloActiveColors.canvas
    val Card: Color get() = evidriloActiveColors.card
    val Surface: Color get() = evidriloActiveColors.surface
    val PaleBlue: Color get() = evidriloActiveColors.paleBlue
    val Atmosphere: Color get() = evidriloActiveColors.atmosphere
    val PatternBlue: Color get() = evidriloActiveColors.patternBlue
    val PatternCobalt: Color get() = evidriloActiveColors.patternCobalt
    val Tint: Color get() = evidriloActiveColors.tint
    val Teal: Color get() = evidriloActiveColors.teal
    val Ink: Color get() = evidriloActiveColors.ink
    val Slate: Color get() = evidriloActiveColors.slate
    val Separator: Color get() = evidriloActiveColors.separator
    val Outline: Color get() = evidriloActiveColors.outline
    val Error: Color get() = evidriloActiveColors.error
    val ErrorSurface: Color get() = evidriloActiveColors.errorSurface
    val Success: Color get() = evidriloActiveColors.success
    val SuccessSurface: Color get() = evidriloActiveColors.successSurface
    val Warning: Color get() = evidriloActiveColors.warning
    val WarningSurface: Color get() = evidriloActiveColors.warningSurface
}

/** Shared geometry tokens for the current target shell. */
public object EvidriloTargetLayout {
    val ContentTopPadding = 28.dp
    val BrandLogoSize = 50.dp
    val SourcesLogoSize = 52.dp
    // The source artwork is intentionally compact enough to keep all three
    // input lanes discoverable on a normal phone viewport. The page remains
    // scrollable, but the primary action should not be hidden below the fold.
    val SourcesGraphicHeight = 158.dp
    val NavigationVisualOffset = 4.dp
}

public fun evidriloPrimaryButtonContentColor(enabled: Boolean): Color =
    if (enabled) EvidriloColors.White else EvidriloColors.Slate

@Composable
private fun evidriloFontFamily(): FontFamily = FontFamily(
    Font(evidriloSourceSansRegularFont, FontWeight.Normal),
    Font(evidriloSourceSansSemiboldFont, FontWeight.SemiBold),
    Font(evidriloSourceSansBoldFont, FontWeight.Bold),
)

@Composable
private fun evidriloTypography(): Typography {
    val fontFamily = evidriloFontFamily()
    // Friendly, confident hierarchy: bold, slightly compact headings so a page
    // reads at a glance, and roomy body text for the reasoning content itself.
    return Typography(
    displayLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        color = EvidriloColors.Ink,
    ),
    displayMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        color = EvidriloColors.Ink,
    ),
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        color = EvidriloColors.Ink,
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        color = EvidriloColors.Ink,
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = EvidriloColors.Ink,
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        color = EvidriloColors.Ink,
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        color = EvidriloColors.Ink,
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        color = EvidriloColors.Ink,
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        color = EvidriloColors.Ink,
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        color = EvidriloColors.Slate,
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = EvidriloColors.Slate,
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        color = EvidriloColors.Ink,
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = EvidriloColors.Slate,
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp,
        color = EvidriloColors.Slate,
    )
    )
}

/** How the app resolves light vs dark. SYSTEM follows the device setting. */
public enum class EvidriloThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

@Composable
public fun EvidriloTheme(
    mode: EvidriloThemeMode = EvidriloThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        EvidriloThemeMode.SYSTEM -> isSystemInDarkTheme()
        EvidriloThemeMode.LIGHT -> false
        EvidriloThemeMode.DARK -> true
    }
    val scheme = if (dark) EvidriloDarkColors else EvidriloLightColors
    // Publish the active scheme before children read EvidriloColors.X. Keying the
    // subtree on `dark` forces a full recompose on switch so cached reads refresh.
    evidriloActiveColors = scheme
    val material = if (dark) {
        darkColorScheme(
            primary = scheme.cobalt,
            onPrimary = scheme.white,
            primaryContainer = scheme.tint,
            onPrimaryContainer = scheme.ink,
            secondary = scheme.slate,
            onSecondary = scheme.white,
            secondaryContainer = scheme.tint,
            onSecondaryContainer = scheme.ink,
            background = scheme.canvas,
            onBackground = scheme.ink,
            surface = scheme.card,
            onSurface = scheme.ink,
            surfaceVariant = scheme.surface,
            onSurfaceVariant = scheme.slate,
            outline = scheme.outline,
            error = scheme.error,
            onError = scheme.white,
            errorContainer = scheme.errorSurface,
            onErrorContainer = scheme.error,
        )
    } else {
        lightColorScheme(
            primary = scheme.cobalt,
            onPrimary = scheme.white,
            primaryContainer = scheme.tint,
            onPrimaryContainer = scheme.ink,
            secondary = scheme.slate,
            onSecondary = scheme.white,
            secondaryContainer = scheme.tint,
            onSecondaryContainer = scheme.ink,
            background = scheme.canvas,
            onBackground = scheme.ink,
            surface = scheme.card,
            onSurface = scheme.ink,
            surfaceVariant = scheme.surface,
            onSurfaceVariant = scheme.slate,
            outline = scheme.outline,
            error = scheme.error,
            onError = scheme.white,
            errorContainer = scheme.errorSurface,
            onErrorContainer = scheme.error,
        )
    }
    androidx.compose.runtime.key(dark) {
        MaterialTheme(
            colorScheme = material,
            typography = evidriloTypography(),
            content = content,
        )
    }
}

@Composable
public fun EvidriloContentColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    // Every scrollable page enters with one gentle rise-and-fade so navigating
    // between surfaces feels guided and calm. Individual cards can still stagger
    // their own reveal with EvidriloAppear on top of this base entrance.
    var appeared by remember { mutableStateOf(false) }
    val enter by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "pageEnter",
    )
    LaunchedEffect(Unit) { appeared = true }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    alpha = enter
                    translationY = (1f - enter) * 28f
                }
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = EvidriloTargetLayout.ContentTopPadding, bottom = 16.dp),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * Compact top bar: logo + wordmark on the left, optional [trailing] status
 * (for example an honest progress pill), and the profile avatar on the right.
 */
@Composable
public fun EvidriloBrandHeader(
    onSettings: (() -> Unit)?,
    avatarLabel: String = "L",
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvidriloLogoMark(size = 36.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Evidrilo",
                style = MaterialTheme.typography.headlineSmall,
                color = EvidriloColors.Cobalt,
            )
        }
        trailing?.invoke(this)
        EvidriloAvatarButton(label = avatarLabel, onClick = onSettings)
    }
}

@Composable
private fun EvidriloAvatarButton(
    label: String,
    onClick: (() -> Unit)?,
) {
    // The 48dp box is the touch target; the visible 40dp avatar sinks into its
    // lip through the shared interaction source.
    val interactionSource = remember { MutableInteractionSource() }
    val available = onClick != null
    Box(
        modifier = Modifier
            .size(48.dp)
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Open profile and settings"
                            role = Role.Button
                        }
                } else {
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "Profile and settings unavailable from this surface"
                        stateDescription = "Unavailable"
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        EvidriloPressableSurface(
            onClick = null,
            interactionSource = interactionSource,
            modifier = Modifier.size(width = 40.dp, height = 43.dp),
            faceColor = if (available) EvidriloColors.Cobalt else EvidriloColors.Separator,
            lipColor = if (available) EvidriloColors.CobaltPressed else EvidriloColors.Separator,
            borderColor = null,
            shape = CircleShape,
            lipHeight = 3.dp,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (available) EvidriloColors.White else EvidriloColors.Slate,
            )
        }
    }
}

/**
 * The shared tactile surface behind buttons, pressable cards, and path nodes.
 * A darker lip sits under the face; on press the face sinks fully into the lip
 * and springs back on release, so every tap has a clear physical response.
 *
 * The caller's [modifier] sizes the whole control, including [lipHeight].
 * When [onClick] is null the surface is static unless an external
 * [interactionSource] drives the pressed state (used for larger touch targets).
 */
@Composable
public fun EvidriloPressableSurface(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    faceColor: Color = EvidriloColors.Card,
    lipColor: Color = EvidriloColors.Separator,
    borderColor: Color? = EvidriloColors.Separator,
    shape: Shape = RoundedCornerShape(20.dp),
    lipHeight: Dp = 4.dp,
    role: Role = Role.Button,
    interactionSource: MutableInteractionSource? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val fallbackSource = remember { MutableInteractionSource() }
    val source = interactionSource ?: fallbackSource
    val pressed by source.collectIsPressedAsState()
    val drop by animateDpAsState(
        targetValue = if (pressed && enabled) lipHeight else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "pressableDrop",
    )
    Box(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(
                    enabled = enabled,
                    interactionSource = source,
                    indication = null,
                    role = role,
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        ),
        propagateMinConstraints = true,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = lipHeight)
                .clip(shape)
                .background(lipColor),
        )
        Box(
            modifier = Modifier
                .padding(bottom = lipHeight)
                .offset { IntOffset(0, drop.roundToPx()) }
                .clip(shape)
                .background(faceColor)
                .then(if (borderColor != null) Modifier.border(2.dp, borderColor, shape) else Modifier),
            contentAlignment = contentAlignment,
            content = content,
        )
    }
}

@Composable
public fun EvidriloLogoMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = EvidriloTargetLayout.BrandLogoSize,
) {
    Image(
        painter = painterResource(evidriloLogoDrawable),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size),
    )
}

@Composable
public fun EvidriloBackButton(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "Back to $label"
                role = Role.Button
            }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EvidriloIcon(EvidriloIconName.ARROW_BACK, tint = EvidriloColors.Cobalt)
        Text(label, color = EvidriloColors.Cobalt, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
public fun EvidriloIconButton(
    icon: EvidriloIconName,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        EvidriloIcon(
            name = icon,
            tint = if (enabled) EvidriloColors.Ink else EvidriloColors.Outline,
        )
    }
}

private val EvidriloButtonShape = RoundedCornerShape(16.dp)

@Composable
public fun EvidriloPrimaryButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailingIcon: EvidriloIconName? = null,
) {
    val contentColor = evidriloPrimaryButtonContentColor(enabled)
    // Solid Cobalt face on a darker lip; a disabled button goes flat and grey so
    // it never looks tappable. The spoken label keeps its original casing.
    EvidriloPressableSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        faceColor = if (enabled) EvidriloColors.Cobalt else EvidriloColors.Separator,
        lipColor = if (enabled) EvidriloColors.CobaltPressed else EvidriloColors.Separator,
        borderColor = null,
        shape = EvidriloButtonShape,
    ) {
        EvidriloButtonContent(label = label, color = contentColor, trailingIcon = trailingIcon)
    }
}

@Composable
public fun EvidriloSecondaryButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    EvidriloPressableSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        faceColor = EvidriloColors.Card,
        lipColor = EvidriloColors.Separator,
        borderColor = EvidriloColors.Separator,
        shape = EvidriloButtonShape,
    ) {
        EvidriloButtonContent(
            label = label,
            color = if (enabled) EvidriloColors.Cobalt else EvidriloColors.Outline,
            trailingIcon = null,
        )
    }
}

/** White button for use on Cobalt surfaces such as the home case banner. */
@Composable
public fun EvidriloInverseButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    EvidriloPressableSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        faceColor = EvidriloColors.White,
        lipColor = EvidriloColors.PatternBlue,
        borderColor = null,
        shape = EvidriloButtonShape,
    ) {
        EvidriloButtonContent(label = label, color = EvidriloColors.CobaltPressed, trailingIcon = null)
    }
}

@Composable
private fun EvidriloButtonContent(
    label: String,
    color: Color,
    trailingIcon: EvidriloIconName?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.8.sp),
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(10.dp))
            EvidriloIcon(trailingIcon, tint = color, modifier = Modifier.size(20.dp))
        }
    }
}

/** A card that behaves like a big button: bordered face, grey lip, press sink. */
@Composable
public fun EvidriloPressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    faceColor: Color = EvidriloColors.Card,
    borderColor: Color = EvidriloColors.Separator,
    lipColor: Color = borderColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    EvidriloPressableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        faceColor = faceColor,
        lipColor = lipColor,
        borderColor = borderColor,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Small uppercase label that introduces a section or a card. */
@Composable
public fun EvidriloEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = EvidriloColors.Cobalt,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
        color = color,
        modifier = modifier,
    )
}

/**
 * Compact icon + value status for top bars. [contentDescription] must spell
 * out what the value means, because the icon and number alone do not.
 */
@Composable
public fun EvidriloStatPill(
    label: String,
    icon: EvidriloIconName,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = EvidriloColors.Cobalt,
) {
    Row(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, EvidriloColors.Separator, RoundedCornerShape(12.dp))
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EvidriloIcon(icon, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = tint)
    }
}

/** Speech-bubble label with a downward pointer, e.g. above the next path step. */
@Composable
public fun EvidriloCalloutBubble(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = EvidriloColors.Card,
    contentColor: Color = EvidriloColors.Cobalt,
    borderColor: Color = EvidriloColors.Separator,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.8.sp),
                color = contentColor,
            )
        }
        Canvas(
            modifier = Modifier
                .offset(y = (-2).dp)
                .size(width = 16.dp, height = 9.dp),
        ) {
            val pointer = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width / 2f, size.height)
                lineTo(size.width, 0f)
                close()
            }
            drawPath(pointer, containerColor)
            val edge = 2.dp.toPx()
            drawLine(borderColor, Offset(0f, 0f), Offset(size.width / 2f, size.height), strokeWidth = edge)
            drawLine(borderColor, Offset(size.width, 0f), Offset(size.width / 2f, size.height), strokeWidth = edge)
        }
    }
}

@Composable
public fun EvidriloTintPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.PaleBlue),
        border = BorderStroke(2.dp, EvidriloColors.Tint),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        },
    )
}

public enum class EvidriloStatusTone {
    NEUTRAL,
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

@Composable
public fun EvidriloStatusChip(
    label: String,
    tone: EvidriloStatusTone = EvidriloStatusTone.INFO,
    icon: EvidriloIconName? = null,
) {
    val (container, content) = when (tone) {
        EvidriloStatusTone.NEUTRAL -> EvidriloColors.Surface to EvidriloColors.Slate
        EvidriloStatusTone.INFO -> EvidriloColors.Tint to EvidriloColors.Cobalt
        EvidriloStatusTone.SUCCESS -> EvidriloColors.SuccessSurface to EvidriloColors.Success
        EvidriloStatusTone.WARNING -> EvidriloColors.WarningSurface to EvidriloColors.Warning
        EvidriloStatusTone.ERROR -> EvidriloColors.ErrorSurface to EvidriloColors.Error
    }
    val resolvedIcon = icon ?: when (tone) {
        EvidriloStatusTone.NEUTRAL -> EvidriloIconName.INFO
        EvidriloStatusTone.INFO -> EvidriloIconName.INFO
        EvidriloStatusTone.SUCCESS -> EvidriloIconName.CHECK
        EvidriloStatusTone.WARNING -> EvidriloIconName.ALERT
        EvidriloStatusTone.ERROR -> EvidriloIconName.ALERT
    }
    // Chip gently scales in when it first appears so a status change (e.g. a
    // freshly computed verification result) draws the eye without a jarring pop.
    var appeared by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.86f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "chipScale",
    )
    LaunchedEffect(Unit) { appeared = true }
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = tone.name.lowercase()
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            EvidriloIcon(resolvedIcon, tint = content, modifier = Modifier.size(16.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = content,
            )
        }
    }
}

/**
 * Wraps content in a gentle rise-and-fade entrance. Screens stagger their cards
 * with an increasing [indexDelayMillis] so a page assembles itself top-to-bottom
 * instead of appearing all at once — a calm, guided reveal.
 */
@Composable
public fun EvidriloAppear(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 360, delayMillis = delayMillis),
        label = "appear",
    )
    LaunchedEffect(Unit) { appeared = true }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 24f
        },
    ) {
        content()
    }
}

@Composable
public fun EvidriloTargetCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Card),
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/**
 * Solid Cobalt hero card with a darker lip and two soft decorative circles.
 * Pass [onClick] to make the whole card pressable.
 */
@Composable
public fun EvidriloCobaltCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    EvidriloPressableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        faceColor = EvidriloColors.Cobalt,
        lipColor = EvidriloColors.CobaltPressed,
        borderColor = null,
        shape = RoundedCornerShape(24.dp),
        lipHeight = 5.dp,
    ) {
        CompositionLocalProvider(LocalContentColor provides EvidriloColors.White) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val glow = Color.White.copy(alpha = 0.09f)
                        drawCircle(glow, radius = size.minDimension * 0.62f, center = Offset(size.width * 1.02f, 0f))
                        drawCircle(glow, radius = size.minDimension * 0.34f, center = Offset(size.width * 0.84f, size.height * 1.04f))
                    }
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
public fun EvidriloProgressBar(progress: Float) {
    val safeProgress = progress.coerceIn(0f, 1f)
    // The fill animates toward its target so progress "grows" with a gentle
    // spring rather than snapping — a small delight that rewards each step.
    val animatedProgress by animateFloatAsState(
        targetValue = safeProgress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "progressFill",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(50))
            .background(EvidriloColors.Separator),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                .height(16.dp)
                .clip(RoundedCornerShape(50))
                .background(EvidriloColors.CobaltBright)
                // A light highlight strip gives the fill a friendly, glossy
                // depth without adding another colour to the palette.
                .drawBehind {
                    val inset = 7.dp.toPx()
                    val stripHeight = 4.dp.toPx()
                    if (size.width > inset * 2) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.32f),
                            topLeft = Offset(inset, 3.dp.toPx()),
                            size = Size(size.width - inset * 2, stripHeight),
                            cornerRadius = CornerRadius(stripHeight / 2f),
                        )
                    }
                },
        )
    }
}

@Composable
public fun EvidriloSettingsRow(
    icon: EvidriloIconName,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    stateDescription: String? = null,
    trailingIcon: EvidriloIconName = EvidriloIconName.CHEVRON_RIGHT,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle == null) 72.dp else 84.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                if (onClick != null) role = Role.Button
                this.selected = selected
                stateDescription?.let { this.stateDescription = it }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) EvidriloColors.Cobalt else EvidriloColors.PaleBlue),
            contentAlignment = Alignment.Center,
        ) {
            EvidriloIcon(
                icon,
                tint = if (selected) EvidriloColors.White else EvidriloColors.Cobalt,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        EvidriloIcon(trailingIcon, tint = EvidriloColors.Slate)
    }
}

@Composable
public fun EvidriloSettingsGroup(
    rows: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Card),
        border = BorderStroke(2.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = {
            Column(content = rows)
        },
    )
}

@Composable
public fun EvidriloSectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
public fun EvidriloDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = EvidriloColors.Separator,
    )
}
