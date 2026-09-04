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
import com.stario.launcher.activities.launcher.widgets.glance.extensions.media.RecentMedia;
import com.stario.launcher.apps.LauncherApplication;
import com.stario.launcher.apps.ProfileManager;
import com.stario.launcher.preferences.Vibrations;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.icons.AdaptiveIconView;

import java.util.List;

/**
 * A short, vertical "what I've been listening to" list backing the
 * Multimedia tab's history section - one row per RecentMedia.Track,
 * tapping a row launches the app that played it (there's no way to
 * relaunch the exact track itself, so the app is the closest useful
 * action, same as tapping a Favoritos/Recientes tile).
 */
class RecentMediaAdapter extends RecyclerView.Adapter<RecentMediaAdapter.TrackViewHolder> {
    private final ThemedActivity activity;
    private final LayoutInflater inflater;
    private List<RecentMedia.Track> tracks;

    RecentMediaAdapter(ThemedActivity activity, List<RecentMedia.Track> tracks) {
        this.activity = activity;
        this.inflater = LayoutInflater.from(activity);
        this.tracks = tracks;
    }

    void setTracks(List<RecentMedia.Track> tracks) {
        this.tracks = tracks;

        notifyDataSetChanged();
    }

    static class TrackViewHolder extends RecyclerView.ViewHolder {
        final AdaptiveIconView icon;
        final TextView title;
        final TextView artist;

        TrackViewHolder(View itemView) {
            super(itemView);

            icon = itemView.findViewById(R.id.icon);
            title = itemView.findViewById(R.id.title);
            artist = itemView.findViewById(R.id.artist);
        }
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.dashboard_media_history_item, parent, false);

        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        RecentMedia.Track track = tracks.get(position);

        LauncherApplication application =
                ProfileManager.getInstance().getApplication(track.packageName);

        if (application != null) {
            holder.icon.setApplication(application);
        } else {
            holder.icon.setIcon(null);
        }

        holder.title.setText(track.title);
        holder.artist.setText(track.artist != null && !track.artist.isBlank() ?
                track.artist : activity.getResources().getString(R.string.unknown_artist));

        holder.itemView.setOnClickListener(v -> {
            Vibrations.getInstance().vibrate();

            if (application != null) {
                application.launch(activity);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }
}
