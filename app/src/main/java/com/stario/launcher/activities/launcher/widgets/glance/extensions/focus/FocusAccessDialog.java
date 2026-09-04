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

import android.content.Intent;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.stario.launcher.R;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.dialogs.ActionDialog;

/**
 * Explains why the Focus chip needs "Do Not Disturb access" before sending
 * the user to the system screen that grants it - same shape as
 * NotificationConfigurator, for the same reason: Android requires this be
 * a deliberate, explained navigation rather than a silent redirect.
 */
public class FocusAccessDialog extends ActionDialog {
    public FocusAccessDialog(@NonNull ThemedActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    protected View inflateContent(LayoutInflater inflater) {
        View root = inflater.inflate(R.layout.pop_up_focus_access, null);

        root.findViewById(R.id.proceed)
                .setOnClickListener(v -> {
                    setOnDismissListener(null);

                    activity.startActivity(new Intent(
                            Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                });
        root.findViewById(R.id.cancel)
                .setOnClickListener(v -> dismiss());

        return root;
    }

    @Override
    protected boolean blurBehind() {
        return true;
    }

    @Override
    protected int getDesiredInitialState() {
        return BottomSheetBehavior.STATE_EXPANDED;
    }
}
