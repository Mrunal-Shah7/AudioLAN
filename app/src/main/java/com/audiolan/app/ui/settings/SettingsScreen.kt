package com.audiolan.app.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.audiolan.app.domain.model.AccentColor
import com.audiolan.app.ui.navigation.Screen
import com.audiolan.app.ui.settings.components.AccentColorPickerSheet
import com.audiolan.app.ui.theme.CardBorder
import com.audiolan.app.ui.theme.Dimensions
import com.audiolan.app.ui.theme.LocalToggleThumbColor
import com.audiolan.app.ui.theme.SurfaceFocused
import com.audiolan.app.ui.theme.Surface as AudioLANSurface
import com.audiolan.app.ui.theme.TextPrimary
import com.audiolan.app.ui.theme.TextSecondary
import com.audiolan.app.util.AnimationUtils

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val animationsEnabled = AnimationUtils.isSystemAnimationEnabled(context) && !LocalInspectionMode.current
    var showAccentColorSheet by remember { mutableStateOf(false) }
    val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
    val amoledMode by viewModel.amoledMode.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = Dimensions.ScreenHorizontalPadding),
            contentPadding = PaddingValues(
                top = Dimensions.SpaceL,
                bottom = Dimensions.BottomNavHeight + Dimensions.ScreenVerticalPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimensions.RowSpacing),
        ) {
            item(key = "title") {
                Text(
                    text = "Settings",
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = settingsItemModifier(animationsEnabled),
                )
            }
            item(key = "transmitter") {
                SettingsNavRow(
                    icon = Icons.Default.Tune,
                    label = "Transmitter settings",
                    subtitle = "Source, channels, sample rate, buffer, vol",
                    onClick = { navController.navigate(Screen.TransmitterSettings.route) },
                    modifier = settingsItemModifier(animationsEnabled),
                )
            }
            item(key = "receiver") {
                SettingsNavRow(
                    icon = Icons.Default.Headphones,
                    label = "Receiver settings",
                    subtitle = "Global volume",
                    onClick = { navController.navigate(Screen.ReceiverSettings.route) },
                    modifier = settingsItemModifier(animationsEnabled),
                )
            }
            item(key = "accent") {
                AccentColorRow(
                    icon = Icons.Default.Palette,
                    label = "Accent color",
                    selectedColor = accentColor,
                    onClick = { showAccentColorSheet = true },
                    modifier = settingsItemModifier(animationsEnabled),
                )
            }
            item(key = "amoled") {
                SettingsToggleRow(
                    icon = Icons.Default.Contrast,
                    label = "AMOLED mode",
                    subtitle = "Pure-black backgrounds",
                    checked = amoledMode,
                    onCheckedChange = viewModel::setAmoledMode,
                    modifier = settingsItemModifier(animationsEnabled),
                )
            }
        }
    }

    if (showAccentColorSheet) {
        AccentColorPickerSheet(
            selected = accentColor,
            onSelect = viewModel::setAccentColor,
            onDismiss = { showAccentColorSheet = false },
        )
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(
        modifier = modifier,
        onClick = onClick,
    ) {
        SettingsLeadingIcon(icon = icon)
        Spacer(Modifier.width(Dimensions.SpaceM))
        SettingsText(label = label, subtitle = subtitle, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
    ) {
        SettingsLeadingIcon(icon = icon)
        Spacer(Modifier.width(Dimensions.SpaceM))
        SettingsText(label = label, subtitle = subtitle, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LocalToggleThumbColor.current,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = CardBorder,
            ),
        )
    }
}

@Composable
private fun AccentColorRow(
    icon: ImageVector,
    label: String,
    selectedColor: AccentColor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(
        modifier = modifier,
        onClick = onClick,
    ) {
        SettingsLeadingIcon(icon = icon)
        Spacer(Modifier.width(Dimensions.SpaceM))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = selectedColor.displayName.replaceFirstChar { it.titlecase() },
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.SpaceXS)) {
            AccentColor.entries.take(5).forEach { entry ->
                AccentSwatch(
                    color = entry,
                    selected = entry == selectedColor,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(Dimensions.CardCornerRadius),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        color = if (pressed) SurfaceFocused else AudioLANSurface,
        shape = shape,
        border = BorderStroke(Dimensions.CardBorderWidth, CardBorder),
    ) {
        Row(
            modifier = Modifier.padding(Dimensions.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun SettingsLeadingIcon(icon: ImageVector) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        shape = RoundedCornerShape(Dimensions.RowCornerRadius),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(Dimensions.SpaceXS)
                .size(22.dp),
        )
    }
}

@Composable
private fun SettingsText(
    label: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AccentSwatch(
    color: AccentColor,
    selected: Boolean,
) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(color = color.primary, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Surface(
                modifier = Modifier.size(18.dp),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(2.dp, TextPrimary),
                content = {},
            )
        }
    }
}

private fun LazyItemScope.settingsItemModifier(animationsEnabled: Boolean): Modifier =
    Modifier.animateItem(
        placementSpec = spring(
            dampingRatio = if (animationsEnabled) 0.75f else 1f,
            stiffness = if (animationsEnabled) 200f else Spring.StiffnessHigh,
        ),
    )
