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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.PopupWindow;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.reflect.TypeToken;
import com.stario.launcher.R;
import com.stario.launcher.activities.settings.dialogs.gestures.GestureAppPickerDialog;
import com.stario.launcher.apps.LauncherApplication;
import com.stario.launcher.apps.ProfileManager;
import com.stario.launcher.preferences.Entry;
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
 * card (the day/weather widget). Shown as a 2-row, horizontally paged
 * grid that grows out of the widget in place, rather than as a separate
 * dialog.
 */
public class GlanceQuickApps {
    private static final String TAG = "GlanceQuickApps";
    private static final String APPS_KEY = "com.stario.GLANCE_QUICK_APPS_LIST";

    private final ThemedActivity activity;
    private final SharedPreferences preferences;
    private final List<String> packages;

    private PopupWindow popupWindow;
    private GlanceQuickAppsAdapter adapter;

    public GlanceQuickApps(ThemedActivity activity) {
        this.activity = activity;
        this.preferences = activity.getApplicationContext().getSharedPreferences(Entry.GLANCE_QUICK_APPS);
        this.packages = load();
    }

    private List<String> load() {
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
            Log.e(TAG, "load: failed to parse stored quick apps", exception);

            return new ArrayList<>();
        }
    }

    private void save() {
        preferences.edit()
                .putString(APPS_KEY, Utils.getGsonInstance().toJson(packages))
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
        LayoutInflater inflater = activity.getLayoutInflater();
        View content = inflater.inflate(R.layout.glance_quick_apps_popup, null);

        RecyclerView recycler = content.findViewById(R.id.recycler);

        GridLayoutManager manager = new GridLayoutManager(activity, 2,
                GridLayoutManager.HORIZONTAL, false);
        recycler.setLayoutManager(manager);
        recycler.setItemAnimator(null);

        new PagerSnapHelper().attachToRecyclerView(recycler);

        adapter = new GlanceQuickAppsAdapter(activity, packages, new GlanceQuickAppsAdapter.Listener() {
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

                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onAddClick() {
                GestureAppPickerDialog dialog = new GestureAppPickerDialog(activity, packageName -> {
                    if (packageName != null && !packages.contains(packageName)) {
                        packages.add(packageName);
                        save();

                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }
                    }
                });

                dialog.show();
            }
        });

        recycler.setAdapter(adapter);

        int width = anchor.getWidth() > 0 ? anchor.getWidth() : ViewGroup.LayoutParams.MATCH_PARENT;

        popupWindow = new PopupWindow(content, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);
        popupWindow.setElevation(Measurements.dpToPx(8));

        content.setAlpha(0f);
        content.setScaleY(0.85f);
        content.setPivotY(0f);

        popupWindow.showAsDropDown(anchor, 0, Measurements.dpToPx(8));

        content.animate()
                .alpha(1f)
                .scaleY(1f)
                .setInterpolator(new DecelerateInterpolator())
                .setDuration(Animation.SHORT.getDuration())
                .start();
    }

    private void dismiss() {
        if (popupWindow != null) {
            popupWindow.dismiss();
        }
    }
}
