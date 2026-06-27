// src/androidTest/java/com/atom/app/MainActivityStatusSequenceTest.java
package com.atom.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verifies a rapid status sequence lands on the final text (Epic 2.2 AC).
 * Requires a connected device/emulator: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4.class)
public class MainActivityStatusSequenceTest {

    @Test
    public void rapidSwapsLandOnFinalText() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity ->
                    activity.runOnUiThread(() -> {
                        // Three swaps within one frame batch; only the last must survive.
                        com.atom.app.ui.motion.StatusCrossfader.swap(
                                activity.findViewById(R.id.status_text), "Listening");
                        com.atom.app.ui.motion.StatusCrossfader.swap(
                                activity.findViewById(R.id.status_text), "Thinking");
                        com.atom.app.ui.motion.StatusCrossfader.swap(
                                activity.findViewById(R.id.status_text), "Done");
                    }));
            onView(withId(R.id.status_text)).check(matches(withText("Done")));
        }
    }
}
