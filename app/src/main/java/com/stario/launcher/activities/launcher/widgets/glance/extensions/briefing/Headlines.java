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

package com.stario.launcher.activities.launcher.widgets.glance.extensions.briefing;

import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.prof18.rssparser.model.RssChannel;
import com.prof18.rssparser.model.RssItem;
import com.stario.launcher.R;
import com.stario.launcher.activities.launcher.Launcher;
import com.stario.launcher.activities.launcher.widgets.glance.GlanceViewExtension;
import com.stario.launcher.preferences.Vibrations;
import com.stario.launcher.sheet.SheetType;
import com.stario.launcher.sheet.briefing.dialog.BriefingDialog;
import com.stario.launcher.sheet.briefing.dialog.page.feed.BriefingFeedList;
import com.stario.launcher.sheet.briefing.dialog.page.feed.Feed;
import com.stario.launcher.sheet.briefing.rss.RSSHelper;
import com.stario.launcher.themes.ThemedActivity;

/**
 * A compact Glance chip bridging into the Briefing (RSS) sheet: shows the
 * latest headline from the first configured feed and, on tap, opens the
 * full Briefing sheet - the same "preview chip -> tap to open the real
 * thing" shape as {@link com.stario.launcher.activities.launcher.widgets.glance.extensions.calendar.Calendar}.
 * <p>
 * Hidden entirely while no feed is configured (nothing to preview), same
 * spirit as GlanceQuickApps' empty state.
 */
public final class Headlines implements GlanceViewExtension {
    // Matches the RSS feeds' own typical publish cadence closely enough
    // without re-fetching on every single Glance update() tick (called
    // roughly once a minute, same as the clock).
    private static final long REFRESH_INTERVAL_MS = 15 * 60 * 1000;

    private ThemedActivity activity;
    private View root;
    private TextView headlineView;
    private View.OnClickListener clickListener;

    private volatile RssItem cachedItem;
    private volatile boolean fetching;
    private long lastFetch;

    @Override
    public View inflate(ThemedActivity activity, LinearLayout container) {
        this.activity = activity;

        root = activity.getLayoutInflater()
                .inflate(R.layout.glance_headlines, container, false);

        headlineView = root.findViewById(R.id.headline);

        clickListener = v -> {
            Vibrations.getInstance().vibrate();

            if (activity instanceof Launcher) {
                Launcher launcher = (Launcher) activity;
                SheetType type = SheetType.getSheetTypeForSheetDialogFragment(activity, BriefingDialog.class);

                launcher.getSheetsController().showSheet(type);
            }
        };

        return root;
    }

    @Override
    public void update() {
        if (activity == null || root == null) {
            return;
        }

        BriefingFeedList feeds = BriefingFeedList.from(activity);

        if (feeds.size() == 0) {
            root.setVisibility(View.GONE);

            return;
        }

        root.setVisibility(View.VISIBLE);

        RssItem item = cachedItem;
        headlineView.setText(item != null ?
                item.getTitle() : activity.getString(R.string.headlines_loading));

        long now = System.currentTimeMillis();

        if (!fetching && now - lastFetch > REFRESH_INTERVAL_MS) {
            fetching = true;
            lastFetch = now;

            Feed feed = feeds.get(0);

            RSSHelper.futureParse(feed.getRSSLink()).whenComplete((channel, throwable) -> {
                fetching = false;

                if (throwable != null) {
                    Log.w("Headlines", "update: failed to refresh " + feed.getRSSLink(), throwable);

                    return;
                }

                RssItem latest = firstItem(channel);

                if (latest != null) {
                    cachedItem = latest;

                    if (headlineView != null) {
                        headlineView.post(() -> headlineView.setText(latest.getTitle()));
                    }
                }
            });
        }
    }

    private RssItem firstItem(RssChannel channel) {
        if (channel == null || channel.getItems() == null || channel.getItems().isEmpty()) {
            return null;
        }

        return channel.getItems().get(0);
    }

    @Override
    public View.OnClickListener getClickListener() {
        return clickListener;
    }
}
