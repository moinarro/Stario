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

package com.stario.launcher.activities.settings.dialogs.widgets;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.stario.launcher.R;
import com.stario.launcher.preferences.Entry;
import com.stario.launcher.sheet.widgets.WidgetSchedule;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.dialogs.ActionDialog;
import com.stario.launcher.ui.recyclers.DividerItemDecorator;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings for WidgetSchedule: an enabled switch plus the list of named
 * time-of-day profiles a widget or stack can be assigned to (from its own
 * long-press menu / options menu - see WidgetsDialog#assignWidgetSchedule).
 * Mirrors PinnedCategoryScheduleDialog's add/edit/delete list shape.
 */
public class WidgetScheduleDialog extends ActionDialog {
    private final SharedPreferences preferences;
    private final List<WidgetSchedule.TimeSlot> slots;

    public WidgetScheduleDialog(@NonNull ThemedActivity activity) {
        super(activity);

        this.preferences = activity.getApplicationContext()
                .getSharedPreferences(Entry.WIDGET_SCHEDULE);
        this.slots = new ArrayList<>(WidgetSchedule.sorted(
                WidgetSchedule.loadSlots(preferences)));
    }

    @NonNull
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected View inflateContent(LayoutInflater inflater) {
        View root = inflater.inflate(R.layout.pop_up_widget_schedule, null);
        RecyclerView recycler = root.findViewById(R.id.recycler);
        View empty = root.findViewById(R.id.empty);
        MaterialSwitch enabledSwitch = root.findViewById(R.id.enabled);

        recycler.setLayoutManager(new LinearLayoutManager(activity,
                LinearLayoutManager.VERTICAL, false));
        recycler.addItemDecoration(new DividerItemDecorator(activity,
                MaterialDividerItemDecoration.VERTICAL));

        Runnable persistAndUpdateEmptyState = () -> {
            WidgetSchedule.saveSlots(preferences, slots);

            empty.setVisibility(slots.isEmpty() ? View.VISIBLE : View.GONE);
        };

        WidgetScheduleAdapter adapter = new WidgetScheduleAdapter(activity, slots,
                persistAndUpdateEmptyState::run);

        recycler.setAdapter(adapter);
        persistAndUpdateEmptyState.run();

        enabledSwitch.setChecked(WidgetSchedule.isEnabled(preferences));
        enabledSwitch.jumpDrawablesToCurrentState();
        enabledSwitch.setOnCheckedChangeListener((button, checked) ->
                WidgetSchedule.setEnabled(preferences, checked));

        root.findViewById(R.id.enabled_container).setOnClickListener(view ->
                enabledSwitch.performClick());

        root.findViewById(R.id.add_container).setOnClickListener(view -> {
            slots.add(new WidgetSchedule.TimeSlot(null, 9 * 60, 17 * 60));

            adapter.notifyItemInserted(slots.size() - 1);
            persistAndUpdateEmptyState.run();
        });

        return root;
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
