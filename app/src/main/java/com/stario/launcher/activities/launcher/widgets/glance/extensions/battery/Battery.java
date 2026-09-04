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

package com.stario.launcher.activities.launcher.widgets.glance.extensions.battery;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.BatteryManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.stario.launcher.R;
import com.stario.launcher.activities.launcher.widgets.glance.GlanceViewExtension;
import com.stario.launcher.preferences.Vibrations;
import com.stario.launcher.themes.ThemedActivity;

/**
 * A compact Glance chip showing the current battery percentage (and, while
 * charging, a bolt-accented icon), read from BatteryManager - no
 * broadcast receiver needed, since {@link #update()} is already called on
 * every onResume() the same as the other chips. Tap opens the system
 * battery usage screen.
 */
public final class Battery implements GlanceViewExtension {
    private ThemedActivity activity;
    private ImageView icon;
    private TextView percentage;
    private View.OnClickListener clickListener;

    @Override
    public View inflate(ThemedActivity activity, LinearLayout container) {
        this.activity = activity;

        View root = activity.getLayoutInflater()
                .inflate(R.layout.glance_battery, container, false);

        icon = root.findViewById(R.id.icon);
        percentage = root.findViewById(R.id.percentage);

        clickListener = v -> {
            Vibrations.getInstance().vibrate();

            try {
                activity.startActivity(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY));
            } catch (ActivityNotFoundException exception) {
                try {
                    activity.startActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS));
                } catch (ActivityNotFoundException fallbackException) {
                    Log.w("Battery", "inflate: no battery settings screen found");
                }
            }
        };

        return root;
    }

    @Override
    public void update() {
        if (activity == null || percentage == null) {
            return;
        }

        BatteryManager manager = activity.getSystemService(BatteryManager.class);

        if (manager == null) {
            return;
        }

        int level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        boolean charging = manager.isCharging();

        percentage.setText(activity.getString(R.string.battery_percentage, level));
        icon.setImageResource(charging ? R.drawable.ic_battery_charging : R.drawable.ic_battery);
    }

    @Override
    public View.OnClickListener getClickListener() {
        return clickListener;
    }
}
