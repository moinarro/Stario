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

package com.stario.launcher.sheet.widgets;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.reflect.TypeToken;
import com.stario.launcher.utils.Utils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Named "widget profiles": time-of-day slots a widget (top-level, or a
 * whole stack) can be tagged with via {@link Widget#scheduleSlotId}, so it
 * only shows up in the widgets grid during that slot - e.g. a "Work" set of
 * widgets from 9 to 18, a "Night" set from 22 to 7. Generalizes the same
 * TimeSlot-and-apply-on-open shape as PinnedCategorySchedule, but a slot
 * here carries a user-given name instead of a single category id, since it
 * drives membership of a whole (possibly empty) set of widgets rather than
 * picking one thing.
 * <p>
 * A widget with no scheduleSlotId is always visible, schedule enabled or
 * not - only widgets that were explicitly assigned to a slot are ever
 * hidden, and only while the schedule is enabled and no slot of theirs is
 * currently active.
 */
public final class WidgetSchedule {
    private static final String TAG = "WidgetSchedule";

    private static final String SCHEDULE_ENABLED = "com.stario.WIDGET_SCHEDULE_ENABLED";
    private static final String SCHEDULE_SLOTS = "com.stario.WIDGET_SCHEDULE_SLOTS";

    private WidgetSchedule() {
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

    public static List<TimeSlot> sorted(List<TimeSlot> slots) {
        List<TimeSlot> copy = new ArrayList<>(slots);

        Collections.sort(copy, Comparator.comparingInt(slot -> slot.startMinute));

        return copy;
    }

    /**
     * @return the id of the slot active right now, or null if the schedule
     * is disabled, empty, or no slot currently covers this time.
     */
    @Nullable
    public static String resolveActiveSlotId(SharedPreferences preferences) {
        if (!isEnabled(preferences)) {
            return null;
        }

        List<TimeSlot> slots = loadSlots(preferences);

        if (slots.isEmpty()) {
            return null;
        }

        java.util.Calendar now = java.util.Calendar.getInstance();
        int minuteOfDay = now.get(java.util.Calendar.HOUR_OF_DAY) * 60
                + now.get(java.util.Calendar.MINUTE);

        for (TimeSlot slot : slots) {
            if (slot.contains(minuteOfDay)) {
                return slot.id;
            }
        }

        return null;
    }

    @Nullable
    public static TimeSlot find(List<TimeSlot> slots, @Nullable String id) {
        if (id == null) {
            return null;
        }

        for (TimeSlot slot : slots) {
            if (slot.id.equals(id)) {
                return slot;
            }
        }

        return null;
    }

    public static class TimeSlot {
        public String id;
        public String name;
        public int startMinute;
        public int endMinute;

        public TimeSlot() {
            this.id = UUID.randomUUID().toString();
        }

        public TimeSlot(String name, int startMinute, int endMinute) {
            this();

            this.name = name;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
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
