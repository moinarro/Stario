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

package com.stario.launcher.sheet.widgets;

import com.google.gson.annotations.SerializedName;
import com.stario.launcher.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * The persisted contents of one "widget stack" slot: an ordered list of
 * real, independently bound AppWidget ids that are paged through
 * horizontally inside a single grid tile. Stored under Entry.WIDGET_STACKS,
 * keyed by the stack's own {@link Widget#id} (the id that positions the
 * stack itself in the grid - a real allocated AppWidgetHost id, but never
 * bound to any provider).
 */
public class WidgetStack {
    @SerializedName("children")
    public List<Integer> children;

    // Optional, user-set label shown in the stack's header. Null/blank
    // means "no custom name" - existing, previously-saved stacks simply
    // deserialize with this null (Gson leaves missing fields at their
    // default), so nothing needs migrating.
    @SerializedName("name")
    public String name;

    public WidgetStack() {
        this.children = new ArrayList<>();
    }

    public static WidgetStack deserialize(String data) {
        try {
            WidgetStack stack = Utils.getGsonInstance().fromJson(data, WidgetStack.class);

            if (stack == null) {
                return null;
            }

            if (stack.children == null) {
                stack.children = new ArrayList<>();
            }

            return stack;
        } catch (Exception exception) {
            return null;
        }
    }

    public String serialize() {
        return Utils.getGsonInstance().toJson(this);
    }
}
