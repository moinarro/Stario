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

package com.stario.launcher.activities.launcher.widgets.glance.extensions.apps;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.stario.launcher.R;
import com.stario.launcher.apps.LauncherApplication;
import com.stario.launcher.apps.ProfileManager;
import com.stario.launcher.preferences.Vibrations;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.icons.AdaptiveIconView;

import java.util.List;

class GlanceQuickAppsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_APP = 0;
    private static final int TYPE_ADD = 1;

    private final ThemedActivity activity;
    private final LayoutInflater inflater;
    private final List<String> packages;
    private final Listener listener;

    GlanceQuickAppsAdapter(ThemedActivity activity, List<String> packages, Listener listener) {
        this.activity = activity;
        this.inflater = LayoutInflater.from(activity);
        this.packages = packages;
        this.listener = listener;

        setHasStableIds(true);
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        final AdaptiveIconView icon;

        AppViewHolder(View itemView) {
            super(itemView);

            icon = itemView.findViewById(R.id.icon);
        }
    }

    static class AddViewHolder extends RecyclerView.ViewHolder {
        AddViewHolder(View itemView) {
            super(itemView);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return position < packages.size() ? TYPE_APP : TYPE_ADD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_ADD) {
            View view = inflater.inflate(R.layout.glance_quick_apps_add_item, parent, false);

            view.setOnClickListener(v -> {
                Vibrations.getInstance().vibrate();

                if (listener != null) {
                    listener.onAddClick();
                }
            });

            return new AddViewHolder(view);
        }

        View view = inflater.inflate(R.layout.glance_quick_apps_item, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AppViewHolder) {
            String packageName = packages.get(position);
            LauncherApplication application = ProfileManager.getInstance().getApplication(packageName);

            AppViewHolder appHolder = (AppViewHolder) holder;

            if (application != null) {
                appHolder.icon.setApplication(application);
            }

            appHolder.icon.setOnClickListener(v -> {
                Vibrations.getInstance().vibrate();

                if (listener != null) {
                    listener.onAppClick(packageName);
                }
            });

            appHolder.icon.setOnLongClickListener(v -> {
                Vibrations.getInstance().vibrate();

                if (listener != null) {
                    listener.onAppLongClick(packageName);
                }

                return true;
            });
        }
    }

    @Override
    public long getItemId(int position) {
        return position < packages.size() ? packages.get(position).hashCode() : Long.MAX_VALUE;
    }

    @Override
    public int getItemCount() {
        return packages.size() + 1;
    }

    public interface Listener {
        void onAppClick(String packageName);

        void onAppLongClick(String packageName);

        void onAddClick();
    }
}
