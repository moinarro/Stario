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

package com.stario.launcher.activities.launcher.widgets.pins;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.reflect.TypeToken;
import com.stario.launcher.apps.Category;
import com.stario.launcher.apps.CategoryManager;
import com.stario.launcher.utils.Utils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Lets the {@link PinnedCategory} widget automatically switch which
 * category it displays depending on the time of day, based on a fully
 * user-configurable list of time slots (e.g. "News & Weather" from 07:00
 * to 09:00, "Streaming" from 20:00 to 23:30, ...).
 * <p>
 * Slots may wrap past midnight (start > end), in which case they're treated
 * as spanning into the next day. Overlapping slots are resolved by picking
 * the one that was defined first in the list.
 */
public final class PinnedCategorySchedule {
    private static final String TAG = "PinnedCategorySchedule";

    public static final String SCHEDULE_ENABLED = "com.stario.PINNED_CATEGORY_SCHEDULE_ENABLED";
    public static final String SCHEDULE_SLOTS = "com.stario.PINNED_CATEGORY_SCHEDULE_SLOTS";

    private PinnedCategorySchedule() {
    }

    public static boolean isEnabled(SharedPreferences preferences) {
        return preferences.getBoolean(SCHEDULE_ENABLED, false);
    }

    public static void setEnabled(SharedPreferences preferences, boolean enabled) {
        preferences.edit()
                .putBoolean(SCHEDULE_ENABLED, enabled)
                .apply();
    }

    @NonNull
    public static List<TimeSlot> loadSlots(SharedPreferences preferences) {
        String json = preferences.getString(SCHEDULE_SLOTS, null);

        if (json == null) {
            return new ArrayList<>();
        }

        try {
            Type type = new TypeToken<ArrayList<TimeSlot>>() {
            }.getType();

            List<TimeSlot> slots = Utils.getGsonInstance().fromJson(json, type);

            return slots != null ? slots : new ArrayList<>();
        } catch (Exception exception) {
            Log.e(TAG, "loadSlots: failed to parse stored schedule", exception);

            return new ArrayList<>();
        }
    }

    public static void saveSlots(SharedPreferences preferences, List<TimeSlot> slots) {
        preferences.edit()
                .putString(SCHEDULE_SLOTS, Utils.getGsonInstance().toJson(slots))
                .apply();
    }

    /**
     * Evaluates the schedule for the current time and, if it is enabled and
     * a matching slot exists, updates {@link PinnedCategory#PINNED_CATEGORY}
     * to point to that slot's category. A no-op if the schedule is disabled,
     * empty, or the currently active category already matches.
     */
    public static void apply(SharedPreferences preferences) {
        if (!isEnabled(preferences)) {
            return;
        }

        List<TimeSlot> slots = loadSlots(preferences);

        if (slots.isEmpty()) {
            return;
        }

        java.util.Calendar now = java.util.Calendar.getInstance();
        int minuteOfDay = now.get(java.util.Calendar.HOUR_OF_DAY) * 60
                + now.get(java.util.Calendar.MINUTE);

        TimeSlot active = resolve(slots, minuteOfDay);
        if (active == null || active.categoryId == null) {
            return;
        }

        // make sure the referenced category still exists before switching to it
        try {
            Category category = CategoryManager.getInstance().get(UUID.fromString(active.categoryId));

            if (category == null) {
                return;
            }
        } catch (IllegalArgumentException exception) {
            return;
        }

        String current = preferences.getString(PinnedCategory.PINNED_CATEGORY, null);

        if (!active.categoryId.equals(current)) {
            preferences.edit()
                    .putString(PinnedCategory.PINNED_CATEGORY, active.categoryId)
                    .apply();
        }
    }

    @Nullable
    private static TimeSlot resolve(List<TimeSlot> slots, int minuteOfDay) {
        for (TimeSlot slot : slots) {
            if (slot.contains(minuteOfDay)) {
                return slot;
            }
        }

        return null;
    }

    public static List<TimeSlot> sorted(List<TimeSlot> slots) {
        List<TimeSlot> copy = new ArrayList<>(slots);

        Collections.sort(copy, Comparator.comparingInt(slot -> slot.startMinute));

        return copy;
    }

    public static class TimeSlot {
        public String id;
        public int startMinute;
        public int endMinute;
        public String categoryId;

        public TimeSlot() {
            this.id = UUID.randomUUID().toString();
        }

        public TimeSlot(int startMinute, int endMinute, @Nullable String categoryId) {
            this();

            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.categoryId = categoryId;
        }

        public boolean contains(int minuteOfDay) {
            if (startMinute == endMinute) {
                return false;
            }

            if (startMinute < endMinute) {
                return minuteOfDay >= startMinute && minuteOfDay < endMinute;
            } else {
                // wraps past midnight
                return minuteOfDay >= startMinute || minuteOfDay < endMinute;
            }
        }
    }
}
