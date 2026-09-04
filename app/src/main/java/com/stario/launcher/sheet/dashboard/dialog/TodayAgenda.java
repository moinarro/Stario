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

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Today's calendar events, most imminent first - the Utilidades page's
 * "Agenda de hoy". Needs the standard READ_CALENDAR runtime permission
 * (unlike the Dashboard's other special-access features, this is an
 * ordinary dangerous permission, requested the same direct way Weather
 * already requests precise location - see LocationRecyclerAdapter), so
 * this is a stateless static query utility rather than a live tracker:
 * there's nothing to record, only the system calendar provider to read
 * on demand.
 */
final class TodayAgenda {
    private static final String TAG = "TodayAgenda";
    private static final int MAX_EVENTS = 6;

    private TodayAgenda() {
    }

    static class Event {
        final long id;
        final String title;
        final long begin;
        final long end;
        final boolean allDay;

        Event(long id, String title, long begin, long end, boolean allDay) {
            this.id = id;
            this.title = title;
            this.begin = begin;
            this.end = end;
            this.allDay = allDay;
        }
    }

    static boolean isGranted(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    static List<Event> getTodayEvents(@NonNull Context context) {
        if (!isGranted(context)) {
            return Collections.emptyList();
        }

        Calendar dayStart = Calendar.getInstance();
        dayStart.set(Calendar.HOUR_OF_DAY, 0);
        dayStart.set(Calendar.MINUTE, 0);
        dayStart.set(Calendar.SECOND, 0);
        dayStart.set(Calendar.MILLISECOND, 0);

        Calendar dayEnd = (Calendar) dayStart.clone();
        dayEnd.add(Calendar.DAY_OF_MONTH, 1);

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, dayStart.getTimeInMillis());
        ContentUris.appendId(builder, dayEnd.getTimeInMillis());

        String[] projection = {
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
        };

        List<Event> events = new ArrayList<>();

        try (Cursor cursor = context.getContentResolver().query(builder.build(),
                projection, null, null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor != null) {
                Resources resources = context.getResources();

                while (cursor.moveToNext() && events.size() < MAX_EVENTS) {
                    String title = cursor.getString(1);

                    events.add(new Event(
                            cursor.getLong(0),
                            title != null && !title.isBlank() ? title :
                                    resources.getString(android.R.string.untitled),
                            cursor.getLong(2),
                            cursor.getLong(3),
                            cursor.getInt(4) != 0
                    ));
                }
            }
        } catch (Exception exception) {
            Log.e(TAG, "getTodayEvents: failed to query the calendar provider", exception);

            return Collections.emptyList();
        }

        return events;
    }
}
