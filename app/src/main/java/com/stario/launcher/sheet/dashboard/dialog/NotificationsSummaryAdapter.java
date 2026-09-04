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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.stario.launcher.R;
import com.stario.launcher.apps.LauncherApplication;
import com.stario.launcher.apps.ProfileManager;
import com.stario.launcher.preferences.Vibrations;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.icons.AdaptiveIconView;

import java.util.List;

/**
 * Backs the Utilidades page's "Notificaciones" summary - one row per app
 * with active notifications, most notifications first. Tapping a row
 * launches that app, same as every other app row on the Dashboard.
 */
class NotificationsSummaryAdapter extends RecyclerView.Adapter<NotificationsSummaryAdapter.EntryViewHolder> {
    private final ThemedActivity activity;
    private final LayoutInflater inflater;
    private List<NotificationsSummary.Entry> entries;

    NotificationsSummaryAdapter(ThemedActivity activity, List<NotificationsSummary.Entry> entries) {
        this.activity = activity;
        this.inflater = LayoutInflater.from(activity);
        this.entries = entries;
    }

    void setEntries(List<NotificationsSummary.Entry> entries) {
        this.entries = entries;

        notifyDataSetChanged();
    }

    static class EntryViewHolder extends RecyclerView.ViewHolder {
        final AdaptiveIconView icon;
        final TextView label;
        final TextView count;

        EntryViewHolder(View itemView) {
            super(itemView);

            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
            count = itemView.findViewById(R.id.count);
        }
    }

    @NonNull
    @Override
    public EntryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.dashboard_notification_item, parent, false);

        return new EntryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EntryViewHolder holder, int position) {
        NotificationsSummary.Entry entry = entries.get(position);

        LauncherApplication application =
                ProfileManager.getInstance().getApplication(entry.packageName);

        if (application != null) {
            holder.icon.setApplication(application);
            holder.label.setText(application.getLabel());
        } else {
            holder.icon.setIcon(null);
            holder.label.setText(entry.packageName);
        }

        holder.count.setText(String.valueOf(entry.count));

        holder.itemView.setOnClickListener(v -> {
            Vibrations.getInstance().vibrate();

            if (application != null) {
                application.launch(activity);
            }
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }
}
