/*
 * Copyright (C) 2025 Răzvan Albu
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

package com.stario.launcher.sheet.widgets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;
import com.stario.launcher.utils.Utils;

public class Widget implements Comparable<Widget> {
    @SerializedName("size")
    public WidgetSize size;

    @SerializedName("id")
    public final int id;

    @SerializedName("position")
    public int position; // up-down

    // Marks this slot as a widget stack (a horizontally-scrolling carousel of
    // several real app widgets) rather than a single bound AppWidget. Absent
    // in previously-serialized entries, which Gson defaults to false, so
    // existing widgets keep working unchanged.
    @SerializedName("stack")
    public boolean isStack;

    // Optional WidgetSchedule.TimeSlot id this widget belongs to. Null
    // (the default for every previously-serialized widget) means "always
    // visible" - only a widget explicitly assigned to a slot is ever
    // hidden while that slot isn't the active one.
    @SerializedName("scheduleSlot")
    @Nullable
    public String scheduleSlotId;

    public Widget(int id, int position, WidgetSize size) {
        this.id = id;
        this.position = position;
        this.size = size;
        this.isStack = false;
    }

    public Widget(int id, int position, WidgetSize size, boolean isStack) {
        this.id = id;
        this.position = position;
        this.size = size;
        this.isStack = isStack;
    }

    public static Widget deserialize(String data) {
        try {
            Widget holder = Utils.getGsonInstance().fromJson(data, Widget.class);

            if (holder.size == null || holder.id == -1 || holder.position == -1) {
                return null;
            }

            return holder;
        } catch (Exception exception) {
            return null;
        }
    }

    public String serialize() {
        return Utils.getGsonInstance().toJson(this);
    }

    @Override
    public boolean equals(@Nullable Object object) {
        return object instanceof Widget && ((Widget) object).id == id;
    }

    @Override
    public int compareTo(@NonNull Widget widget) {
        return position - widget.position;
    }
}
