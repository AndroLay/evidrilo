package dev.nextgen.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nextgen.mobile.design.resources.evidriloLogoDrawable
import dev.nextgen.mobile.design.resources.evidriloSourceSansBoldFont
import dev.nextgen.mobile.design.resources.evidriloSourceSansRegularFont
import dev.nextgen.mobile.design.resources.evidriloSourceSansSemiboldFont
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

public object EvidriloColors {
    val Cobalt = Color(0xFF294BC6)
    val CobaltPressed = Color(0xFF203BA0)
    val White = Color(0xFFFFFFFF)
    val Surface = Color(0xFFF5F7FB)
    val Tint = Color(0xFFEAF0FF)
    val Ink = Color(0xFF202C40)
    val Slate = Color(0xFF53627A)
    val Separator = Color(0xFFE1E6EF)
    val Outline = Color(0xFF8190A8)
    val Error = Color(0xFFB42318)
    val ErrorSurface = Color(0xFFFFE9E7)
    val Success = Color(0xFF146C43)
    val SuccessSurface = Color(0xFFE7F5EC)
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
    return Typography(
    displayLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 44.sp,
        color = EvidriloColors.Ink,
    ),
    displayMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        color = EvidriloColors.Ink,
    ),
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        color = EvidriloColors.Ink,
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        color = EvidriloColors.Ink,
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        color = EvidriloColors.Ink,
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = EvidriloColors.Ink,
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        color = EvidriloColors.Ink,
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
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
        fontWeight = FontWeight.SemiBold,
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
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = EvidriloColors.Slate,
    )
    )
}

@Composable
public fun EvidriloTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = EvidriloColors.Cobalt,
            onPrimary = EvidriloColors.White,
            primaryContainer = EvidriloColors.Tint,
            onPrimaryContainer = EvidriloColors.Ink,
            secondary = EvidriloColors.Slate,
            onSecondary = EvidriloColors.White,
            secondaryContainer = EvidriloColors.Tint,
            onSecondaryContainer = EvidriloColors.Ink,
            background = EvidriloColors.White,
            onBackground = EvidriloColors.Ink,
            surface = EvidriloColors.White,
            onSurface = EvidriloColors.Ink,
            surfaceVariant = EvidriloColors.Surface,
            onSurfaceVariant = EvidriloColors.Slate,
            outline = EvidriloColors.Outline,
            error = EvidriloColors.Error,
            onError = EvidriloColors.White,
            errorContainer = EvidriloColors.ErrorSurface,
            onErrorContainer = EvidriloColors.Error,
        ),
        typography = evidriloTypography(),
        content = content,
    )
}

@Composable
public fun EvidriloContentColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
public fun EvidriloBrandHeader(onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvidriloLogoMark()
            Spacer(modifier = Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    "Evidrilo",
                    style = MaterialTheme.typography.headlineSmall,
                    color = EvidriloColors.Ink,
                )
                Text(
                    "From evidence to action.",
                    style = MaterialTheme.typography.labelMedium,
                    color = EvidriloColors.Cobalt,
                )
            }
        }
        EvidriloIconButton(
            icon = EvidriloIconName.SETTINGS,
            contentDescription = "Open settings",
            onClick = onSettings,
        )
    }
}

@Composable
public fun EvidriloLogoMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(evidriloLogoDrawable),
        contentDescription = contentDescription,
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp)),
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

@Composable
public fun EvidriloPrimaryButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val contentColor = evidriloPrimaryButtonContentColor(enabled)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = EvidriloColors.Cobalt,
            contentColor = contentColor,
            disabledContainerColor = EvidriloColors.Surface,
            disabledContentColor = contentColor,
        ),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = contentColor)
            EvidriloIcon(EvidriloIconName.ARROW_FORWARD, tint = contentColor)
        }
    }
}

@Composable
public fun EvidriloSecondaryButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (enabled) EvidriloColors.Separator else EvidriloColors.Outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = EvidriloColors.Ink,
            disabledContentColor = EvidriloColors.Outline,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
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
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.Tint),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        },
    )
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
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EvidriloIcon(icon, tint = if (selected) EvidriloColors.Cobalt else EvidriloColors.Ink)
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
        colors = CardDefaults.cardColors(containerColor = EvidriloColors.White),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
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
