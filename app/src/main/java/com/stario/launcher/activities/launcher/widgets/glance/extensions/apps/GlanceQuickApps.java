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

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.reflect.TypeToken;
import com.stario.launcher.R;
import com.stario.launcher.activities.settings.dialogs.gestures.GestureAppPickerDialog;
import com.stario.launcher.apps.LauncherApplication;
import com.stario.launcher.apps.ProfileManager;
import com.stario.launcher.preferences.Entry;
import com.stario.launcher.preferences.Vibrations;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.Measurements;
import com.stario.launcher.ui.utils.animation.Animation;
import com.stario.launcher.utils.Utils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * A free-form, individually curated list of apps (independent from the
 * category system) that the user reaches by long-pressing the Glance
 * card (the day/weather widget). Shown as a paginated or continuously
 * scrolling grid that grows out of the widget in place, rather than as
 * a separate dialog. Icon size, row count and scrolling behaviour are
 * all user-configurable (see {@link IconSize} / {@link ScrollMode} and
 * the getters/setters below), surfaced through a Settings dialog.
 */
public class GlanceQuickApps {
    private static final String TAG = "GlanceQuickApps";
    private static final String APPS_KEY = "com.stario.GLANCE_QUICK_APPS_LIST";
    private static final String ICON_SIZE_KEY = "com.stario.GLANCE_QUICK_APPS_ICON_SIZE";
    private static final String ROWS_KEY = "com.stario.GLANCE_QUICK_APPS_ROWS";
    private static final String SCROLL_MODE_KEY = "com.stario.GLANCE_QUICK_APPS_SCROLL_MODE";

    private static final int MIN_ROWS = 1;
    private static final int MAX_ROWS = 4;
    private static final int DEFAULT_ROWS = 2;

    // px/frame the continuous scroll mode advances the list by. Roughly
    // 2dp-equivalent at 60fps, i.e. a slow, readable marquee.
    private static final float AUTO_SCROLL_SPEED_DP_PER_FRAME = 1.4f;
    private static final long AUTO_SCROLL_RESUME_DELAY = 700L;

    public enum IconSize {
        SMALL(52),
        MEDIUM(72),
        LARGE(92);

        public final int dp;

        IconSize(int dp) {
            this.dp = dp;
        }
    }

    public enum ScrollMode {
        PAGINATION,
        CONTINUOUS
    }

    private final ThemedActivity activity;
    private final SharedPreferences preferences;
    private final List<String> packages;

    private PopupWindow popupWindow;
    private GlanceQuickAppsAdapter adapter;
    private RecyclerView recycler;
    private Runnable autoScrollRunnable;
    private Runnable autoScrollResumeRunnable;

    public GlanceQuickApps(ThemedActivity activity) {
        this.activity = activity;
        this.preferences = activity.getApplicationContext().getSharedPreferences(Entry.GLANCE_QUICK_APPS);
        this.packages = load();
    }

    private List<String> load() {
        return getPackages(preferences);
    }

    /**
     * The curated app list itself, read the same static way as
     * getIconSize()/getRows()/getScrollMode() below - lets other features
     * (the Dashboard's "favorites" section) read this same
     * individually-curated list without needing a live GlanceQuickApps
     * instance (which owns a whole popup lifecycle this has nothing to do
     * with).
     */
    public static List<String> getPackages(SharedPreferences preferences) {
        String json = preferences.getString(APPS_KEY, null);

        if (json == null) {
            return new ArrayList<>();
        }

        try {
            Type type = new TypeToken<ArrayList<String>>() {
            }.getType();

            List<String> stored = Utils.getGsonInstance().fromJson(json, type);

            return stored != null ? new ArrayList<>(stored) : new ArrayList<>();
        } catch (Exception exception) {
            Log.e(TAG, "getPackages: failed to parse stored quick apps", exception);

            return new ArrayList<>();
        }
    }

    private void save() {
        preferences.edit()
                .putString(APPS_KEY, Utils.getGsonInstance().toJson(packages))
                .apply();
    }

    // -------------------------------------------------------------------
    // Settings, backed by the same SharedPreferences the app list itself
    // uses. Static so the Settings dialog can read/write them without
    // needing a live GlanceQuickApps instance, exactly like the rest of
    // Stario's per-widget preferences (see PinnedCategorySchedule).
    // -------------------------------------------------------------------

    public static IconSize getIconSize(SharedPreferences preferences) {
        int ordinal = preferences.getInt(ICON_SIZE_KEY, IconSize.MEDIUM.ordinal());
        IconSize[] values = IconSize.values();

        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IconSize.MEDIUM;
    }

    public static void setIconSize(SharedPreferences preferences, IconSize size) {
        preferences.edit()
                .putInt(ICON_SIZE_KEY, size.ordinal())
                .apply();
    }

    public static int getRows(SharedPreferences preferences) {
        int rows = preferences.getInt(ROWS_KEY, DEFAULT_ROWS);

        return Math.max(MIN_ROWS, Math.min(MAX_ROWS, rows));
    }

    public static void setRows(SharedPreferences preferences, int rows) {
        preferences.edit()
                .putInt(ROWS_KEY, Math.max(MIN_ROWS, Math.min(MAX_ROWS, rows)))
                .apply();
    }

    public static ScrollMode getScrollMode(SharedPreferences preferences) {
        int ordinal = preferences.getInt(SCROLL_MODE_KEY, ScrollMode.PAGINATION.ordinal());
        ScrollMode[] values = ScrollMode.values();

        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ScrollMode.PAGINATION;
    }

    public static void setScrollMode(SharedPreferences preferences, ScrollMode mode) {
        preferences.edit()
                .putInt(SCROLL_MODE_KEY, mode.ordinal())
                .apply();
    }

    /**
     * Toggles the in-place quick-apps popup anchored to the Glance card.
     */
    public void toggle(View anchor) {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();

            return;
        }

        show(anchor);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void show(View anchor) {
        IconSize iconSize = getIconSize(preferences);
        int rows = getRows(preferences);
        ScrollMode scrollMode = getScrollMode(preferences);

        LayoutInflater inflater = activity.getLayoutInflater();
        View content = inflater.inflate(R.layout.glance_quick_apps_popup, null);

        TextView empty = content.findViewById(R.id.empty);
        ImageView add = content.findViewById(R.id.add);
        recycler = content.findViewById(R.id.recycler);

        add.setOnClickListener(v -> {
            Vibrations.getInstance().vibrate();

            openAppPicker();
        });

        int iconSizePx = Measurements.dpToPx(iconSize.dp);
        // GridLayoutManager doesn't add spacing between rows on its own (no
        // ItemDecoration is attached), so the RecyclerView's content height is
        // exactly rows * iconSizePx - matching that exactly avoids any extra
        // blank space below the last row.
        ViewGroup.LayoutParams recyclerParams = recycler.getLayoutParams();
        recyclerParams.height = iconSizePx * rows;
        recycler.setLayoutParams(recyclerParams);

        int width = anchor.getWidth() > 0 ? anchor.getWidth() : ViewGroup.LayoutParams.MATCH_PARENT;

        // The popup (and so the grid) is always as wide as the Glance card,
        // which is normally most of the screen. Looping is only meaningful
        // - and only what "infinite scroll" should mean - once the real
        // apps don't already fill that width on their own; otherwise
        // wrapping just repeats the same few icons over and over until they
        // do, which reads as a bug rather than a feature.
        int columnsNeeded = (int) Math.ceil(packages.size() / (double) rows);
        boolean infinite = !packages.isEmpty() && width > 0
                && columnsNeeded * iconSizePx > width;

        GridLayoutManager manager = new GridLayoutManager(activity, rows,
                GridLayoutManager.HORIZONTAL, false);
        recycler.setLayoutManager(manager);
        recycler.setItemAnimator(null);

        // PagerSnapHelper's snap-distance calculation only accounts for a
        // single span; with a multi-row GridLayoutManager (rows > 1) it
        // fights the huge scrollToPosition() jump performed below, causing
        // a corrective-scroll oscillation that reads as the icons
        // flickering until the user's touch interrupts it. Snapping is
        // only safe with a single row.
        if (scrollMode == ScrollMode.PAGINATION && infinite && rows == 1) {
            new PagerSnapHelper().attachToRecyclerView(recycler);
        }

        adapter = new GlanceQuickAppsAdapter(activity, packages, iconSizePx, infinite,
                new GlanceQuickAppsAdapter.Listener() {
            @Override
            public void onAppClick(String packageName) {
                LauncherApplication application = ProfileManager.getInstance().getApplication(packageName);

                if (application != null) {
                    application.launch(activity);
                }

                dismiss();
            }

            @Override
            public void onAppLongClick(String packageName) {
                packages.remove(packageName);
                save();

                updateEmptyState(empty);

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        });

        recycler.setAdapter(adapter);
        updateEmptyState(empty);

        // Kept invisible until the layout below has actually settled at its
        // target scroll position (see the post() a few lines down) - masks
        // the brief window, right after attaching the adapter and jumping
        // to that position, where RecyclerView/GridLayoutManager is still
        // reconciling intermediate view states and would otherwise be
        // visible mid-shuffle for a frame or two, reading as a flicker.
        if (infinite) {
            recycler.setVisibility(View.INVISIBLE);
        }

        if (infinite) {
            // Wraps around indefinitely (the adapter reports Integer.MAX_VALUE
            // items and maps position -> real index via modulo), so start
            // some way in, aligned to both a row boundary and a full loop of
            // the real list, to allow scrolling freely in either direction
            // without ever hitting an edge.
            //
            // Deliberately NOT a huge jump, despite the adapter itself
            // reporting Integer.MAX_VALUE items: scrollToPosition() to a far
            // starting index makes RecyclerView/GridLayoutManager measure and
            // bind through a chain of intermediate view states while it
            // settles into that position (worse with a multi-row grid, since
            // there's more to lay out per pass), which is visible as the
            // real icons flashing past in rapid succession right after the
            // popup opens - reading as flicker even though nothing is
            // actually broken. A handful of loops of the real list is
            // already far more headroom than a user swiping a small popup
            // will ever reach in either direction, so keep the jump tiny.
            long block = (long) rows * packages.size();
            int startPosition = (int) Math.min(block * 4, Integer.MAX_VALUE);

            manager.scrollToPosition(startPosition);

            recycler.post(() -> recycler.setVisibility(View.VISIBLE));

            if (scrollMode == ScrollMode.CONTINUOUS) {
                attachAutoScroll(recycler);
            }
        }

        popupWindow = new PopupWindow(content, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.setElevation(Measurements.dpToPx(8));
        popupWindow.setOnDismissListener(this::stopAutoScroll);

        content.setAlpha(0f);
        content.setScaleY(0.85f);
        content.setPivotY(0f);

        // showAsDropDown() leaves it to the platform to decide whether the popup
        // fits below the anchor, and on several OEM builds that logic pushes an
        // oversized/undersized-measured WRAP_CONTENT popup all the way to the
        // bottom of the screen instead of flipping it above the anchor, making it
        // land off-screen. Position it ourselves against the anchor's actual
        // on-screen location, measured up-front, and flip above the anchor when
        // there isn't enough room below.
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        content.measure(widthSpec, heightSpec);
        int popupHeight = content.getMeasuredHeight();

        int[] anchorLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);

        int margin = Measurements.dpToPx(8);
        int screenHeight = Measurements.getHeight();

        int spaceBelow = screenHeight - (anchorLocation[1] + anchor.getHeight());

        int x = anchorLocation[0];
        int y;

        if (spaceBelow >= popupHeight + margin) {
            y = anchorLocation[1] + anchor.getHeight() + margin;
            content.setPivotY(0f);
        } else {
            y = Math.max(0, anchorLocation[1] - popupHeight - margin);
            content.setPivotY(popupHeight);
        }

        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);

        content.animate()
                .alpha(1f)
                .scaleY(1f)
                .setInterpolator(new DecelerateInterpolator())
                .setDuration(Animation.SHORT.getDuration())
                .start();
    }

    private void openAppPicker() {
        GestureAppPickerDialog dialog = new GestureAppPickerDialog(activity, packageName -> {
            if (packageName != null && !packages.contains(packageName)) {
                packages.add(packageName);
                save();

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }

                if (popupWindow != null && popupWindow.getContentView() != null) {
                    updateEmptyState(popupWindow.getContentView().findViewById(R.id.empty));
                }
            }
        });

        dialog.show();
    }

    private void updateEmptyState(TextView empty) {
        if (empty == null) {
            return;
        }

        boolean isEmpty = packages.isEmpty();

        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);

        if (recycler != null) {
            recycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Drives the "continuous" scroll mode: a slow, uninterrupted marquee
     * that pauses while the user is touching the list and resumes shortly
     * after they let go, rather than fighting their drag.
     */
    @SuppressLint("ClickableViewAccessibility")
    private void attachAutoScroll(RecyclerView recycler) {
        float speedPx = AUTO_SCROLL_SPEED_DP_PER_FRAME * Measurements.getDensity();

        autoScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (GlanceQuickApps.this.recycler == null) {
                    return;
                }

                GlanceQuickApps.this.recycler.scrollBy((int) Math.max(1, speedPx), 0);
                GlanceQuickApps.this.recycler.postOnAnimation(this);
            }
        };

        autoScrollResumeRunnable = () -> {
            if (this.recycler != null && autoScrollRunnable != null) {
                this.recycler.postOnAnimation(autoScrollRunnable);
            }
        };

        recycler.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView view, @NonNull MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        pauseAutoScroll();
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        resumeAutoScrollDelayed();
                        break;
                }

                return false;
            }
        });

        recycler.postOnAnimation(autoScrollRunnable);
    }

    private void pauseAutoScroll() {
        if (recycler != null) {
            if (autoScrollRunnable != null) {
                recycler.removeCallbacks(autoScrollRunnable);
            }

            if (autoScrollResumeRunnable != null) {
                recycler.removeCallbacks(autoScrollResumeRunnable);
            }
        }
    }

    private void resumeAutoScrollDelayed() {
        if (recycler != null && autoScrollResumeRunnable != null) {
            recycler.postDelayed(autoScrollResumeRunnable, AUTO_SCROLL_RESUME_DELAY);
        }
    }

    private void stopAutoScroll() {
        pauseAutoScroll();

        recycler = null;
    }

    private void dismiss() {
        if (popupWindow != null) {
            popupWindow.dismiss();
        }
    }
}
