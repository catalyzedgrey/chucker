package com.chuckerteam.chucker.internal.ui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.chuckerteam.chucker.R

/**
 * Material 3 theme mapped from the same color resources the former `Chucker.BaseTheme` used,
 * so light/dark (`values-night`) palettes are unchanged.
 */
@Composable
internal fun ChuckerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val primary = colorResource(R.color.chucker_color_primary)
    val onPrimary = colorResource(R.color.chucker_color_on_primary)
    val secondary = colorResource(R.color.chucker_color_secondary)
    val onSecondary = colorResource(R.color.chucker_color_on_secondary)
    val background = colorResource(R.color.chucker_color_background)
    val onBackground = colorResource(R.color.chucker_color_on_background)
    val surface = colorResource(R.color.chucker_color_surface)
    val onSurface = colorResource(R.color.chucker_color_on_surface)
    val error = colorResource(R.color.chucker_status_error)
    val onError = colorResource(R.color.chucker_color_on_error)

    val scheme: ColorScheme =
        if (dark) {
            darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                secondary = secondary,
                onSecondary = onSecondary,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceContainer = surface,
                surfaceContainerHigh = surface,
                error = error,
                onError = onError,
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                secondary = secondary,
                onSecondary = onSecondary,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceContainer = surface,
                surfaceContainerHigh = surface,
                error = error,
                onError = onError,
            )
        }
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * The former toolbars used `ThemeOverlay.MaterialComponents.Dark.ActionBar` on a "primary surface"
 * app bar: primary color in light mode, surface color in dark mode, with white content.
 */
@Composable
@ReadOnlyComposable
internal fun chuckerAppBarContainerColor(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary

@Composable
internal fun chuckerTopAppBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(
        containerColor = chuckerAppBarContainerColor(),
        scrolledContainerColor = chuckerAppBarContainerColor(),
        titleContentColor = Color.White,
        navigationIconContentColor = Color.White,
        actionIconContentColor = Color.White,
    )
