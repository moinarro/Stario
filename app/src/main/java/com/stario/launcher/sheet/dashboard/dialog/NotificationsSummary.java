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

import android.content.Context;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;

import com.stario.launcher.BuildConfig;
import com.stario.launcher.services.NotificationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Current notification counts by package, most first - the Utilidades
 * page's "Notificaciones" summary. Reuses NotificationService, the same
 * notification listener the Focus chip's "Do Not Disturb access" cousin
 * and the Glance notification dots already run on, so this is a stateless
 * snapshot read rather than a listener of its own: nothing to record,
 * just today's currently-active notifications at the moment the
 * Dashboard is refreshed.
 */
final class NotificationsSummary {
    private NotificationsSummary() {
    }

    static class Entry {
        final String packageName;
        final int count;

        Entry(String packageName, int count) {
            this.packageName = packageName;
            this.count = count;
        }
    }

    static boolean isGranted(@NonNull Context context) {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(BuildConfig.APPLICATION_ID);
    }

    @NonNull
    static List<Entry> getSummary(@NonNull Context context) {
        if (!isGranted(context)) {
            return new ArrayList<>();
        }

        NotificationService service = NotificationService.getInstance();

        if (service == null) {
            return new ArrayList<>();
        }

        StatusBarNotification[] notifications;
        try {
            notifications = service.getActiveNotifications();
        } catch (Exception exception) {
            return new ArrayList<>();
        }

        if (notifications == null) {
            return new ArrayList<>();
        }

        Map<String, Integer> counts = NotificationService.convertToNotificationMap(notifications);

        List<Entry> entries = new ArrayList<>(counts.size());
        for (Map.Entry<String, Integer> count : counts.entrySet()) {
            if (count.getValue() > 0) {
                entries.add(new Entry(count.getKey(), count.getValue()));
            }
        }

        entries.sort((a, b) -> Integer.compare(b.count, a.count));

        return entries;
    }
}
