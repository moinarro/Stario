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

package com.stario.launcher.activities.settings.dialogs.pin;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.stario.launcher.R;
import com.stario.launcher.activities.launcher.widgets.pins.PinnedCategorySchedule;
import com.stario.launcher.apps.Category;
import com.stario.launcher.apps.CategoryManager;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.dialogs.ActionDialog;
import com.stario.launcher.ui.recyclers.DividerItemDecorator;

import java.util.ArrayList;
import java.util.List;

public class PinnedCategoryScheduleDialog extends ActionDialog {
    private final SharedPreferences preferences;
    private final List<PinnedCategorySchedule.TimeSlot> slots;

    public PinnedCategoryScheduleDialog(@NonNull ThemedActivity activity, SharedPreferences preferences) {
        super(activity);

        this.preferences = preferences;
        this.slots = new ArrayList<>(PinnedCategorySchedule.sorted(
                PinnedCategorySchedule.loadSlots(preferences)));
    }

    @NonNull
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected View inflateContent(LayoutInflater inflater) {
        View root = inflater.inflate(R.layout.pop_up_pinned_category_schedule, null);
        RecyclerView recycler = root.findViewById(R.id.recycler);
        View empty = root.findViewById(R.id.empty);

        recycler.setLayoutManager(new LinearLayoutManager(activity,
                LinearLayoutManager.VERTICAL, false));
        recycler.addItemDecoration(new DividerItemDecorator(activity,
                MaterialDividerItemDecoration.VERTICAL));

        Runnable persistAndUpdateEmptyState = () -> {
            PinnedCategorySchedule.saveSlots(preferences, slots);
            PinnedCategorySchedule.apply(preferences);

            empty.setVisibility(slots.isEmpty() ? View.VISIBLE : View.GONE);
        };

        PinnedCategoryScheduleAdapter adapter = new PinnedCategoryScheduleAdapter(activity, slots,
                persistAndUpdateEmptyState::run);

        recycler.setAdapter(adapter);
        persistAndUpdateEmptyState.run();

        root.findViewById(R.id.add_container).setOnClickListener(view -> {
            String defaultCategory = getFirstCategoryIdentifier();
            slots.add(new PinnedCategorySchedule.TimeSlot(9 * 60, 17 * 60, defaultCategory));

            adapter.notifyItemInserted(slots.size() - 1);
            persistAndUpdateEmptyState.run();
        });

        return root;
    }

    private String getFirstCategoryIdentifier() {
        CategoryManager categoryManager = CategoryManager.getInstance();

        for (int index = 0; index < categoryManager.size(); index++) {
            Category category = categoryManager.get(index);

            if (category != null) {
                return category.identifier.toString();
            }
        }

        return null;
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
