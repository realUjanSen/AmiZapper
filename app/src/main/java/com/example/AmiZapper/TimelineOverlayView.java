package com.example.amizapper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.Calendar;

/**
 * A FrameLayout that draws a red timeline indicator showing current time
 */
public class TimelineOverlayView extends FrameLayout {

    private Paint linePaint;
    private Paint circlePaint;
    private Handler handler;
    private Runnable updateRunnable;
    private boolean isVertical = true; // true for day view, false for week view

    // Time boundaries (in minutes from midnight)
    private int startTimeMinutes = 9 * 60 + 15;  // 9:15 AM
    private int endTimeMinutes = 17 * 60 + 25;   // 5:25 PM

    private int contentHeight = 0;
    private int contentWidth = 0;
    private int headerOffset = 0;

    public TimelineOverlayView(@NonNull Context context) {
        super(context);
        init();
    }

    public TimelineOverlayView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TimelineOverlayView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(0xFFE53935); // Red color
        linePaint.setStrokeWidth(3f);
        linePaint.setStyle(Paint.Style.STROKE);

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(0xFFE53935);
        circlePaint.setStyle(Paint.Style.FILL);

        handler = new Handler(Looper.getMainLooper());
        updateRunnable = () -> {
            invalidate();
            // Update every minute
            handler.postDelayed(updateRunnable, 60000);
        };
    }

    public void setVertical(boolean vertical) {
        this.isVertical = vertical;
        invalidate();
    }

    public void setContentDimensions(int width, int height, int headerOffset) {
        this.contentWidth = width;
        this.contentHeight = height;
        this.headerOffset = headerOffset;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.post(updateRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(updateRunnable);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        drawTimeline(canvas);
    }

    private void drawTimeline(Canvas canvas) {
        Calendar now = Calendar.getInstance();
        int currentHour = now.get(Calendar.HOUR_OF_DAY);
        int currentMinute = now.get(Calendar.MINUTE);
        int currentTimeMinutes = currentHour * 60 + currentMinute;

        // Check if current time is within school hours
        if (currentTimeMinutes < startTimeMinutes || currentTimeMinutes > endTimeMinutes) {
            return; // Don't draw timeline outside school hours
        }

        float progress = (float)(currentTimeMinutes - startTimeMinutes) / (endTimeMinutes - startTimeMinutes);

        if (isVertical) {
            // Vertical timeline (for day view)
            int effectiveHeight = contentHeight > 0 ? contentHeight : getHeight() - headerOffset;
            float y = headerOffset + (progress * effectiveHeight);

            // Draw circle on the left
            canvas.drawCircle(8, y, 6, circlePaint);

            // Draw line across
            canvas.drawLine(14, y, getWidth(), y, linePaint);
        } else {
            // Horizontal timeline (for week view)
            int effectiveWidth = contentWidth > 0 ? contentWidth : getWidth() - headerOffset;
            float x = headerOffset + (progress * effectiveWidth);

            // Draw circle on top
            canvas.drawCircle(x, 8, 6, circlePaint);

            // Draw line down
            canvas.drawLine(x, 14, x, getHeight(), linePaint);
        }
    }
}
