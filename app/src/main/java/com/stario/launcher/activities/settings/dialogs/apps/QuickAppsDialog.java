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

package com.stario.launcher.activities.settings.dialogs.apps;

import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.stario.launcher.R;
import com.stario.launcher.activities.launcher.widgets.glance.extensions.apps.GlanceQuickApps;
import com.stario.launcher.preferences.Entry;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.dialogs.ActionDialog;

/**
 * Lets the user configure the free-form apps list shown when long-pressing
 * the Glance (day/weather) widget: icon size, row count, and whether it
 * scrolls in discrete pages or as a continuous, looping marquee. All three
 * are read live by {@link GlanceQuickApps} the next time its popup opens,
 * so there is nothing to explicitly "apply" here.
 */
public class QuickAppsDialog extends ActionDialog {
    private final SharedPreferences preferences;

    public QuickAppsDialog(@NonNull ThemedActivity activity) {
        super(activity);

        this.preferences = activity.getApplicationContext().getSharedPreferences(Entry.GLANCE_QUICK_APPS);
    }

    @NonNull
    @Override
    protected View inflateContent(LayoutInflater inflater) {
        View root = inflater.inflate(R.layout.pop_up_quick_apps, null);

        setupIconSize(root);
        setupRows(root);
        setupScrollMode(root);

        return root;
    }

    private void setupIconSize(View root) {
        MaterialButtonToggleGroup group = root.findViewById(R.id.icon_size);

        GlanceQuickApps.IconSize current = GlanceQuickApps.getIconSize(preferences);

        switch (current) {
            case SMALL:
                group.check(R.id.icon_size_small);
                break;
            case LARGE:
                group.check(R.id.icon_size_large);
                break;
            case MEDIUM:
            default:
                group.check(R.id.icon_size_medium);
                break;
        }

        group.addOnButtonCheckedListener((toggleGroup, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }

            if (checkedId == R.id.icon_size_small) {
                GlanceQuickApps.setIconSize(preferences, GlanceQuickApps.IconSize.SMALL);
            } else if (checkedId == R.id.icon_size_large) {
                GlanceQuickApps.setIconSize(preferences, GlanceQuickApps.IconSize.LARGE);
            } else {
                GlanceQuickApps.setIconSize(preferences, GlanceQuickApps.IconSize.MEDIUM);
            }
        });
    }

    private void setupRows(View root) {
        MaterialButtonToggleGroup group = root.findViewById(R.id.rows);

        int current = GlanceQuickApps.getRows(preferences);
        int[] ids = {R.id.rows_1, R.id.rows_2, R.id.rows_3, R.id.rows_4};

        group.check(ids[Math.max(0, Math.min(ids.length - 1, current - 1))]);

        group.addOnButtonCheckedListener((toggleGroup, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }

            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == checkedId) {
                    GlanceQuickApps.setRows(preferences, i + 1);

                    break;
                }
            }
        });
    }

    private void setupScrollMode(View root) {
        MaterialButtonToggleGroup group = root.findViewById(R.id.scroll_mode);

        GlanceQuickApps.ScrollMode current = GlanceQuickApps.getScrollMode(preferences);

        group.check(current == GlanceQuickApps.ScrollMode.CONTINUOUS
                ? R.id.scroll_mode_continuous : R.id.scroll_mode_pagination);

        group.addOnButtonCheckedListener((toggleGroup, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }

            GlanceQuickApps.setScrollMode(preferences, checkedId == R.id.scroll_mode_continuous
                    ? GlanceQuickApps.ScrollMode.CONTINUOUS : GlanceQuickApps.ScrollMode.PAGINATION);
        });
    }

    @Override
    protected boolean blurBehind() {
        return true;
    }

    @Override
    protected int getDesiredInitialState() {
        return BottomSheetBehavior.STATE_EXPANDED;
    }
}
