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
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;

import com.stario.launcher.R;
import com.stario.launcher.apps.LauncherApplication;
import com.stario.launcher.apps.ProfileManager;
import com.stario.launcher.preferences.Vibrations;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.icons.AdaptiveIconView;

import java.util.List;

/**
 * Backs a single Dashboard row (Favoritos, Recientes, or the Multimedia
 * tab's editable app list): a simple, finite horizontal list of app icons
 * with a label underneath - unlike GlanceQuickAppsAdapter, there's no
 * infinite-scroll wrapping here, since the Dashboard's lists are short and
 * meant to be skimmed, not browsed.
 * <p>
 * When {@code editable}, an extra trailing "+" tile is appended (tap ->
 * {@link Listener#onAddClick()}) and long-pressing a real item reports
 * {@link Listener#onAppLongClick}, so the Multimedia tab can let the user
 * curate its list directly - Favoritos/Recientes stay read-only here since
 * they're already edited elsewhere (Glance's popup, and simply using apps).
 */
class DashboardAppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_APP = 0;
    private static final int VIEW_TYPE_ADD = 1;

    private final ThemedActivity activity;
    private final LayoutInflater inflater;
    private final Listener listener;
    private final boolean editable;
    private List<String> packages;

    DashboardAppAdapter(ThemedActivity activity, List<String> packages, Listener listener) {
        this(activity, packages, listener, false);
    }

    DashboardAppAdapter(ThemedActivity activity, List<String> packages,
                        Listener listener, boolean editable) {
        this.activity = activity;
        this.inflater = LayoutInflater.from(activity);
        this.packages = packages;
        this.listener = listener;
        this.editable = editable;
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

    @Override
    public int getItemViewType(int position) {
        return editable && position == packages.size() ? VIEW_TYPE_ADD : VIEW_TYPE_APP;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.dashboard_app_item, parent, false);

        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        AppViewHolder holder = (AppViewHolder) viewHolder;

        if (getItemViewType(position) == VIEW_TYPE_ADD) {
            holder.icon.setIcon(AppCompatResources.getDrawable(activity, R.drawable.ic_add));
            holder.label.setText(R.string.dashboard_add_app);

            holder.itemView.setOnClickListener(v -> {
                Vibrations.getInstance().vibrate();

                if (listener != null) {
                    listener.onAddClick();
                }
            });
            holder.itemView.setOnLongClickListener(null);

            return;
        }

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

        if (editable) {
            holder.itemView.setOnLongClickListener(v -> {
                Vibrations.getInstance().vibrate();

                if (listener != null) {
                    listener.onAppLongClick(packageName);
                }

                return true;
            });
        } else {
            holder.itemView.setOnLongClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return packages.size() + (editable ? 1 : 0);
    }

    public interface Listener {
        void onAppClick(String packageName);

        default void onAppLongClick(String packageName) {
        }

        default void onAddClick() {
        }
    }
}
