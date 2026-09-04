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
import com.stario.launcher.activities.launcher.widgets.glance.extensions.focus.Focus;
import com.stario.launcher.activities.launcher.widgets.glance.extensions.media.RecentMedia;
import com.stario.launcher.activities.settings.dialogs.gestures.GestureAppPickerDialog;
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
import com.stario.launcher.ui.common.pager.CustomDurationViewPager;
import com.stario.launcher.ui.common.tabs.CenterTabLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * A small "mini-dashboard" occupying TOP_SHEET, the one sheet slot nothing
 * else claims by default (see SheetType.getDefaultSheetTypeForSheetDialogFragment).
 * Reached only through the dedicated two-finger swipe down (see
 * Launcher.attachGestures()) - the one-finger swipe down that reveals the
 * system notification shade is untouched by any of this.
 * <p>
 * Two tabs, swiped between manually (CenterTabLayout + a plain View-backed
 * PagerAdapter - see DashboardPagerAdapter):
 * <ul>
 *     <li>Inicio: a DND toggle, "Favoritos" (GlanceQuickApps' curated
 *     list) and "Recientes" (RecentApps' launch history) - all sourced
 *     from lists that already exist elsewhere.</li>
 *     <li>Multimedia: a Now Playing card driven by the same
 *     MediaSessionManager/NotificationService approach the Glance Media
 *     chip already uses, an "Escuchado recientemente" listening history
 *     (RecentMedia, fed from Media's own live session tracking) - the
 *     actual dashboard summary this tab was missing - plus a small
 *     user-curated row of media app shortcuts (DashboardMediaApps) -
 *     editable directly here, since unlike Favoritos there's nowhere
 *     else for it to be edited.</li>
 * </ul>
 */
public class DashboardDialog extends SheetDialogFragment {
    private SharedPreferences quickAppsPreferences;
    private SharedPreferences recentAppsPreferences;
    private SharedPreferences mediaAppsPreferences;
    private SharedPreferences recentMediaPreferences;
    private DashboardAppAdapter favoritesAdapter;
    private DashboardAppAdapter recentAdapter;
    private DashboardAppAdapter mediaAppsAdapter;
    private RecentMediaAdapter mediaHistoryAdapter;
    private MediaSessionManager mediaSessionManager;
    private MediaController nowPlayingSession;
    private ThemedActivity activity;
    private ViewGroup placeholder;
    private View favoritesSection;
    private View recentSection;
    private View nowPlayingCard;
    private View historySection;
    private ImageView nowPlayingCover;
    private ImageView nowPlayingPlayPause;
    private TextView nowPlayingTitle;
    private TextView nowPlayingArtist;
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
        this.mediaAppsPreferences = activity.getApplicationContext()
                .getSharedPreferences(Entry.DASHBOARD_MEDIA_APPS);
        this.recentMediaPreferences = activity.getApplicationContext()
                .getSharedPreferences(Entry.RECENT_MEDIA);
        this.mediaSessionManager = (MediaSessionManager)
                activity.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.widget_dashboard, container, false);

        FadingEdgeLayout fader = root.findViewById(R.id.fader);
        LinearLayout content = root.findViewById(R.id.content);
        CenterTabLayout tabs = root.findViewById(R.id.tabs);
        CustomDurationViewPager pager = root.findViewById(R.id.pager);

        View pageHome = inflater.inflate(R.layout.dashboard_page_home, pager, false);
        View pageMedia = inflater.inflate(R.layout.dashboard_page_media, pager, false);

        setupHomePage(pageHome);
        setupMediaPage(pageMedia);

        List<View> pages = new ArrayList<>();
        pages.add(pageHome);
        pages.add(pageMedia);

        List<CharSequence> titles = new ArrayList<>();
        titles.add(activity.getResources().getString(R.string.dashboard_home));
        titles.add(activity.getResources().getString(R.string.dashboard_media));

        pager.setAdapter(new DashboardPagerAdapter(pages, titles));
        tabs.setViewPager(pager);

        Measurements.addStatusBarListener(value -> {
            fader.setFadeSizes(value, 0, Measurements.getNavHeight(), 0);

            content.setPadding(content.getPaddingLeft(), value,
                    content.getPaddingRight(), content.getPaddingBottom());
        });
        Measurements.addNavListener(value -> {
            fader.setFadeSizes(Measurements.getSysUIHeight(), 0, value, 0);

            pager.setPadding(pager.getPaddingLeft(), pager.getPaddingTop(),
                    pager.getPaddingRight(), value);
        });

        setOnBackPressed(() -> {
            hide(true);

            return true;
        });

        return root;
    }

    private void setupHomePage(View page) {
        placeholder = page.findViewById(R.id.placeholder);
        favoritesSection = page.findViewById(R.id.favorites_section);
        recentSection = page.findViewById(R.id.recent_section);

        // Reuses the same Glance chip rather than inventing a second status
        // display - a quick DND toggle right where the user is already
        // looking for "what's going on right now".
        LinearLayout statusRow = page.findViewById(R.id.status_row);

        focus = new Focus();
        View focusView = focus.inflate(activity, statusRow);
        focusView.setOnClickListener(focus.getClickListener());
        statusRow.addView(focusView);

        RecyclerView recyclerFavorites = page.findViewById(R.id.recycler_favorites);
        RecyclerView recyclerRecent = page.findViewById(R.id.recycler_recent);

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
    }

    private void setupMediaPage(View page) {
        nowPlayingCard = page.findViewById(R.id.now_playing_card);
        nowPlayingCover = page.findViewById(R.id.now_playing_cover);
        nowPlayingTitle = page.findViewById(R.id.now_playing_title);
        nowPlayingArtist = page.findViewById(R.id.now_playing_artist);
        nowPlayingPlayPause = page.findViewById(R.id.now_playing_play_pause);
        ImageView nowPlayingPrevious = page.findViewById(R.id.now_playing_previous);
        ImageView nowPlayingNext = page.findViewById(R.id.now_playing_next);

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

        historySection = page.findViewById(R.id.history_section);
        RecyclerView recyclerMediaHistory = page.findViewById(R.id.recycler_media_history);

        recyclerMediaHistory.setLayoutManager(new LinearLayoutManager(activity,
                LinearLayoutManager.VERTICAL, false));

        mediaHistoryAdapter = new RecentMediaAdapter(activity, new ArrayList<>());
        recyclerMediaHistory.setAdapter(mediaHistoryAdapter);

        RecyclerView recyclerMediaApps = page.findViewById(R.id.recycler_media_apps);
        recyclerMediaApps.setLayoutManager(new LinearLayoutManager(activity,
                LinearLayoutManager.HORIZONTAL, false));

        mediaAppsAdapter = new DashboardAppAdapter(activity, new ArrayList<>(),
                new DashboardAppAdapter.Listener() {
                    @Override
                    public void onAppClick(String packageName) {
                        LauncherApplication application =
                                ProfileManager.getInstance().getApplication(packageName);

                        if (application != null) {
                            application.launch(activity);
                        }

                        hide(true);
                    }

                    @Override
                    public void onAppLongClick(String packageName) {
                        DashboardMediaApps.remove(mediaAppsPreferences, packageName);

                        refreshMediaApps();
                    }

                    @Override
                    public void onAddClick() {
                        new GestureAppPickerDialog(activity, packageName -> {
                            if (packageName != null) {
                                DashboardMediaApps.add(mediaAppsPreferences, packageName);

                                refreshMediaApps();
                            }
                        }).show();
                    }
                }, true);

        recyclerMediaApps.setAdapter(mediaAppsAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();

        refresh();
    }

    /**
     * Every source list can change while this sheet is closed (favorites
     * through the Glance long-press popup, recents through simply using
     * the launcher, playback through whatever media app is running), so
     * pull a fresh snapshot every time the sheet comes back on screen
     * rather than only once in onCreateView(). Both pages are already
     * inflated (see DashboardPagerAdapter), so this refreshes both
     * regardless of which tab happens to be showing.
     */
    private void refresh() {
        if (focus != null) {
            focus.update();
        }

        updateNowPlaying();
        refreshMediaApps();
        refreshMediaHistory();

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

    private void refreshMediaApps() {
        if (mediaAppsAdapter == null) {
            return;
        }

        mediaAppsAdapter.setPackages(filterInstalled(DashboardMediaApps.getPackages(mediaAppsPreferences)));
    }

    /**
     * "Escuchado recientemente" - the actual listening-history summary
     * the Multimedia tab was missing (see RecentMedia, fed from Media's
     * own live session tracking). Entries whose app was since uninstalled
     * are dropped the same way Favoritos/Recientes already are.
     */
    private void refreshMediaHistory() {
        if (mediaHistoryAdapter == null || historySection == null) {
            return;
        }

        List<RecentMedia.Track> tracks = new ArrayList<>();

        for (RecentMedia.Track track : RecentMedia.getTracks(recentMediaPreferences)) {
            if (ProfileManager.getInstance().getApplication(track.packageName) != null) {
                tracks.add(track);
            }
        }

        mediaHistoryAdapter.setTracks(tracks);
        historySection.setVisibility(tracks.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * The same MediaSessionManager/NotificationService approach the Glance
     * Media chip already uses (and the same "Do Not Disturb access"-style
     * separate permission: notification listener access), just without
     * that chip's popup/transition machinery, since this only needs to
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
