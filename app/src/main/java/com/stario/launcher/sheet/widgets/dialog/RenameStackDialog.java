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

package com.stario.launcher.sheet.widgets.dialog;

import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.stario.launcher.R;
import com.stario.launcher.themes.ThemedActivity;
import com.stario.launcher.ui.dialogs.ActionDialog;
import com.stario.launcher.ui.keyboard.extract.ExtractEditText;

/**
 * Small bottom sheet for naming a widget stack, offered from its options
 * menu (see WidgetsDialog#showStackOptionsMenu). Mirrors
 * RenameCategoryDialog's pattern - an ExtractEditText prefilled with the
 * current value, committed on dismiss - minus the uniqueness check, since
 * stack names are just a label with no lookup key attached to them.
 */
public class RenameStackDialog extends ActionDialog {
    private final String initialName;
    private final Listener listener;

    private ExtractEditText editText;

    public RenameStackDialog(@NonNull ThemedActivity activity,
                             @Nullable String initialName, @NonNull Listener listener) {
        super(activity);

        this.initialName = initialName;
        this.listener = listener;

        setOnDismissListener(dialog -> {
            Editable text = editText.getText();
            String value = text != null ? text.toString().trim() : "";

            listener.onNameCommitted(TextUtils.isEmpty(value) ? null : value);
        });
    }

    @NonNull
    @Override
    protected View inflateContent(LayoutInflater inflater) {
        View root = inflater.inflate(R.layout.pop_up_widget_stack_name, null);

        editText = root.findViewById(R.id.name);
        editText.setText(initialName);

        if (initialName != null) {
            editText.setSelection(initialName.length());
        }

        root.findViewById(R.id.reset).setOnClickListener(v -> editText.setText(null));

        return root;
    }

    @Override
    protected int getDesiredInitialState() {
        return BottomSheetBehavior.STATE_EXPANDED;
    }

    @Override
    protected boolean blurBehind() {
        return true;
    }

    public interface Listener {
        /**
         * @param name the trimmed name, or null when cleared/left blank
         */
        void onNameCommitted(@Nullable String name);
    }
}
