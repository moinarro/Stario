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

package com.stario.launcher.activities.launcher.widgets.glance.extensions.focus;

import android.app.NotificationManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.stario.launcher.R;
import com.stario.launcher.activities.launcher.widgets.glance.GlanceViewExtension;
import com.stario.launcher.preferences.Vibrations;
import com.stario.launcher.themes.ThemedActivity;

/**
 * A one-tap Do Not Disturb toggle living in Glance, next to Calendar/
 * Battery/Headlines. Distinct from those: this one *acts* on tap rather
 * than just previewing something, the same "tap performs the thing"
 * shape as GlanceQuickApps' items launching an app.
 * <p>
 * Toggling DND requires the user to have granted "Do Not Disturb access"
 * (ACCESS_NOTIFICATION_POLICY is a normal permission, but actually using
 * it needs this separate, deliberate grant, same as notification listener
 * access elsewhere in Settings) - a tap while it's missing opens
 * {@link FocusAccessDialog} instead of toggling anything.
 */
public final class Focus implements GlanceViewExtension {
    private ThemedActivity activity;
    private ImageView icon;
    private View.OnClickListener clickListener;

    @Override
    public View inflate(ThemedActivity activity, LinearLayout container) {
        this.activity = activity;

        View root = activity.getLayoutInflater()
                .inflate(R.layout.glance_focus, container, false);

        icon = root.findViewById(R.id.icon);

        clickListener = v -> {
            Vibrations.getInstance().vibrate();

            NotificationManager manager = activity.getSystemService(NotificationManager.class);

            if (manager == null) {
                return;
            }

            if (!manager.isNotificationPolicyAccessGranted()) {
                new FocusAccessDialog(activity).show();

                return;
            }

            boolean isOn = manager.getCurrentInterruptionFilter() !=
                    NotificationManager.INTERRUPTION_FILTER_ALL;

            manager.setInterruptionFilter(isOn ?
                    NotificationManager.INTERRUPTION_FILTER_ALL :
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY);

            updateIcon(manager);
        };

        return root;
    }

    @Override
    public void update() {
        if (activity == null || icon == null) {
            return;
        }

        NotificationManager manager = activity.getSystemService(NotificationManager.class);

        if (manager != null) {
            updateIcon(manager);
        }
    }

    private void updateIcon(NotificationManager manager) {
        boolean isOn = manager.isNotificationPolicyAccessGranted() &&
                manager.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;

        icon.setImageResource(isOn ? R.drawable.ic_focus_on : R.drawable.ic_focus_off);
        icon.setAlpha(isOn ? 1f : 0.7f);
    }

    @Override
    public View.OnClickListener getClickListener() {
        return clickListener;
    }
}
