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

package com.stario.launcher.gestures;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * Recognizes a two-finger swipe in one of the four cardinal directions.
 * <p>
 * The detector stays completely dormant while only a single pointer is on
 * screen, so it never interferes with the launcher's regular single-finger
 * gestures (sheet dragging, grid rearranging, scrolling, etc). It only
 * starts tracking once a second pointer goes down, and it keeps "owning"
 * the touch stream until every pointer has been lifted, at which point it
 * either reports a detected direction or reports nothing if the movement
 * didn't clear the swipe threshold.
 */
public class TwoFingerSwipeGestureDetector {
    public enum Direction {
        UP, DOWN, LEFT, RIGHT
    }

    public interface OnSwipeListener {
        /**
         * Called the moment two fingers are detected on screen, before any
         * direction has been established. Implementations should use this
         * to cancel/clear whatever the first pointer might have already
         * started (e.g. a single-finger drag).
         */
        void onGestureArmed();

        void onSwipe(Direction direction);

        /**
         * Called once every pointer has been lifted, regardless of whether
         * a swipe was actually detected.
         */
        void onGestureFinished();
    }

    private final int touchSlop;
    private final float minVelocityPx;

    private OnSwipeListener listener;

    private boolean armed;
    private boolean consumed;
    private float startCentroidX;
    private float startCentroidY;

    public TwoFingerSwipeGestureDetector(Context context) {
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        this.minVelocityPx = touchSlop * 2f;
    }

    public void setOnSwipeListener(OnSwipeListener listener) {
        this.listener = listener;
    }

    public boolean isTracking() {
        return armed;
    }

    /**
     * Feed every touch event dispatched to the activity into this method.
     *
     * @return true if the detector is currently owning the gesture (i.e.
     * the caller should swallow the event and not let it reach children).
     */
    public boolean onTouchEvent(MotionEvent event) {
        boolean ownedBeforeThisEvent = armed;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                armed = false;
                consumed = false;

                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (event.getPointerCount() == 2 && !armed) {
                    armed = true;
                    consumed = false;

                    float[] centroid = centroid(event);
                    startCentroidX = centroid[0];
                    startCentroidY = centroid[1];

                    if (listener != null) {
                        listener.onGestureArmed();
                    }
                } else if (event.getPointerCount() > 2) {
                    // a third finger touched down; bail out of the gesture entirely
                    armed = false;
                }

                break;

            case MotionEvent.ACTION_MOVE:
                if (armed && !consumed && event.getPointerCount() >= 2) {
                    float[] centroid = centroid(event);
                    float dx = centroid[0] - startCentroidX;
                    float dy = centroid[1] - startCentroidY;

                    if (Math.hypot(dx, dy) >= Math.max(touchSlop * 3f, minVelocityPx)) {
                        Direction direction = resolveDirection(dx, dy);

                        consumed = true;

                        if (listener != null) {
                            listener.onSwipe(direction);
                        }
                    }
                }

                break;

            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (event.getPointerCount() <= 1) {
                    boolean wasArmed = armed;

                    armed = false;
                    consumed = false;

                    if (wasArmed && listener != null) {
                        listener.onGestureFinished();
                    }
                }

                break;
        }

        // keep swallowing the very last event of an armed gesture (the one that
        // drops the pointer count back to <=1) so children never receive a
        // dangling ACTION_UP with no matching ACTION_DOWN
        return armed || ownedBeforeThisEvent;
    }

    private Direction resolveDirection(float dx, float dy) {
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? Direction.RIGHT : Direction.LEFT;
        } else {
            return dy > 0 ? Direction.DOWN : Direction.UP;
        }
    }

    private float[] centroid(MotionEvent event) {
        float x = 0;
        float y = 0;
        int count = Math.min(event.getPointerCount(), 2);

        for (int index = 0; index < count; index++) {
            x += event.getX(index);
            y += event.getY(index);
        }

        return new float[]{x / count, y / count};
    }
}
