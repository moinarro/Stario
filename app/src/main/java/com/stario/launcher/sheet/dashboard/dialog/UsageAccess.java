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

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * "Apps más usadas" needs the special Usage Access permission (a manual,
 * Settings-screen-only grant - PACKAGE_USAGE_STATS can't be requested
 * through the normal runtime-permission dialog), so this is a small,
 * stateless static utility rather than a live-tracked feature like
 * RecentApps/RecentMedia: there is nothing to record, only a permission
 * to check and a system API to query on demand.
 */
final class UsageAccess {
    private static final long WINDOW = TimeUnit.DAYS.toMillis(7);

    private UsageAccess() {
    }

    static boolean isGranted(@NonNull Context context) {
        AppOpsManager appOps = context.getSystemService(AppOpsManager.class);

        if (appOps == null) {
            return false;
        }

        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName());

        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * Top packages by foreground time over the last {@link #WINDOW}, most
     * used first. Returns an empty list rather than throwing when access
     * isn't actually granted (checkOpNoThrow can lag a fresh grant by a
     * moment) or the system service is unavailable.
     */
    @NonNull
    static List<String> getMostUsed(@NonNull Context context, int limit) {
        UsageStatsManager manager = context.getSystemService(UsageStatsManager.class);

        if (manager == null) {
            return new ArrayList<>();
        }

        long end = System.currentTimeMillis();
        long start = end - WINDOW;

        Map<String, UsageStats> stats;
        try {
            stats = manager.queryAndAggregateUsageStats(start, end);
        } catch (Exception exception) {
            return new ArrayList<>();
        }

        if (stats == null || stats.isEmpty()) {
            return new ArrayList<>();
        }

        List<UsageStats> sorted = new ArrayList<>(stats.values());
        sorted.sort((a, b) -> Long.compare(b.getTotalTimeInForeground(), a.getTotalTimeInForeground()));

        List<String> packages = new ArrayList<>(limit);
        for (UsageStats usageStats : sorted) {
            if (usageStats.getTotalTimeInForeground() <= 0) {
                continue;
            }

            String packageName = usageStats.getPackageName();

            if (packageName == null || packages.contains(packageName)) {
                continue;
            }

            packages.add(packageName);

            if (packages.size() >= limit) {
                break;
            }
        }

        return packages;
    }
}
