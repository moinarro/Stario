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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.stario.launcher.R;
import com.stario.launcher.activities.launcher.widgets.glance.extensions.apps.GlanceQuickApps;
import com.stario.launcher.activities.launcher.widgets.glance.extensions.battery.Battery;
import com.stario.launcher.activities.launcher.widgets.glance.extensions.focus.Focus;
import com.stario.launcher.apps.LauncherApplication;
import com.stario.launcher.apps.ProfileManager;
import com.stario.launcher.apps.RecentApps;
import com.stario.launcher.preferences.Entry;
import com.stario.launcher.sheet.SheetDialogFragment;
import com.stario.launcher.sheet.SheetType;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.Measurements;
import com.stario.launcher.ui.common.FadingEdgeLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * A small "mini-dashboard" occupying TOP_SHEET, the one sheet slot nothing
 * else claims by default (see SheetType.getDefaultSheetTypeForSheetDialogFragment).
 * Reached without touching the one-finger swipe-down that reveals the
 * system notification shade - see Launcher.attachGestures(), which opens
 * this only as a fallback when a two-finger swipe DOWN has nothing assigned
 * in Gestures.
 * <p>
 * Content is entirely sourced from lists that already exist elsewhere
 * rather than a new favorites mechanism: "Favoritos" is GlanceQuickApps'
 * curated list, "Recientes" is RecentApps' launch history.
 */
public class DashboardDialog extends SheetDialogFragment {
    private SharedPreferences quickAppsPreferences;
    private SharedPreferences recentAppsPreferences;
    private DashboardAppAdapter favoritesAdapter;
    private DashboardAppAdapter recentAdapter;
    private ThemedActivity activity;
    private ViewGroup placeholder;
    private View favoritesSection;
    private View recentSection;
    private Battery battery;
    private Focus focus;
    private View root;

    public DashboardDialog() {
        super();
    }

    public DashboardDialog(SheetType type) {
        super(type);
    }

    public static String getName() {
        return "Dashboard";
    }

    @Override
    public boolean requiresEagerInitialization() {
        return false;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        this.activity = (ThemedActivity) context;
        this.quickAppsPreferences = activity.getApplicationContext()
                .getSharedPreferences(Entry.GLANCE_QUICK_APPS);
        this.recentAppsPreferences = activity.getApplicationContext()
                .getSharedPreferences(Entry.RECENT_APPS);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.widget_dashboard, container, false);

        FadingEdgeLayout fader = root.findViewById(R.id.fader);
        ViewGroup content = root.findViewById(R.id.content);
        placeholder = root.findViewById(R.id.placeholder);
        favoritesSection = root.findViewById(R.id.favorites_section);
        recentSection = root.findViewById(R.id.recent_section);

        // Reuses the same Glance chips rather than inventing a second status
        // display - a quick DND toggle and battery read, right where the
        // user is already looking for "what's going on right now".
        LinearLayout statusRow = root.findViewById(R.id.status_row);

        battery = new Battery();
        View batteryView = battery.inflate(activity, statusRow);
        batteryView.setOnClickListener(battery.getClickListener());
        statusRow.addView(batteryView);

        focus = new Focus();
        View focusView = focus.inflate(activity, statusRow);
        focusView.setOnClickListener(focus.getClickListener());
        statusRow.addView(focusView);

        RecyclerView recyclerFavorites = root.findViewById(R.id.recycler_favorites);
        RecyclerView recyclerRecent = root.findViewById(R.id.recycler_recent);

        recyclerFavorites.setLayoutManager(new LinearLayoutManager(activity,
                LinearLayoutManager.HORIZONTAL, false));
        recyclerRecent.setLayoutManager(new LinearLayoutManager(activity,
                LinearLayoutManager.HORIZONTAL, false));

        DashboardAppAdapter.Listener launchListener = packageName -> {
            LauncherApplication application = ProfileManager.getInstance().getApplication(packageName);

            if (application != null) {
                application.launch(activity);
            }

            hide(true);
        };

        favoritesAdapter = new DashboardAppAdapter(activity, new ArrayList<>(), launchListener);
        recentAdapter = new DashboardAppAdapter(activity, new ArrayList<>(), launchListener);

        recyclerFavorites.setAdapter(favoritesAdapter);
        recyclerRecent.setAdapter(recentAdapter);

        Measurements.addStatusBarListener(value -> {
            fader.setFadeSizes(value, 0, Measurements.getNavHeight(), 0);

            content.setPadding(content.getPaddingLeft(), value,
                    content.getPaddingRight(), content.getPaddingBottom());
        });
        Measurements.addNavListener(value -> {
            fader.setFadeSizes(Measurements.getSysUIHeight(), 0, value, 0);

            content.setPadding(content.getPaddingLeft(), content.getPaddingTop(),
                    content.getPaddingRight(), value);
        });

        setOnBackPressed(() -> {
            hide(true);

            return true;
        });

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();

        refresh();
    }

    /**
     * Both source lists can change while this sheet is closed (favorites
     * through the Glance long-press popup, recents through simply using
     * the launcher), so pull a fresh snapshot every time the sheet comes
     * back on screen rather than only once in onCreateView().
     */
    private void refresh() {
        if (battery != null) {
            battery.update();
        }

        if (focus != null) {
            focus.update();
        }

        if (favoritesAdapter == null || recentAdapter == null) {
            return;
        }

        List<String> favorites = filterInstalled(GlanceQuickApps.getPackages(quickAppsPreferences));
        List<String> recent = filterInstalled(RecentApps.getPackages(recentAppsPreferences));

        favoritesAdapter.setPackages(favorites);
        recentAdapter.setPackages(recent);

        favoritesSection.setVisibility(favorites.isEmpty() ? View.GONE : View.VISIBLE);
        recentSection.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);

        placeholder.setVisibility(favorites.isEmpty() && recent.isEmpty() ?
                View.VISIBLE : View.GONE);
    }

    private List<String> filterInstalled(List<String> packages) {
        List<String> filtered = new ArrayList<>(packages.size());

        for (String packageName : packages) {
            if (ProfileManager.getInstance().getApplication(packageName) != null) {
                filtered.add(packageName);
            }
        }

        return filtered;
    }
}
