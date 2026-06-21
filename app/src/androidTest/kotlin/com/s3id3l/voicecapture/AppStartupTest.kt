package com.s3id3l.voicecapture

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test: verifies the app starts without crashing.
 *
 * Catches: Application.onCreate() failures, manifest issues,
 * service restart loops, nav-graph inflation errors.
 *
 * Run via: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class AppStartupTest {

    @Test
    fun appStartsWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assert(!activity.isFinishing) { "MainActivity finished immediately after launch" }
                assert(!activity.isDestroyed) { "MainActivity destroyed immediately after launch" }
            }
        }
    }

    @Test
    fun bottomNavigationIsVisible() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.bottomNav)).check(matches(isDisplayed()))
        }
    }
}
