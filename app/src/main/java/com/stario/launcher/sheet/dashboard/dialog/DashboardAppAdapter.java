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
 * Backs a single Dashboard row (Favoritos or Recientes): a simple, finite
 * horizontal list of app icons with a label underneath - unlike
 * GlanceQuickAppsAdapter, there's no infinite-scroll wrapping here, since
 * the Dashboard's lists are short and meant to be skimmed, not browsed.
 */
class DashboardAppAdapter extends RecyclerView.Adapter<DashboardAppAdapter.AppViewHolder> {
    private final LayoutInflater inflater;
    private final Listener listener;
    private List<String> packages;

    DashboardAppAdapter(ThemedActivity activity, List<String> packages, Listener listener) {
        this.inflater = LayoutInflater.from(activity);
        this.packages = packages;
        this.listener = listener;
    }

    void setPackages(List<String> packages) {
        this.packages = packages;

        notifyDataSetChanged();
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        final AdaptiveIconView icon;
        final TextView label;

        AppViewHolder(View itemView) {
            super(itemView);

            icon = itemView.findViewById(R.id.icon);
            label = itemView.findViewById(R.id.label);
        }
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.dashboard_app_item, parent, false);

        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        String packageName = packages.get(position);
        LauncherApplication application = ProfileManager.getInstance().getApplication(packageName);

        if (application != null) {
            holder.icon.setApplication(application);
            holder.label.setText(application.getLabel());
        }

        holder.itemView.setOnClickListener(v -> {
            Vibrations.getInstance().vibrate();

            if (listener != null) {
                listener.onAppClick(packageName);
            }
        });
    }

    @Override
    public int getItemCount() {
        return packages.size();
    }

    public interface Listener {
        void onAppClick(String packageName);
    }
}
