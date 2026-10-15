package com.localdrop.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.localdrop.app.data.LocalDropStore
import com.localdrop.app.ui.onboarding.OnboardingScreen
import com.localdrop.app.ui.settings.SettingsScreen
import com.localdrop.app.ui.theme.LocalDropTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PolishUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun onboardingHasFourShortPagesAndCompletes() {
        var completed = false
        compose.setContent { LocalDropTheme { OnboardingScreen { completed = true } } }
        compose.onNodeWithText("Your files.\nA shorter journey.").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Share with\nany browser.").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Phone to PC.\nOne clear route.").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Your network.\nYour control.").assertIsDisplayed()
        compose.onNodeWithText("Open Pushvia").performClick()
        compose.runOnIdle { assertTrue(completed) }
    }
    @Test fun settingsChangesThemeAndOpensDevicesWithoutLongScroll() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LocalDropStore(context)
        val original = store.settings
        var chosenTheme = ""
        var devicesOpened = false
        try {
            compose.setContent { LocalDropTheme { SettingsScreen(store, { chosenTheme = it }, {}, {}, { devicesOpened = true }) } }
            compose.onNodeWithText("Dark").performClick()
            compose.runOnIdle { assertEquals("Dark", chosenTheme); assertEquals("Dark", store.settings.theme) }
            compose.onNodeWithText("Privacy", substring = false).performClick()
            compose.onNodeWithText("Trusted devices", substring = true).assertIsDisplayed().performClick()
            compose.runOnIdle { assertTrue(devicesOpened) }
            compose.onNodeWithText("Help", substring = false).performClick()
            compose.onNodeWithText("Browser Share", substring = true).assertIsDisplayed()
        } finally { store.settings = original }
    }
}
