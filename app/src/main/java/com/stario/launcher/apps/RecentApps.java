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

package com.stario.launcher.apps;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.reflect.TypeToken;
import com.stario.launcher.utils.Utils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Most-recently-launched app packages, most recent first, capped at
 * {@link #MAX_ENTRIES}. Purely a side effect of {@link LauncherApplication#launch}
 * - there is no separate "start tracking" step - so this stays a small
 * stateless static utility over its own SharedPreferences, the same shape
 * as GlanceQuickApps' static settings accessors, rather than a singleton
 * with a live in-memory list to keep in sync.
 */
public final class RecentApps {
    private static final String TAG = "RecentApps";
    private static final String KEY = "com.stario.RECENT_APPS_LIST";
    private static final int MAX_ENTRIES = 10;

    private RecentApps() {
    }

    @NonNull
    public static List<String> getPackages(SharedPreferences preferences) {
        String json = preferences.getString(KEY, null);

        if (json == null) {
            return new ArrayList<>();
        }

        try {
            Type type = new TypeToken<ArrayList<String>>() {
            }.getType();

            List<String> stored = Utils.getGsonInstance().fromJson(json, type);

            return stored != null ? new ArrayList<>(stored) : new ArrayList<>();
        } catch (Exception exception) {
            Log.e(TAG, "getPackages: failed to parse stored recent apps", exception);

            return new ArrayList<>();
        }
    }

    /**
     * Moves packageName to the front (inserting it if new), then trims
     * down to {@link #MAX_ENTRIES}.
     */
    public static void recordLaunch(SharedPreferences preferences, String packageName) {
        if (packageName == null) {
            return;
        }

        List<String> packages = getPackages(preferences);

        packages.remove(packageName);
        packages.add(0, packageName);

        while (packages.size() > MAX_ENTRIES) {
            packages.remove(packages.size() - 1);
        }

        preferences.edit()
                .putString(KEY, Utils.getGsonInstance().toJson(packages))
                .apply();
    }

    public static void remove(SharedPreferences preferences, String packageName) {
        List<String> packages = getPackages(preferences);

        if (packages.remove(packageName)) {
            preferences.edit()
                    .putString(KEY, Utils.getGsonInstance().toJson(packages))
                    .apply();
        }
    }
}
