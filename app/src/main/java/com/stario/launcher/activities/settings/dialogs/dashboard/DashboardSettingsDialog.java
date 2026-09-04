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

package com.stario.launcher.activities.settings.dialogs.dashboard;

import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.stario.launcher.R;
import com.stario.launcher.preferences.Entry;
import com.stario.launcher.sheet.dashboard.dialog.DashboardPages;
import com.stario.launcher.sheet.dashboard.dialog.DashboardPages.Page;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.dialogs.ActionDialog;

import java.util.EnumMap;
import java.util.Map;

/**
 * Which of the Dashboard's pages (Inicio, Multimedia, Utilidades) are
 * enabled, and which one it opens on - see DashboardPages for the
 * preferences themselves and DashboardDialog for where they're read.
 */
public class DashboardSettingsDialog extends ActionDialog {
    private final SharedPreferences preferences;
    private final Map<Page, MaterialSwitch> switches;
    private final Map<Page, Integer> defaultButtonIds;
    private MaterialButtonToggleGroup defaultGroup;

    public DashboardSettingsDialog(@NonNull ThemedActivity activity) {
        super(activity);

        this.preferences = activity.getApplicationContext().getSharedPreferences(Entry.DASHBOARD_SETTINGS);
        this.switches = new EnumMap<>(Page.class);
        this.defaultButtonIds = new EnumMap<>(Page.class);

        defaultButtonIds.put(Page.HOME, R.id.default_page_home);
        defaultButtonIds.put(Page.MULTIMEDIA, R.id.default_page_media);
        defaultButtonIds.put(Page.UTILITIES, R.id.default_page_utilities);
    }

    @NonNull
    @Override
    protected View inflateContent(LayoutInflater inflater) {
        View root = inflater.inflate(R.layout.pop_up_dashboard, null);

        setupPageSwitch(root, Page.HOME, R.id.page_home_container, R.id.page_home);
        setupPageSwitch(root, Page.MULTIMEDIA, R.id.page_media_container, R.id.page_media);
        setupPageSwitch(root, Page.UTILITIES, R.id.page_utilities_container, R.id.page_utilities);

        defaultGroup = root.findViewById(R.id.default_page);
        setupDefaultGroup();

        return root;
    }

    private void setupPageSwitch(View root, Page page, int containerId, int switchId) {
        MaterialSwitch switchView = root.findViewById(switchId);
        switches.put(page, switchView);

        switchView.setChecked(DashboardPages.isEnabled(preferences, page));
        switchView.jumpDrawablesToCurrentState();

        switchView.setOnCheckedChangeListener((button, checked) -> {
            DashboardPages.setEnabled(preferences, page, checked);

            // setEnabled() refuses to disable the last enabled page, so
            // reflect what actually happened rather than trusting the
            // switch's own new state.
            boolean actuallyEnabled = DashboardPages.isEnabled(preferences, page);

            if (checked && !actuallyEnabled) {
                switchView.setChecked(false);
            } else if (!checked && actuallyEnabled) {
                switchView.setChecked(true);
            }

            updateSwitchAvailability();
            updateDefaultButtonAvailability();
            reflectStoredDefault();
        });

        root.findViewById(containerId).setOnClickListener(view -> switchView.performClick());
    }

    /**
     * The last remaining enabled switch can't be turned off from here
     * either (matches DashboardPages.setEnabled() refusing it) - grayed
     * out and unclickable instead of allowing a tap that would silently
     * do nothing.
     */
    private void updateSwitchAvailability() {
        boolean onlyOneEnabled = DashboardPages.getEnabledPages(preferences).size() <= 1;

        for (Map.Entry<Page, MaterialSwitch> entry : switches.entrySet()) {
            MaterialSwitch switchView = entry.getValue();
            boolean isTheOneEnabled = onlyOneEnabled && switchView.isChecked();

            switchView.setEnabled(!isTheOneEnabled);
        }
    }

    private void setupDefaultGroup() {
        updateSwitchAvailability();
        updateDefaultButtonAvailability();
        reflectStoredDefault();

        defaultGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }

            for (Map.Entry<Page, Integer> entry : defaultButtonIds.entrySet()) {
                if (entry.getValue() == checkedId) {
                    DashboardPages.setDefault(preferences, entry.getKey());

                    break;
                }
            }
        });
    }

    private void updateDefaultButtonAvailability() {
        for (Map.Entry<Page, Integer> entry : defaultButtonIds.entrySet()) {
            View button = defaultGroup.findViewById(entry.getValue());

            if (button != null) {
                button.setEnabled(DashboardPages.isEnabled(preferences, entry.getKey()));
            }
        }
    }

    private void reflectStoredDefault() {
        Integer buttonId = defaultButtonIds.get(DashboardPages.getDefault(preferences));

        if (buttonId != null) {
            defaultGroup.check(buttonId);
        }
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
