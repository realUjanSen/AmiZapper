package com.example.amizapper;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DayPagerAdapter extends RecyclerView.Adapter<DayPagerAdapter.DayViewHolder> {

    private final String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
    private Map<String, List<TimetableClass>> timetableByDay = new HashMap<>();
    private final LayoutInflater inflater;

    public DayPagerAdapter(LayoutInflater inflater) {
        this.inflater = inflater;
    }

    public void setTimetableData(Map<String, List<TimetableClass>> data) {
        this.timetableByDay = data;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.fragment_day_timetable, parent, false);
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        String day = days[position];
        List<TimetableClass> classes = timetableByDay.get(day);
        holder.bind(classes, inflater, position);
    }

    @Override
    public void onViewRecycled(@NonNull DayViewHolder holder) {
        super.onViewRecycled(holder);
        holder.stopTimeUpdates();
    }

    @Override
    public int getItemCount() {
        return days.length;
    }

    static class DayViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout classesContainer;
        private final LinearLayout emptyState;
        private final List<TimeIndicatorInfo> indicatorInfos = new ArrayList<>();
        private Handler handler;
        private Runnable updateRunnable;
        private int dayPosition = -1;

        private static class TimeIndicatorInfo {
            View indicator;
            View indicatorCircle;
            View cardView;
            String startTime;
            String endTime;
            int periodNumber;
            boolean isLunchBreak;
            boolean isGapStart; // Show indicator at bottom when in gap
            boolean isGapEnd;   // Show indicator at top when in gap

            TimeIndicatorInfo(View indicator, View indicatorCircle, View cardView,
                              String startTime, String endTime, int periodNumber, boolean isLunchBreak) {
                this.indicator = indicator;
                this.indicatorCircle = indicatorCircle;
                this.cardView = cardView;
                this.startTime = startTime;
                this.endTime = endTime;
                this.periodNumber = periodNumber;
                this.isLunchBreak = isLunchBreak;
            }
        }

        DayViewHolder(@NonNull View itemView) {
            super(itemView);
            classesContainer = itemView.findViewById(R.id.classesContainer);
            emptyState = itemView.findViewById(R.id.emptyState);

            handler = new Handler(Looper.getMainLooper());
            updateRunnable = () -> {
                updateTimeIndicators();
                handler.postDelayed(updateRunnable, 30000); // Update every 30 seconds
            };
        }

        void setDayPosition(int position) {
            this.dayPosition = position;
        }

        void startTimeUpdates() {
            handler.removeCallbacks(updateRunnable);
            handler.post(updateRunnable);
        }

        void stopTimeUpdates() {
            handler.removeCallbacks(updateRunnable);
        }

        private void updateTimeIndicators() {
            // Only show indicators on today's view
            if (!CurrentTimeIndicator.isToday(dayPosition)) {
                hideAllIndicators();
                return;
            }

            int currentPeriod = CurrentTimeIndicator.getCurrentPeriodNumber();
            int[] gapPeriods = CurrentTimeIndicator.getGapAdjacentPeriods();

            for (TimeIndicatorInfo info : indicatorInfos) {
                boolean showIndicator = false;
                float progress = 0;
                boolean atBottom = false;
                boolean atTop = false;

                if (currentPeriod >= 0) {
                    // We're in a period (including lunch = 0)
                    boolean isCurrentPeriod = (info.isLunchBreak && currentPeriod == 0) ||
                            (!info.isLunchBreak && info.periodNumber == currentPeriod);

                    if (isCurrentPeriod) {
                        showIndicator = true;
                        CurrentTimeIndicator.IndicatorPosition pos =
                                CurrentTimeIndicator.getIndicatorPosition(info.startTime, info.endTime);
                        progress = pos.progress;
                    }
                } else if (currentPeriod == -2 && gapPeriods != null) {
                    // We're in a gap between periods
                    int prevPeriod = gapPeriods[0];
                    int nextPeriod = gapPeriods[1];

                    // Show at bottom of previous period
                    boolean isPrevPeriod = (info.isLunchBreak && prevPeriod == 0) ||
                            (!info.isLunchBreak && info.periodNumber == prevPeriod);
                    if (isPrevPeriod) {
                        showIndicator = true;
                        atBottom = true;
                        progress = 1.0f;
                    }

                    // Show at top of next period
                    boolean isNextPeriod = (info.isLunchBreak && nextPeriod == 0) ||
                            (!info.isLunchBreak && info.periodNumber == nextPeriod);
                    if (isNextPeriod) {
                        showIndicator = true;
                        atTop = true;
                        progress = 0.0f;
                    }
                }

                if (showIndicator) {
                    positionIndicator(info, progress, atBottom, atTop);
                } else {
                    info.indicator.setVisibility(View.GONE);
                    info.indicatorCircle.setVisibility(View.GONE);
                }
            }
        }

        private void positionIndicator(TimeIndicatorInfo info, float progress, boolean atBottom, boolean atTop) {
            final float finalProgress = progress;
            info.cardView.post(() -> {
                int cardHeight = info.cardView.getHeight();
                if (cardHeight <= 0) return;

                float yPos;
                if (atBottom) {
                    // Position at the absolute bottom edge of the card (minus line height to keep line within card)
                    yPos = cardHeight - 3; // 3dp line at very bottom edge
                } else if (atTop) {
                    yPos = 0; // At top of card
                } else {
                    // Clamp progress to ensure indicator stays within card bounds
                    float clampedProgress = Math.max(0f, Math.min(1f, finalProgress));
                    yPos = clampedProgress * (cardHeight - 3); // Leave room for 3dp line
                }

                // Position the line
                info.indicator.setVisibility(View.VISIBLE);
                FrameLayout.LayoutParams lineParams = (FrameLayout.LayoutParams) info.indicator.getLayoutParams();
                lineParams.topMargin = (int) yPos;
                info.indicator.setLayoutParams(lineParams);

                // Position the circle - center 12dp circle on 3dp line
                // Adjust circle position to be centered on the line
                float circleOffset = (12f - 3f) / 2f; // 4.5dp
                info.indicatorCircle.setVisibility(View.VISIBLE);
                FrameLayout.LayoutParams circleParams = (FrameLayout.LayoutParams) info.indicatorCircle.getLayoutParams();
                circleParams.topMargin = Math.max(0, (int) (yPos - circleOffset)); // Don't go negative
                circleParams.leftMargin = 0;
                info.indicatorCircle.setLayoutParams(circleParams);
            });
        }

        private void hideAllIndicators() {
            for (TimeIndicatorInfo info : indicatorInfos) {
                info.indicator.setVisibility(View.GONE);
                info.indicatorCircle.setVisibility(View.GONE);
            }
        }

        void bind(List<TimetableClass> classes, LayoutInflater inflater, int dayPosition) {
            classesContainer.removeAllViews();
            indicatorInfos.clear();
            setDayPosition(dayPosition);

            if (classes == null || classes.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                classesContainer.setVisibility(View.GONE);
                stopTimeUpdates();
                return;
            }

            emptyState.setVisibility(View.GONE);
            classesContainer.setVisibility(View.VISIBLE);

            // Create a map of period number to class
            Map<Integer, TimetableClass> classByPeriod = new HashMap<>();
            for (TimetableClass cls : classes) {
                int period = PeriodSlots.findPeriodNumber(cls.getStartTime());
                if (period > 0) {
                    classByPeriod.put(period, cls);
                }
            }

            // Find the last period with a class
            int lastPeriod = PeriodSlots.getLastScheduledPeriod(classes);

            // Display periods up to the last scheduled one
            for (PeriodSlots.PeriodSlot slot : PeriodSlots.getAllSlots()) {
                if (slot.isBreak) {
                    // Only show lunch break if there are afternoon classes
                    if (lastPeriod >= 5) {
                        addLunchBreakView(inflater, slot);
                    }
                } else if (slot.periodNumber <= lastPeriod) {
                    TimetableClass cls = classByPeriod.get(slot.periodNumber);
                    if (cls != null) {
                        addClassView(cls, slot.periodNumber, inflater);
                    } else {
                        addFreePeriodView(slot, inflater);
                    }
                }
            }

            // If classes end before lunch (before Period 5), show early dismissal card
            if (lastPeriod > 0 && lastPeriod < 5) {
                // Get the end time of the last class
                TimetableClass lastClass = classByPeriod.get(lastPeriod);
                if (lastClass != null) {
                    addEarlyDismissalCard(lastClass.getEndTime(), inflater);
                }
            }

            // Start time indicator updates
            startTimeUpdates();
        }

        private void addClassView(TimetableClass cls, int periodNumber, LayoutInflater inflater) {
            View view = inflater.inflate(R.layout.item_timetable_class, classesContainer, false);

            TextView startTimeText = view.findViewById(R.id.startTimeText);
            TextView endTimeText = view.findViewById(R.id.endTimeText);
            TextView courseCodeText = view.findViewById(R.id.courseCodeText);
            TextView subjectCodeText = view.findViewById(R.id.subjectCodeText);
            TextView teacherText = view.findViewById(R.id.teacherText);
            TextView locationText = view.findViewById(R.id.locationText);
            TextView attendanceText = view.findViewById(R.id.attendanceText);
            View attendanceIndicator = view.findViewById(R.id.attendanceIndicator);
            View timeIndicator = view.findViewById(R.id.timeIndicator);
            View timeIndicatorCircle = view.findViewById(R.id.timeIndicatorCircle);

            startTimeText.setText(cls.getStartTime());
            endTimeText.setText(cls.getEndTime());
            // Show subject name without credits
            courseCodeText.setText(cls.getShortSubjectName(50));
            // Show course code with credits
            subjectCodeText.setText(SubjectMapper.getCourseCodeWithCredits(cls.getCourseCode()));
            teacherText.setText(cls.getShortTeacher());
            locationText.setText(cls.getLocation());

            // Display attendance percentage if available
            if (attendanceText != null) {
                String courseCode = cls.getCourseCode();
                android.util.Log.d("DayPagerAdapter", "Looking up attendance for: " + courseCode);
                AttendanceData attData = AttendanceStorage.getForCourse(
                        view.getContext(), courseCode);
                if (attData != null) {
                    String attStr = String.format(java.util.Locale.US, "%.0f%% (%d/%d)",
                            attData.getPercentage(),
                            attData.getClassesAttended(),
                            attData.getTotalClasses());
                    android.util.Log.d("DayPagerAdapter", "Found attendance for " + courseCode + ": " + attStr);
                    attendanceText.setText(attStr);
                    attendanceText.setVisibility(View.VISIBLE);
                    // Set color to white/gray (same as teacher text) instead of green
                    attendanceText.setTextColor(Color.parseColor("#B0B0B0"));
                } else {
                    android.util.Log.d("DayPagerAdapter", "No attendance found for: " + courseCode);
                    attendanceText.setVisibility(View.GONE);
                }
            }

            // Set attendance indicator color
            try {
                String attColor = cls.getAttendanceColor();
                if (attColor != null && !attColor.isEmpty()) {
                    attendanceIndicator.setBackgroundColor(Color.parseColor(attColor));
                }
            } catch (Exception e) {
                // Default to blue if color parsing fails
                attendanceIndicator.setBackgroundColor(Color.parseColor("#3a87ad"));
            }

            // Register time indicator info
            if (timeIndicator != null && timeIndicatorCircle != null) {
                indicatorInfos.add(new TimeIndicatorInfo(
                        timeIndicator, timeIndicatorCircle, view,
                        cls.getStartTime(), cls.getEndTime(), periodNumber, false));
            }

            classesContainer.addView(view);
        }

        private void addFreePeriodView(PeriodSlots.PeriodSlot slot, LayoutInflater inflater) {
            View view = inflater.inflate(R.layout.item_free_period, classesContainer, false);

            TextView startTimeText = view.findViewById(R.id.startTimeText);
            TextView endTimeText = view.findViewById(R.id.endTimeText);
            TextView freePeriodText = view.findViewById(R.id.freePeriodText);
            View timeIndicator = view.findViewById(R.id.timeIndicator);
            View timeIndicatorCircle = view.findViewById(R.id.timeIndicatorCircle);

            startTimeText.setText(slot.startTime);
            endTimeText.setText(slot.endTime);
            freePeriodText.setText("Free Period");

            // Register time indicator info
            if (timeIndicator != null && timeIndicatorCircle != null) {
                indicatorInfos.add(new TimeIndicatorInfo(
                        timeIndicator, timeIndicatorCircle, view,
                        slot.startTime, slot.endTime, slot.periodNumber, false));
            }

            classesContainer.addView(view);
        }

        private void addLunchBreakView(LayoutInflater inflater, PeriodSlots.PeriodSlot slot) {
            View view = inflater.inflate(R.layout.item_lunch_break, classesContainer, false);
            View timeIndicator = view.findViewById(R.id.timeIndicator);
            View timeIndicatorCircle = view.findViewById(R.id.timeIndicatorCircle);

            // Register time indicator info for lunch (period 0 represents lunch)
            if (timeIndicator != null && timeIndicatorCircle != null) {
                indicatorInfos.add(new TimeIndicatorInfo(
                        timeIndicator, timeIndicatorCircle, view,
                        slot.startTime, slot.endTime, 0, true));
            }

            classesContainer.addView(view);
        }

        private void addEarlyDismissalCard(String endTime, LayoutInflater inflater) {
            View view = inflater.inflate(R.layout.item_early_dismissal, classesContainer, false);

            TextView endTimeText = view.findViewById(R.id.dismissalTimeText);
            if (endTimeText != null) {
                endTimeText.setText(endTime);
            }

            classesContainer.addView(view);
        }
    }
}
