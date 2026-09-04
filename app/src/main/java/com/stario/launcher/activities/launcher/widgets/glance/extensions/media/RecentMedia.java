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

package com.stario.launcher.activities.launcher.widgets.glance.extensions.media;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.reflect.TypeToken;
import com.stario.launcher.utils.Utils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A small "what I've been listening to" history - most recent track
 * first, capped at {@link #MAX_ENTRIES}. Fed as a side effect of two
 * independent, already-existing MediaController trackers rather than a
 * listener of its own: Media.updateSession() (only runs while the Glance
 * Media chip is enabled) and DashboardDialog.updateNowPlaying() (runs any
 * time the Dashboard's Multimedia tab is open, regardless of that Glance
 * toggle - the reliable source, since a user who never enabled the chip
 * would otherwise see no history at all). Both call record(), which
 * dedupes against the current front entry, so neither produces
 * duplicates on its own.
 */
public final class RecentMedia {
    private static final String TAG = "RecentMedia";
    private static final String KEY = "com.stario.RECENT_MEDIA_LIST";
    private static final int MAX_ENTRIES = 8;

    private RecentMedia() {
    }

    public static class Track {
        public final String packageName;
        public final String title;
        public final String artist;

        Track(String packageName, String title, String artist) {
            this.packageName = packageName;
            this.title = title;
            this.artist = artist;
        }
    }

    @NonNull
    public static List<Track> getTracks(SharedPreferences preferences) {
        String json = preferences.getString(KEY, null);

        if (json == null) {
            return new ArrayList<>();
        }

        try {
            Type type = new TypeToken<ArrayList<Track>>() {
            }.getType();

            List<Track> stored = Utils.getGsonInstance().fromJson(json, type);

            return stored != null ? new ArrayList<>(stored) : new ArrayList<>();
        } catch (Exception exception) {
            Log.e(TAG, "getTracks: failed to parse stored recent media", exception);

            return new ArrayList<>();
        }
    }

    /**
     * Records a newly-started track, most recent first. A record matching
     * the current front entry (same package + title) is skipped, since
     * Media calls this once per detected title change and re-selecting
     * the same session as active shouldn't duplicate the top entry.
     */
    public static void record(SharedPreferences preferences, String packageName,
                              String title, @Nullable String artist) {
        if (preferences == null || packageName == null || title == null || title.isEmpty()) {
            return;
        }

        List<Track> tracks = getTracks(preferences);

        if (!tracks.isEmpty()) {
            Track front = tracks.get(0);

            if (Objects.equals(front.packageName, packageName) &&
                    Objects.equals(front.title, title)) {
                return;
            }
        }

        tracks.add(0, new Track(packageName, title, artist));

        while (tracks.size() > MAX_ENTRIES) {
            tracks.remove(tracks.size() - 1);
        }

        preferences.edit()
                .putString(KEY, Utils.getGsonInstance().toJson(tracks))
                .apply();
    }
}
