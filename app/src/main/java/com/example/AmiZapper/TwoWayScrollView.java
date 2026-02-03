package com.example.amizapper;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

/**
 * A ScrollView that allows both horizontal and vertical scrolling (diagonal scrolling)
 */
public class TwoWayScrollView extends HorizontalScrollView {
    private float startX, startY;
    private static final int TOUCH_SLOP = 10;

    public TwoWayScrollView(Context context) {
        super(context);
    }

    public TwoWayScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TwoWayScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(ev.getX() - startX);
                float dy = Math.abs(ev.getY() - startY);
                // Allow both horizontal and vertical scrolling
                if (dx > TOUCH_SLOP || dy > TOUCH_SLOP) {
                    return true;
                }
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Handle horizontal scrolling
        return super.onTouchEvent(ev);
    }
}
