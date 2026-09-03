/*
 * Copyright (C) 2026 Răzvan Albu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package com.stario.launcher.gestures;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.stario.launcher.apps.LauncherApplication;
import com.stario.launcher.apps.ProfileManager;
import com.stario.launcher.preferences.Entry;
import com.stario.launcher.preferences.Vibrations;
import com.stario.launcher.themes.ThemedActivity;

/**
 * Stores and resolves which application should be launched for each
 * two-finger swipe direction. Every direction is fully user configurable
 * and independent from the others; a direction with nothing assigned to it
 * is simply a no-op.
 */
public final class Gestures {
    private static final String KEY_PREFIX = "com.stario.GESTURE_APPLICATION_";

    private final SharedPreferences preferences;

    public Gestures(ThemedActivity activity) {
        this.preferences = activity.getApplicationContext()
                .getSharedPreferences(Entry.GESTURES);
    }

    private static String key(TwoFingerSwipeGestureDetector.Direction direction) {
        return KEY_PREFIX + direction.name();
    }

    @Nullable
    public String getAssignedPackage(TwoFingerSwipeGestureDetector.Direction direction) {
        return preferences.getString(key(direction), null);
    }

    public boolean isAssigned(TwoFingerSwipeGestureDetector.Direction direction) {
        return getAssignedPackage(direction) != null;
    }

    public void assign(TwoFingerSwipeGestureDetector.Direction direction, String packageName) {
        preferences.edit()
                .putString(key(direction), packageName)
                .apply();
    }

    public void clear(TwoFingerSwipeGestureDetector.Direction direction) {
        preferences.edit()
                .remove(key(direction))
                .apply();
    }

    /**
     * @return true if an application was assigned and launching was attempted
     */
    public boolean trigger(ThemedActivity activity, TwoFingerSwipeGestureDetector.Direction direction) {
        String packageName = getAssignedPackage(direction);

        if (packageName == null) {
            return false;
        }

        LauncherApplication application = ProfileManager.getInstance().getApplication(packageName);

        if (application == null) {
            // the assigned application is no longer installed; clean up
            clear(direction);

            return false;
        }

        Vibrations.getInstance().vibrate();
        application.launch(activity);

        return true;
    }
}
