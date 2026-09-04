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

package com.stario.launcher.ui.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View.MeasureSpec;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.stario.launcher.R;
import com.stario.launcher.gestures.TwoFingerSwipeGestureDetector;
import com.stario.launcher.preferences.Vibrations;
import com.stario.launcher.ui.Measurements;

import java.util.List;

/**
 * A single grid tile that hosts several real, independently bound app
 * widgets as horizontally-swipeable pages instead of just one - a "widget
 * stack" / carousel, added through the same picker as any other widget
 * (see WidgetConfigurator's pinned "Widget stack" entry).
 * <p>
 * Ownership stays with the caller (WidgetsDialog): this view only ever
 * asks for child views through {@link Callback#createChildView(int)} and
 * reports additions/removals back - it never touches AppWidgetHost/
 * AppWidgetManager itself, so the same allocate/bind/configure flow used
 * for top-level widgets can be reused unchanged for stack children.
 */
@SuppressLint("ViewConstructor")
public class WidgetStackView extends FrameLayout {
    // Above this many children, dots would either overflow the header or
    // become too small to tap reliably - fall back to the plain "x / y"
    // text instead of trying to cram that many dots in.
    private static final int MAX_DOTS = 8;

    private final List<Integer> children;
    private final Callback callback;
    private ChildAdapter adapter;
    private TextView nameView;
    private TextView pageIndicator;
    private LinearLayout dots;
    private RecyclerView recycler;
    private LinearLayoutManager layoutManager;
    private PagerSnapHelper snapHelper;
    private TwoFingerSwipeGestureDetector twoFingerDetector;
    private boolean contentAttached;
    private String pendingName;
    private OnLongClickListener pendingHeaderListener;
    private boolean pendingHeaderListenerSet;

    public WidgetStackView(Context context, List<Integer> children, Callback callback) {
        super(context);

        // Assign these before anything else in this constructor: this
        // class's own getChildCount() override reads `children`, and
        // Android's internal layout/RTL-resolution machinery calls
        // getChildCount() on a ViewGroup as part of adding/modifying it -
        // including, as it turns out, from inside setLayoutDirection()
        // itself below. Every earlier crash chasing a supposed framework
        // NullPointerException was really just this: `children` still
        // being null when that machinery called back into our own
        // getChildCount() override.
        this.children = children;
        this.callback = callback;

        // ViewGroup.addViewInner() calls child.resetRtlProperties() -
        // whose ViewGroup override walks the child's own children via
        // getChildCount() - whenever the child's isLayoutDirectionInherited()
        // is true, which it is by default. Explicitly resolving this view's
        // own direction up front means that guard is false, so that whole
        // walk is skipped once this view is actually added elsewhere.
        setLayoutDirection(LAYOUT_DIRECTION_LOCALE);

        // Adding a ViewGroup that already has real child views (this one's
        // recycler/dots/name header, once inflated) to a fresh parent
        // crashes with a NullPointerException in
        // ViewGroup.resolveLayoutParams() on some newer Android versions -
        // a plain AppWidgetHostView never hits this because its actual
        // remote-view content only arrives asynchronously after it's
        // already attached. Mirror that here: stay a completely empty
        // FrameLayout until this view is itself attached to a window (i.e.
        // until WidgetContainer.addView(this) has already safely returned),
        // then build the real content in attachContent().
        post(this::attachContent);
    }

    /**
     * Builds the actual stack content - deferred out of the constructor,
     * see the comment there. Safe to call only once this view is already
     * attached to its parent.
     */
    private void attachContent() {
        if (contentAttached) {
            return;
        }

        contentAttached = true;

        Context context = getContext();

        View root = LayoutInflater.from(context)
                .inflate(R.layout.widget_stack_container, this, true);

        nameView = root.findViewById(R.id.name);
        pageIndicator = root.findViewById(R.id.page_indicator);
        dots = root.findViewById(R.id.dots);
        recycler = root.findViewById(R.id.recycler);

        layoutManager = new LinearLayoutManager(context,
                LinearLayoutManager.HORIZONTAL, false);
        recycler.setLayoutManager(layoutManager);
        recycler.setItemAnimator(null);

        // The stack lives inside the widgets sheet, which is itself
        // dismissed by a horizontal swipe (RightSheetBehavior). That sheet
        // decides whether to treat a horizontal drag as "close me" from the
        // raw touch stream, before this recycler ever sees the event - so
        // without this, swiping to the previous/next widget at the very
        // start of the gesture gets stolen and closes the sheet instead of
        // paging the stack. Claiming the touch stream up front lets the
        // recycler's own (already boundary-aware) nested-scroll dispatch
        // negotiate with the sheet instead, which correctly hands off to
        // the sheet's close gesture only once there is no more previous/next
        // widget to scroll to.
        recycler.setNestedScrollingEnabled(true);

        // A two-finger swipe anywhere on the stack jumps straight to the
        // first/last widget - independent of (and on top of) the regular
        // single-finger paging. It's a fresh detector scoped to just this
        // recycler rather than the launcher's global one (Launcher.java),
        // since the widgets sheet is its own Dialog window and never sees
        // that instance's touch feed at all.
        twoFingerDetector = new TwoFingerSwipeGestureDetector(context);
        twoFingerDetector.setOnSwipeListener(new TwoFingerSwipeGestureDetector.OnSwipeListener() {
            @Override
            public void onGestureArmed() {
                // No extra bookkeeping needed: once this listener's
                // onInterceptTouchEvent below returns true for the
                // triggering ACTION_POINTER_DOWN, RecyclerView cancels
                // whatever single-finger drag it had already started.
            }

            @Override
            public void onSwipe(TwoFingerSwipeGestureDetector.Direction direction) {
                if (direction == TwoFingerSwipeGestureDetector.Direction.LEFT) {
                    Vibrations.getInstance().vibrate();

                    scrollToPage(children.size() - 1);
                } else if (direction == TwoFingerSwipeGestureDetector.Direction.RIGHT) {
                    Vibrations.getInstance().vibrate();

                    scrollToPage(0);
                }
            }

            @Override
            public void onGestureFinished() {
            }
        });

        recycler.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView view, @NonNull MotionEvent event) {
                return handle(view, event);
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView view, @NonNull MotionEvent event) {
                handle(view, event);
            }

            private boolean handle(RecyclerView view, MotionEvent event) {
                boolean twoFingerOwnsGesture = twoFingerDetector.onTouchEvent(event);

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        ViewParent parent = view.getParent();

                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        ViewParent releasedParent = view.getParent();

                        if (releasedParent != null) {
                            releasedParent.requestDisallowInterceptTouchEvent(false);
                        }
                        break;
                }

                return twoFingerOwnsGesture;
            }
        });

        snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(recycler);

        adapter = new ChildAdapter();
        recycler.setAdapter(adapter);

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                updatePageIndicator();
            }
        });

        if (pendingHeaderListenerSet) {
            setOnHeaderLongClickListener(pendingHeaderListener);
        } else {
            setOnHeaderLongClickListener(null);
        }

        if (pendingName != null) {
            setName(pendingName);
        }

        updatePageIndicator();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // WidgetContainer hands this view whatever space it has available
        // (WRAP_CONTENT-style AT_MOST specs, since it never gets explicit
        // MATCH_PARENT LayoutParams - see WidgetContainer's addView(host)
        // call), so claim all of it here instead of collapsing down to
        // however small its own content would otherwise measure.
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    public void setOnHeaderLongClickListener(OnLongClickListener listener) {
        // Called by WidgetsDialog right after construction, before
        // attachContent() (deferred via post()) has necessarily run yet -
        // stash it and apply once the header view actually exists.
        if (!contentAttached) {
            pendingHeaderListener = listener;
            pendingHeaderListenerSet = true;

            return;
        }

        View header = findViewById(R.id.header);

        header.setLongClickable(listener != null);
        header.setOnLongClickListener(listener);
    }

    /**
     * Shows the given label in the header, or hides it entirely when null
     * or blank so the plain dot/page indicator gets the header's full
     * width, matching a stack that was never renamed.
     */
    public void setName(@Nullable String name) {
        // Same reasoning as setOnHeaderLongClickListener(): may be called
        // before attachContent() has built nameView yet.
        if (!contentAttached) {
            pendingName = name;

            return;
        }

        if (TextUtils.isEmpty(name)) {
            nameView.setVisibility(GONE);
            nameView.setText(null);
        } else {
            nameView.setText(name);
            nameView.setVisibility(VISIBLE);
        }
    }

    /**
     * Number of real widgets currently in the stack (the trailing "add"
     * page doesn't count). Overrides ViewGroup's own getChildCount() (real
     * attached child views), which Android's internal view-tree machinery
     * can call on this object before the constructor has finished - see
     * the comment at the top of the constructor - hence the null guard.
     */
    @Override
    public int getChildCount() {
        return children != null ? children.size() : 0;
    }

    /**
     * Snap directly to the page at {@code index} (clamped into range),
     * animating the same way a manual swipe would settle. Used by both the
     * tappable page dots and a two-finger jump-to-first/last gesture.
     */
    public void scrollToPage(int index) {
        if (children.isEmpty()) {
            return;
        }

        int target = Math.max(0, Math.min(index, children.size() - 1));

        recycler.smoothScrollToPosition(target);
    }

    /**
     * Call after the children list changes (a child was added or removed)
     * to refresh the carousel and the page indicator.
     */
    public void notifyChildrenChanged() {
        adapter.notifyDataSetChanged();
        updatePageIndicator();
    }

    private void updatePageIndicator() {
        if (children.isEmpty()) {
            pageIndicator.setVisibility(GONE);
            dots.setVisibility(GONE);

            return;
        }

        int position = layoutManager.findFirstVisibleItemPosition();

        if (position < 0) {
            return;
        }

        int page = Math.min(position, children.size() - 1);

        if (children.size() > MAX_DOTS) {
            dots.setVisibility(GONE);
            pageIndicator.setVisibility(VISIBLE);

            pageIndicator.setText(getContext().getString(R.string.widget_stack_page_count,
                    page + 1, children.size()));
        } else {
            pageIndicator.setVisibility(GONE);

            updateDots(page);
        }
    }

    private void updateDots(int selected) {
        if (dots.getChildCount() != children.size()) {
            dots.removeAllViews();

            int size = Measurements.dpToPx(6);
            int margin = Measurements.dpToPx(2);

            for (int index = 0; index < children.size(); index++) {
                View dot = new View(getContext());

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
                params.setMargins(margin, 0, margin, 0);
                dot.setLayoutParams(params);

                int page = index;
                dot.setOnClickListener(v -> scrollToPage(page));

                dots.addView(dot);
            }
        }

        for (int index = 0; index < dots.getChildCount(); index++) {
            View dot = dots.getChildAt(index);

            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(MaterialColors.getColor(dot,
                    com.google.android.material.R.attr.colorOnSurface));

            dot.setBackground(background);
            dot.setAlpha(index == selected ? 0.9f : 0.3f);
        }

        dots.setVisibility(VISIBLE);
    }

    private class ChildAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_ADD = -1;

        @Override
        public int getItemViewType(int position) {
            return position < children.size() ? children.get(position) : TYPE_ADD;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_ADD) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.widget_stack_add_page, parent, false);

                view.setOnClickListener(v -> {
                    if (callback != null) {
                        callback.onAddRequested(WidgetStackView.this);
                    }
                });

                return new RecyclerView.ViewHolder(view) {
                };
            }

            int appWidgetId = viewType;

            FrameLayout wrapper = new FrameLayout(parent.getContext());
            wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            if (callback != null) {
                View host = callback.createChildView(appWidgetId);

                if (host != null) {
                    if (host.getParent() instanceof ViewGroup) {
                        ((ViewGroup) host.getParent()).removeView(host);
                    }

                    host.setLayoutParams(new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                    host.setOnLongClickListener(v -> {
                        callback.onRemoveRequested(WidgetStackView.this, appWidgetId);

                        return true;
                    });

                    wrapper.addView(host);
                }
            }

            return new RecyclerView.ViewHolder(wrapper) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            // Nothing to bind: the view type already identifies exactly which
            // child (or the trailing add page) this holder is for, so its
            // content is fully built in onCreateViewHolder(). This trades a
            // (harmless, since stacks hold a handful of children at most)
            // larger RecycledViewPool for the guarantee that a live,
            // stateful AppWidgetHostView never gets silently rebound to a
            // different widget.
        }

        @Override
        public int getItemCount() {
            return children.size() + 1;
        }
    }

    public interface Callback {
        /**
         * Build (or reuse) the real, already-bound AppWidgetHostView for
         * this child. May return null if the widget can no longer be
         * resolved (e.g. its provider was uninstalled).
         */
        View createChildView(int appWidgetId);

        void onAddRequested(WidgetStackView stack);

        void onRemoveRequested(WidgetStackView stack, int appWidgetId);
    }
}
