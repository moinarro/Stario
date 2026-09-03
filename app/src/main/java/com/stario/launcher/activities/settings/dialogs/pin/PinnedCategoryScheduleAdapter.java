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
import android.app.TimePickerDialog;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.stario.launcher.R;
import com.stario.launcher.activities.launcher.widgets.pins.PinnedCategorySchedule;
import com.stario.launcher.apps.Category;
import com.stario.launcher.apps.CategoryManager;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.popup.PopupMenu;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

class PinnedCategoryScheduleAdapter extends RecyclerView.Adapter<PinnedCategoryScheduleAdapter.ViewHolder> {
    private final ThemedActivity activity;
    private final LayoutInflater inflater;
    private final List<PinnedCategorySchedule.TimeSlot> slots;
    private final OnScheduleChangeListener listener;

    PinnedCategoryScheduleAdapter(ThemedActivity activity, List<PinnedCategorySchedule.TimeSlot> slots,
                                  OnScheduleChangeListener listener) {
        this.activity = activity;
        this.inflater = LayoutInflater.from(activity);
        this.slots = slots;
        this.listener = listener;

        setHasStableIds(true);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView startTime;
        private final TextView endTime;
        private final TextView categoryName;
        private final ImageView delete;

        public ViewHolder(View itemView) {
            super(itemView);

            startTime = itemView.findViewById(R.id.start_time);
            endTime = itemView.findViewById(R.id.end_time);
            categoryName = itemView.findViewById(R.id.category_name);
            delete = itemView.findViewById(R.id.delete);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void commit() {
        if (listener != null) {
            listener.onScheduleChanged();
        }

        notifyDataSetChanged();
    }

    private String format(int minuteOfDay) {
        return String.format(Locale.getDefault(), "%02d:%02d",
                (minuteOfDay / 60) % 24, minuteOfDay % 60);
    }

    private void pickTime(int initialMinuteOfDay, TimePickerDialog.OnTimeSetListener listener) {
        int hour = (initialMinuteOfDay / 60) % 24;
        int minute = initialMinuteOfDay % 60;

        new TimePickerDialog(activity, (view, selectedHour, selectedMinute) ->
                listener.onTimeSet(view, selectedHour, selectedMinute),
                hour, minute, DateFormat.is24HourFormat(activity)).show();
    }

    private void pickCategory(View anchor, PinnedCategorySchedule.TimeSlot slot) {
        CategoryManager categoryManager = CategoryManager.getInstance();
        PopupMenu menu = new PopupMenu(activity);

        for (int index = 0; index < categoryManager.size(); index++) {
            Category category = categoryManager.get(index);

            if (category == null) {
                continue;
            }

            String name = categoryManager.getCategoryName(category.identifier);

            menu.add(new PopupMenu.Item(name, null, view -> {
                slot.categoryId = category.identifier.toString();

                commit();
            }));
        }

        menu.show(activity, anchor, PopupMenu.PIVOT_DEFAULT);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PinnedCategorySchedule.TimeSlot slot = slots.get(position);

        holder.startTime.setText(format(slot.startMinute));
        holder.endTime.setText(format(slot.endMinute));

        String categoryName = null;
        if (slot.categoryId != null) {
            try {
                categoryName = CategoryManager.getInstance()
                        .getCategoryName(UUID.fromString(slot.categoryId));
            } catch (IllegalArgumentException ignored) {
            }
        }

        holder.categoryName.setText(categoryName != null ? categoryName :
                activity.getString(R.string.pinned_category_schedule_choose_category));

        holder.startTime.setOnClickListener(view ->
                pickTime(slot.startMinute, (picker, hour, minute) -> {
                    slot.startMinute = hour * 60 + minute;

                    commit();
                }));

        holder.endTime.setOnClickListener(view ->
                pickTime(slot.endMinute, (picker, hour, minute) -> {
                    slot.endMinute = hour * 60 + minute;

                    commit();
                }));

        holder.categoryName.setOnClickListener(view -> pickCategory(view, slot));

        holder.delete.setOnClickListener(view -> {
            slots.remove(slot);

            commit();
        });
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(inflater.inflate(R.layout.schedule_slot_item, parent, false));
    }

    @Override
    public long getItemId(int position) {
        return slots.get(position).id.hashCode();
    }

    @Override
    public int getItemCount() {
        return slots.size();
    }

    public interface OnScheduleChangeListener {
        void onScheduleChanged();
    }
}
