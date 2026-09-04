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

package com.stario.launcher.activities.settings.dialogs.gestures;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.stario.launcher.R;
import com.stario.launcher.apps.LauncherApplication;
import com.stario.launcher.apps.ProfileManager;
import com.stario.launcher.gestures.Gestures;
import com.stario.launcher.gestures.TwoFingerSwipeGestureDetector.Direction;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.dialogs.ActionDialog;

import java.util.EnumMap;
import java.util.Map;

public class GesturesDialog extends ActionDialog {
    private final Gestures gestures;
    private final Map<Direction, TextView> subtitles;

    public GesturesDialog(@NonNull ThemedActivity activity) {
        super(activity);

        this.gestures = new Gestures(activity);
        this.subtitles = new EnumMap<>(Direction.class);
    }

    @NonNull
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected View inflateContent(LayoutInflater inflater) {
        View root = inflater.inflate(R.layout.pop_up_gestures, null);

        setupRow(root, Direction.UP, R.id.gesture_up_container, R.id.gesture_up_app);
        // DOWN is intentionally absent here: it's hardcoded to always open the
        // Dashboard (see Launcher.attachGestures()), not user-assignable.
        setupRow(root, Direction.LEFT, R.id.gesture_left_container, R.id.gesture_left_app);
        setupRow(root, Direction.RIGHT, R.id.gesture_right_container, R.id.gesture_right_app);

        return root;
    }

    private void setupRow(View root, Direction direction, int containerId, int subtitleId) {
        TextView subtitle = root.findViewById(subtitleId);
        subtitles.put(direction, subtitle);
        updateSubtitle(direction);

        root.findViewById(containerId).setOnClickListener(view -> {
            GestureAppPickerDialog dialog = new GestureAppPickerDialog(activity, packageName -> {
                if (packageName == null) {
                    gestures.clear(direction);
                } else {
                    gestures.assign(direction, packageName);
                }

                updateSubtitle(direction);
            });

            dialog.show();
        });
    }

    private void updateSubtitle(Direction direction) {
        TextView subtitle = subtitles.get(direction);
        if (subtitle == null) {
            return;
        }

        String packageName = gestures.getAssignedPackage(direction);
        LauncherApplication application = packageName != null ?
                ProfileManager.getInstance().getApplication(packageName) : null;

        if (application != null) {
            subtitle.setText(application.getLabel());
        } else {
            subtitle.setText(R.string.gesture_not_assigned);
        }
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
