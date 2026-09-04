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

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.stario.launcher.BuildConfig;
import com.stario.launcher.R;
import com.stario.launcher.activities.launcher.widgets.glance.extensions.apps.GlanceQuickApps;
import com.stario.launcher.activities.launcher.widgets.glance.extensions.battery.Battery;
import com.stario.launcher.activities.launcher.widgets.glance.extensions.focus.Focus;
import com.stario.launcher.apps.LauncherApplication;
import com.stario.launcher.apps.ProfileManager;
import com.stario.launcher.apps.RecentApps;
import com.stario.launcher.preferences.Entry;
import com.stario.launcher.preferences.Vibrations;
import com.stario.launcher.services.NotificationService;
import com.stario.launcher.sheet.SheetDialogFragment;
import com.stario.launcher.sheet.SheetType;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.Measurements;
import com.stario.launcher.ui.common.FadingEdgeLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * A small "mini-dashboard" occupying TOP_SHEET, the one sheet slot nothing
 * else claims by default (see SheetType.getDefaultSheetTypeForSheetDialogFragment).
 * Reached without touching the one-finger swipe-down that reveals the
 * system notification shade - see Launcher.attachGestures(), which opens
 * this only as a fallback when a two-finger swipe DOWN has nothing assigned
 * in Gestures.
 * <p>
 * Content is entirely sourced from lists that already exist elsewhere
 * rather than a new favorites mechanism: "Favoritos" is GlanceQuickApps'
 * curated list, "Recientes" is RecentApps' launch history.
 */
public class DashboardDialog extends SheetDialogFragment {
    private SharedPreferences quickAppsPreferences;
    private SharedPreferences recentAppsPreferences;
    private DashboardAppAdapter favoritesAdapter;
    private DashboardAppAdapter recentAdapter;
    private MediaSessionManager mediaSessionManager;
    private MediaController nowPlayingSession;
    private ThemedActivity activity;
    private ViewGroup placeholder;
    private View favoritesSection;
    private View recentSection;
    private View nowPlayingCard;
    private ImageView nowPlayingCover;
    private ImageView nowPlayingPlayPause;
    private TextView nowPlayingTitle;
    private TextView nowPlayingArtist;
    private Battery battery;
    private Focus focus;
    private View root;

    public DashboardDialog() {
        super();
    }

    public DashboardDialog(SheetType type) {
        super(type);
    }

    public static String getName() {
        return "Dashboard";
    }

    @Override
    public boolean requiresEagerInitialization() {
        return false;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        this.activity = (ThemedActivity) context;
        this.quickAppsPreferences = activity.getApplicationContext()
                .getSharedPreferences(Entry.GLANCE_QUICK_APPS);
        this.recentAppsPreferences = activity.getApplicationContext()
                .getSharedPreferences(Entry.RECENT_APPS);
        this.mediaSessionManager = (MediaSessionManager)
                activity.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.widget_dashboard, container, false);

        FadingEdgeLayout fader = root.findViewById(R.id.fader);
        ViewGroup content = root.findViewById(R.id.content);
        placeholder = root.findViewById(R.id.placeholder);
        favoritesSection = root.findViewById(R.id.favorites_section);
        recentSection = root.findViewById(R.id.recent_section);

        // Reuses the same Glance chips rather than inventing a second status
        // display - a quick DND toggle and battery read, right where the
        // user is already looking for "what's going on right now".
        LinearLayout statusRow = root.findViewById(R.id.status_row);

        battery = new Battery();
        View batteryView = battery.inflate(activity, statusRow);
        batteryView.setOnClickListener(battery.getClickListener());
        statusRow.addView(batteryView);

        focus = new Focus();
        View focusView = focus.inflate(activity, statusRow);
        focusView.setOnClickListener(focus.getClickListener());
        statusRow.addView(focusView);

        nowPlayingCard = root.findViewById(R.id.now_playing_card);
        nowPlayingCover = root.findViewById(R.id.now_playing_cover);
        nowPlayingTitle = root.findViewById(R.id.now_playing_title);
        nowPlayingArtist = root.findViewById(R.id.now_playing_artist);
        nowPlayingPlayPause = root.findViewById(R.id.now_playing_play_pause);
        ImageView nowPlayingPrevious = root.findViewById(R.id.now_playing_previous);
        ImageView nowPlayingNext = root.findViewById(R.id.now_playing_next);

        nowPlayingPlayPause.setOnClickListener(v -> {
            if (nowPlayingSession == null) {
                return;
            }

            Vibrations.getInstance().vibrate();

            PlaybackState state = nowPlayingSession.getPlaybackState();

            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                nowPlayingSession.getTransportControls().pause();
            } else {
                nowPlayingSession.getTransportControls().play();
            }

            nowPlayingPlayPause.postDelayed(this::updateNowPlaying, 200);
        });
        nowPlayingPrevious.setOnClickListener(v -> {
            if (nowPlayingSession != null) {
                Vibrations.getInstance().vibrate();

                nowPlayingSession.getTransportControls().skipToPrevious();
            }
        });
        nowPlayingNext.setOnClickListener(v -> {
            if (nowPlayingSession != null) {
                Vibrations.getInstance().vibrate();

                nowPlayingSession.getTransportControls().skipToNext();
            }
        });

        RecyclerView recyclerFavorites = root.findViewById(R.id.recycler_favorites);
        RecyclerView recyclerRecent = root.findViewById(R.id.recycler_recent);

        recyclerFavorites.setLayoutManager(new LinearLayoutManager(activity,
                LinearLayoutManager.HORIZONTAL, false));
        recyclerRecent.setLayoutManager(new LinearLayoutManager(activity,
                LinearLayoutManager.HORIZONTAL, false));

        DashboardAppAdapter.Listener launchListener = packageName -> {
            LauncherApplication application = ProfileManager.getInstance().getApplication(packageName);

            if (application != null) {
                application.launch(activity);
            }

            hide(true);
        };

        favoritesAdapter = new DashboardAppAdapter(activity, new ArrayList<>(), launchListener);
        recentAdapter = new DashboardAppAdapter(activity, new ArrayList<>(), launchListener);

        recyclerFavorites.setAdapter(favoritesAdapter);
        recyclerRecent.setAdapter(recentAdapter);

        Measurements.addStatusBarListener(value -> {
            fader.setFadeSizes(value, 0, Measurements.getNavHeight(), 0);

            content.setPadding(content.getPaddingLeft(), value,
                    content.getPaddingRight(), content.getPaddingBottom());
        });
        Measurements.addNavListener(value -> {
            fader.setFadeSizes(Measurements.getSysUIHeight(), 0, value, 0);

            content.setPadding(content.getPaddingLeft(), content.getPaddingTop(),
                    content.getPaddingRight(), value);
        });

        setOnBackPressed(() -> {
            hide(true);

            return true;
        });

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();

        refresh();
    }

    /**
     * Both source lists can change while this sheet is closed (favorites
     * through the Glance long-press popup, recents through simply using
     * the launcher), so pull a fresh snapshot every time the sheet comes
     * back on screen rather than only once in onCreateView().
     */
    private void refresh() {
        if (battery != null) {
            battery.update();
        }

        if (focus != null) {
            focus.update();
        }

        updateNowPlaying();

        if (favoritesAdapter == null || recentAdapter == null) {
            return;
        }

        List<String> favorites = filterInstalled(GlanceQuickApps.getPackages(quickAppsPreferences));
        List<String> recent = filterInstalled(RecentApps.getPackages(recentAppsPreferences));

        favoritesAdapter.setPackages(favorites);
        recentAdapter.setPackages(recent);

        favoritesSection.setVisibility(favorites.isEmpty() ? View.GONE : View.VISIBLE);
        recentSection.setVisibility(recent.isEmpty() ? View.GONE : View.VISIBLE);

        placeholder.setVisibility(favorites.isEmpty() && recent.isEmpty() ?
                View.VISIBLE : View.GONE);
    }

    /**
     * A compact playback card - the same MediaSessionManager/NotificationService
     * approach the Glance Media chip already uses (and the same "Do Not Disturb
     * access"-style separate permission: notification listener access), just
     * without that chip's popup/transition machinery, since this only needs to
     * show current state and forward transport commands.
     */
    private void updateNowPlaying() {
        if (nowPlayingCard == null || mediaSessionManager == null) {
            return;
        }

        if (!NotificationManagerCompat.getEnabledListenerPackages(activity)
                .contains(BuildConfig.APPLICATION_ID)) {
            nowPlayingSession = null;
            nowPlayingCard.setVisibility(View.GONE);

            return;
        }

        List<MediaController> sessions;

        try {
            sessions = mediaSessionManager.getActiveSessions(
                    new ComponentName(activity, NotificationService.class));
        } catch (SecurityException exception) {
            nowPlayingSession = null;
            nowPlayingCard.setVisibility(View.GONE);

            return;
        }

        MediaController candidate = null;

        for (MediaController controller : sessions) {
            PlaybackState state = controller.getPlaybackState();
            AudioAttributes attrs = controller.getPlaybackInfo().getAudioAttributes();

            if (attrs != null && attrs.getUsage() == AudioAttributes.USAGE_MEDIA &&
                    state != null && state.getState() == PlaybackState.STATE_PLAYING &&
                    hasTitle(controller.getMetadata())) {
                candidate = controller;

                break;
            }
        }

        // Nothing actively playing - fall back to any paused session with
        // valid metadata, so a track paused on the way in still shows up
        // here (and can be resumed) instead of the card just vanishing.
        if (candidate == null) {
            for (MediaController controller : sessions) {
                AudioAttributes attrs = controller.getPlaybackInfo().getAudioAttributes();

                if (attrs != null && attrs.getUsage() == AudioAttributes.USAGE_MEDIA &&
                        hasTitle(controller.getMetadata())) {
                    candidate = controller;

                    break;
                }
            }
        }

        nowPlayingSession = candidate;

        if (candidate == null) {
            nowPlayingCard.setVisibility(View.GONE);

            return;
        }

        MediaMetadata metadata = candidate.getMetadata();

        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        nowPlayingTitle.setText(title != null ? title.trim() : "");

        String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        nowPlayingArtist.setText(artist != null && !artist.isBlank() ?
                artist.trim() : activity.getResources().getString(R.string.unknown_artist));

        PlaybackState state = candidate.getPlaybackState();
        boolean isPlaying = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        nowPlayingPlayPause.setImageResource(isPlaying ?
                R.drawable.ic_media_pause : R.drawable.ic_media_play);

        updateCover(metadata);

        nowPlayingCard.setVisibility(View.VISIBLE);
    }

    private boolean hasTitle(MediaMetadata metadata) {
        if (metadata == null) {
            return false;
        }

        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);

        return title != null && !title.isEmpty();
    }

    private void updateCover(MediaMetadata metadata) {
        String coverUri = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI);

        if (coverUri == null || coverUri.isBlank()) {
            coverUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
        }

        if (coverUri == null || coverUri.isBlank()) {
            coverUri = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        }

        if (coverUri != null && !coverUri.isBlank()) {
            Glide.with(activity).load(Uri.parse(coverUri)).into(nowPlayingCover);

            return;
        }

        Bitmap bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);

        if (bitmap == null) {
            bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        }

        if (bitmap == null) {
            bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        }

        if (bitmap != null) {
            Glide.with(activity).load(bitmap).into(nowPlayingCover);
        } else {
            nowPlayingCover.setImageDrawable(null);
        }
    }

    private List<String> filterInstalled(List<String> packages) {
        List<String> filtered = new ArrayList<>(packages.size());

        for (String packageName : packages) {
            if (ProfileManager.getInstance().getApplication(packageName) != null) {
                filtered.add(packageName);
            }
        }

        return filtered;
    }
}
