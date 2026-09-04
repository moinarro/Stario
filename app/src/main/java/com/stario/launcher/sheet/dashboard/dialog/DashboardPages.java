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

import androidx.annotation.NonNull;

import com.stario.launcher.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Which of the Dashboard's pages (see DashboardDialog) the user has kept
 * enabled, and which one opens by default - both configurable from
 * DashboardSettingsDialog. A plain static utility over its own
 * SharedPreferences, the same shape as the rest of the Dashboard's small
 * preference-backed pieces (DashboardMediaApps, RecentMedia): there's
 * nothing here that needs a live in-memory copy, just reads/writes read on
 * demand whenever the Dashboard (re)builds its pager or the settings
 * dialog opens.
 */
public final class DashboardPages {
    private static final String ENABLED_KEY_PREFIX = "com.stario.DASHBOARD_PAGE_ENABLED_";
    private static final String DEFAULT_KEY = "com.stario.DASHBOARD_DEFAULT_PAGE";

    private DashboardPages() {
    }

    public enum Page {
        HOME(R.string.dashboard_home),
        MULTIMEDIA(R.string.dashboard_media),
        UTILITIES(R.string.dashboard_utilities);

        public final int titleRes;

        Page(int titleRes) {
            this.titleRes = titleRes;
        }
    }

    public static boolean isEnabled(@NonNull SharedPreferences preferences, @NonNull Page page) {
        return preferences.getBoolean(ENABLED_KEY_PREFIX + page.name(), true);
    }

    /**
     * No-op if this would disable the last remaining enabled page - the
     * Dashboard always needs somewhere to land. If the page being disabled
     * was the default, the default falls back to the first page that's
     * still enabled.
     */
    public static void setEnabled(@NonNull SharedPreferences preferences,
                                  @NonNull Page page, boolean enabled) {
        if (!enabled) {
            List<Page> currentlyEnabled = getEnabledPages(preferences);

            if (currentlyEnabled.size() <= 1 && currentlyEnabled.contains(page)) {
                return;
            }
        }

        preferences.edit()
                .putBoolean(ENABLED_KEY_PREFIX + page.name(), enabled)
                .apply();

        if (!enabled && getDefault(preferences) == page) {
            List<Page> stillEnabled = getEnabledPages(preferences);

            if (!stillEnabled.isEmpty()) {
                setDefault(preferences, stillEnabled.get(0));
            }
        }
    }

    /**
     * In declaration order (Inicio, Multimedia, Utilidades) - the same
     * fixed order the pager always uses, just filtered down to whichever
     * of them are currently enabled.
     */
    @NonNull
    public static List<Page> getEnabledPages(@NonNull SharedPreferences preferences) {
        List<Page> pages = new ArrayList<>();

        for (Page page : Page.values()) {
            if (isEnabled(preferences, page)) {
                pages.add(page);
            }
        }

        return pages;
    }

    /**
     * The page the Dashboard should land on when it opens. Falls back to
     * the first enabled page if the stored default was since disabled (or
     * nothing was ever stored), and to HOME as an absolute last resort -
     * that path shouldn't be reachable in practice since setEnabled()
     * refuses to disable the last enabled page, but a fresh install with
     * no preferences written yet still needs an answer.
     */
    @NonNull
    public static Page getDefault(@NonNull SharedPreferences preferences) {
        String stored = preferences.getString(DEFAULT_KEY, Page.HOME.name());

        try {
            Page page = Page.valueOf(stored);

            if (isEnabled(preferences, page)) {
                return page;
            }
        } catch (IllegalArgumentException ignored) {
        }

        List<Page> enabled = getEnabledPages(preferences);

        return enabled.isEmpty() ? Page.HOME : enabled.get(0);
    }

    public static void setDefault(@NonNull SharedPreferences preferences, @NonNull Page page) {
        preferences.edit()
                .putString(DEFAULT_KEY, page.name())
                .apply();
    }
}
