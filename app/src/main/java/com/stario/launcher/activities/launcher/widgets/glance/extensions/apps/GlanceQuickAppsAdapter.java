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

/**
 * Backs the quick-apps popup grid. Reports an effectively unbounded item
 * count and maps position to the real list via modulo, so both scroll
 * modes (paged, or the continuous auto-scrolling marquee) can loop
 * indefinitely instead of stopping at an edge. See GlanceQuickApps for
 * how the starting position is chosen to keep this illusion seamless.
 */
class GlanceQuickAppsAdapter extends RecyclerView.Adapter<GlanceQuickAppsAdapter.AppViewHolder> {
    private final LayoutInflater inflater;
    private final List<String> packages;
    private final int iconSizePx;
    private final Listener listener;

    GlanceQuickAppsAdapter(ThemedActivity activity, List<String> packages,
                           int iconSizePx, Listener listener) {
        this.inflater = LayoutInflater.from(activity);
        this.packages = packages;
        this.iconSizePx = iconSizePx;
        this.listener = listener;
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        final AdaptiveIconView icon;

        AppViewHolder(View itemView) {
            super(itemView);

            icon = itemView.findViewById(R.id.icon);
        }
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.glance_quick_apps_item, parent, false);

        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = iconSizePx;
        params.height = iconSizePx;
        view.setLayoutParams(params);

        int padding = iconSizePx / 9;
        view.setPadding(padding, padding, padding, padding);

        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        if (packages.isEmpty()) {
            return;
        }

        String packageName = packages.get(Math.floorMod(position, packages.size()));
        LauncherApplication application = ProfileManager.getInstance().getApplication(packageName);

        if (application != null) {
            holder.icon.setApplication(application);
        }

        holder.icon.setOnClickListener(v -> {
            Vibrations.getInstance().vibrate();

            if (listener != null) {
                listener.onAppClick(packageName);
            }
        });

        holder.icon.setOnLongClickListener(v -> {
            Vibrations.getInstance().vibrate();

            if (listener != null) {
                listener.onAppLongClick(packageName);
            }

            return true;
        });
    }

    @Override
    public int getItemCount() {
        // A real, finite count would make both the paged and the continuous
        // scroll mode dead-end at the last item; report an effectively
        // unbounded count instead and wrap onBindViewHolder()'s position
        // via modulo so scrolling in either direction never runs out.
        return packages.isEmpty() ? 0 : Integer.MAX_VALUE;
    }

    public interface Listener {
        void onAppClick(String packageName);

        void onAppLongClick(String packageName);
    }
}
