/*
 * Copyright (C) 2025 Răzvan Albu
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

package com.stario.launcher.ui.widgets;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;

import com.stario.launcher.sheet.widgets.Widget;
import com.stario.launcher.sheet.widgets.WidgetSize;
import com.stario.launcher.ui.Measurements;

@SuppressLint("ViewConstructor")
public class WidgetContainer extends RelativeLayout implements Comparable<WidgetContainer> {
    // Usually an AppWidgetHostView, but a widget stack slot hosts a plain
    // View (WidgetStackView) instead - there is no single AppWidgetHostView
    // to report a size to, each of its child pages manages its own.
    private final View host;
    private final Widget widget;
    private WidgetMap.Cell origin; // top-left

    WidgetContainer(Context context, View host, Widget widget, WidgetMap.Cell cell) {
        super(context);

        this.origin = cell;
        this.host = host;
        this.widget = widget;

        int padding = Measurements.dpToPx(10);
        setPadding(padding, padding, padding, padding);
        setRotation(180);

        // Any addView(host) overload - regardless of explicit params, of
        // whether this container has been attached yet, or of whether host
        // itself has any children yet - ends up calling host.setLayoutParams(),
        // which crashes with a NullPointerException in
        // ViewGroup.resolveLayoutParams() specifically when host is a
        // WidgetStackView, on some newer Android versions. A plain
        // AppWidgetHostView never hits it.
        //
        // addViewInLayout(..., preventRequestLayout=true) is the framework's
        // own escape hatch for adding a child without going through
        // setLayoutParams() at all - it assigns the LayoutParams field
        // directly instead (the same mechanism RecyclerView/ViewPager use
        // internally to add views outside the normal flow), sidestepping
        // the crash entirely. It skips the requestLayout()/invalidate()
        // that addView() normally triggers, so those are called explicitly
        // afterward.
        ViewGroup.LayoutParams params = host.getLayoutParams();

        if (params == null) {
            params = generateDefaultLayoutParams();
        }

        addViewInLayout(host, -1, params, true);

        requestLayout();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!(getParent() instanceof WidgetGrid)) {
            throw new RuntimeException("WidgetContainer views can only be children of WidgetGrid");
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        GridLayout.LayoutParams params = (GridLayout.LayoutParams) getLayoutParams();

        int cellSize = ((WidgetGrid) getParent()).getCellSize();

        params.rowSpec = WidgetGrid.spec(origin.row, widget.size.height);
        params.columnSpec = WidgetGrid.spec(origin.column, widget.size.width);
        params.width = cellSize * widget.size.width;
        params.height = cellSize * widget.size.height;

        setLayoutParams(params);

        int hostWidth = params.width - getPaddingLeft() - getPaddingRight();
        int hostHeight = params.height - getPaddingTop() - getPaddingBottom();

        if (getMeasuredWidth() > 0 && getMeasuredHeight() > 0 && host instanceof AppWidgetHostView) {
            ((AppWidgetHostView) host).updateAppWidgetSize(null,
                    (int) (hostWidth / Measurements.getDensity()),
                    (int) (hostHeight / Measurements.getDensity()),
                    (int) (hostWidth / Measurements.getDensity()),
                    (int) (hostHeight / Measurements.getDensity()));
        }
    }

    public WidgetSize getSize() {
        return widget.size;
    }

    public int getPosition() {
        return widget.position;
    }

    public Widget getWidget() {
        return widget;
    }

    public int getOriginRow() {
        return origin.row;
    }

    public int getOriginColumn() {
        return origin.column;
    }

    void updateOrigin(WidgetMap.Cell origin) {
        if (origin != null && !origin.equals(this.origin)) {
            this.origin = origin;

            requestLayout();
        }
    }

    @Override
    public int compareTo(@NonNull WidgetContainer container) {
        return widget.compareTo(container.widget);
    }
}
