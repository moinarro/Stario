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

package com.stario.launcher.sheet.dashboard.dialog;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.gson.reflect.TypeToken;
import com.stario.launcher.utils.Utils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * The Multimedia tab's own curated app list - Spotify, Amazon Music,
 * Symfonium, Audible, Play Books, YouTube, whatever the user picks - kept
 * separate from GlanceQuickApps' list since it serves a different tab with
 * a different purpose, but stored and shaped the exact same way (a plain
 * JSON array of package names, static accessors over its own
 * SharedPreferences), so it needs no popup/scroll-mode machinery of its
 * own - just add/remove, edited directly from the Dashboard.
 */
final class DashboardMediaApps {
    private static final String TAG = "DashboardMediaApps";
    private static final String KEY = "com.stario.DASHBOARD_MEDIA_APPS_LIST";

    private DashboardMediaApps() {
    }

    @NonNull
    static List<String> getPackages(SharedPreferences preferences) {
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
            Log.e(TAG, "getPackages: failed to parse stored media apps", exception);

            return new ArrayList<>();
        }
    }

    static void add(SharedPreferences preferences, String packageName) {
        List<String> packages = getPackages(preferences);

        if (packageName != null && !packages.contains(packageName)) {
            packages.add(packageName);

            save(preferences, packages);
        }
    }

    static void remove(SharedPreferences preferences, String packageName) {
        List<String> packages = getPackages(preferences);

        if (packages.remove(packageName)) {
            save(preferences, packages);
        }
    }

    private static void save(SharedPreferences preferences, List<String> packages) {
        preferences.edit()
                .putString(KEY, Utils.getGsonInstance().toJson(packages))
                .apply();
    }
}
