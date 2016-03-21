package solar.blaz.date.week;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.text.TextDirectionHeuristicCompat;
import android.support.v4.text.TextDirectionHeuristicsCompat;
import android.text.BoringLayout;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import org.threeten.bp.DayOfWeek;
import org.threeten.bp.LocalDate;
import org.threeten.bp.format.TextStyle;
import org.threeten.bp.temporal.TemporalAdjusters;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created by Blaž Šolar on 24/01/14.
 */
public class WeekDatePicker extends View {

    public static final String TAG = "DatePicker";

    /**
     * The coefficient by which to adjust (divide) the max fling velocity.
     */
    private static final int SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT = 4;

    /**
     * The the duration for adjusting the selector wheel.
     */
    private static final int SELECTOR_ADJUSTMENT_DURATION_MILLIS = 800;

    /**
     * Determines speed during touch scrolling.
     */
    private VelocityTracker velocityTracker;

    /**
     * @see ViewConfiguration#getScaledMinimumFlingVelocity()
     */
    private int minimumFlingVelocity;

    /**
     * @see ViewConfiguration#getScaledMaximumFlingVelocity()
     */
    private int maximumFlingVelocity;

    private int touchSlop;

    private final LocalDate today;
    private final LocalDate firstDay; // first day of week for current date
    private final DayOfWeek firstDayOfWeek;
    private final BoringLayout[] layouts = new BoringLayout[3 * 7]; // we are drawing 3 weeks at a time on screen
    private final BoringLayout[] dayLabelLayouts = new BoringLayout[7];

    @Nullable private final CharSequence[] labelNames;

    private final TextPaint dayTextPaint;
    private final TextPaint dayLabelTextPain;
    private final Paint selectedDayColor;

    private BoringLayout.Metrics dayMetrics;
    private BoringLayout.Metrics dayLabelMetrics;

    private ColorStateList dayTextColor;
    private ColorStateList dayLabelTextColor;

    private TextUtils.TruncateAt ellipsize;

    @Nullable private Drawable dayDrawable;
    @Nullable private Drawable indicatorDrawable;

    private int weekWidth;
    private int dayWidth;

    private float lastDownEventX;

    private OverScroller flingScrollerX;
    private OverScroller adjustScrollerX;

    private int previousScrollerX;

    private boolean scrollingX;
    private int scrollPositionStart;

    private OnWeekChanged onWeekChanged;
    private OnDateSelected onDateSelected;

    private final SparseBooleanArray dayIndicators = new SparseBooleanArray();

    private int selectedWeek;
    private int selectedDay;
    private int pressedDay = Integer.MIN_VALUE;

    private float dividerSize = 0;
    private float labelPadding = 0;

    @Nullable private Rect backgroundRect;
    @Nullable private Rect indicatorRect;

    private TextDirectionHeuristicCompat textDir;

    public WeekDatePicker(Context context) {
        this(context, null);
    }

    public WeekDatePicker(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.weekDatePickerStyle);
    }

    public WeekDatePicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // create the selector wheel paint
        TextPaint paint = new TextPaint();
        paint.setAntiAlias(true);
        dayTextPaint = paint;

        dayLabelTextPain = new TextPaint();
        dayLabelTextPain.setAntiAlias(true);

        selectedDayColor = new Paint();
        selectedDayColor.setColor(Color.RED);
        selectedDayColor.setStyle(Style.FILL);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.WeekDatePicker,
                defStyle, 0
        );

        int ellipsize = 3; // END default value

        try {
            dayTextColor = a.getColorStateList(R.styleable.WeekDatePicker_android_textColor);
            if (dayTextColor == null) {
                dayTextColor = ColorStateList.valueOf(Color.BLACK);
            }

            dayLabelTextColor = a.getColorStateList(R.styleable.WeekDatePicker_wdp_labelTextColor);
            if (dayLabelTextColor == null) {
                dayLabelTextColor = ColorStateList.valueOf(Color.BLACK);
            }

            ellipsize = a.getInt(R.styleable.WeekDatePicker_android_ellipsize, ellipsize);
            dividerSize = a.getDimension(R.styleable.WeekDatePicker_wdp_dividerSize, dividerSize);

            float textSize = a.getDimension(R.styleable.WeekDatePicker_android_textSize, -1);
            if(textSize > -1) {
                setTextSize(textSize);
            }

            float labelTextSize = a.getDimension(R.styleable.WeekDatePicker_wdp_labelTextSize, -1);
            if (labelTextSize > -1) {
                setLabelTextSize(labelTextSize);
            }

            labelPadding = a.getDimension(R.styleable.WeekDatePicker_wdp_labelPadding, labelPadding);

            labelNames = a.getTextArray(R.styleable.WeekDatePicker_wdp_labelNames);

            dayDrawable = a.getDrawable(R.styleable.WeekDatePicker_wdp_dayBackground);
            indicatorDrawable = a.getDrawable(R.styleable.WeekDatePicker_wdp_indicatorDrawable);

            int dayOfWeek = a.getInt(R.styleable.WeekDatePicker_wdp_firstDayOfWeek, DayOfWeek.SUNDAY.getValue());
            firstDayOfWeek = DayOfWeek.of(dayOfWeek);

        } finally {
            a.recycle();
        }

        switch (ellipsize) {
            case 1:
                setEllipsize(TextUtils.TruncateAt.START);
                break;
            case 2:
                setEllipsize(TextUtils.TruncateAt.MIDDLE);
                break;
            case 3:
                setEllipsize(TextUtils.TruncateAt.END);
                break;
            case 4:
                setEllipsize(TextUtils.TruncateAt.MARQUEE);
                break;
        }

        buildFontMetrics();
        buildLabelFontMetrics();

//        setWillNotDraw(false);

        flingScrollerX = new OverScroller(context);
        adjustScrollerX = new OverScroller(context, new DecelerateInterpolator(2.5f));

        // initialize constants
        ViewConfiguration configuration = ViewConfiguration.get(context);
        touchSlop = configuration.getScaledTouchSlop();
        minimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        maximumFlingVelocity = configuration.getScaledMaximumFlingVelocity()
                / SELECTOR_MAX_FLING_VELOCITY_ADJUSTMENT;

        previousScrollerX = Integer.MIN_VALUE;

        calculateItemSize(getWidth(), getHeight());

//        mTouchHelper = new PickerTouchHelper(this);
//        ViewCompat.setAccessibilityDelegate(this, mTouchHelper);

        today = LocalDate.now();
        firstDay = getFirstDay(0);
        selectedDay = firstDay.until(today).getDays();

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        for (int i = 0; i < 2; i++) {

            if (i != 0 && width == getMeasuredWidth()) {
                break;
            }

            int height;
            if (heightMode == MeasureSpec.EXACTLY) {
                height = heightSize;
            } else {
                int labelTextHeight = Math.abs(dayLabelMetrics.ascent) + Math.abs(dayLabelMetrics.descent);
                labelTextHeight += getPaddingTop() + getPaddingBottom();

                int measuredWidth = getMeasuredWidth();
                if (measuredWidth == 0) {
                    measuredWidth = width;
                }

                int totalHeight = (int) (labelTextHeight + measuredWidth / 7 / 3 * 2 + labelPadding);

                if (heightMode == MeasureSpec.AT_MOST) {
                    height = Math.min(heightSize, totalHeight);
                } else {
                    height = totalHeight;
                }
            }

            setMeasuredDimension(width, height);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float itemWithPadding = weekWidth + dividerSize;

        int saveCount = canvas.getSaveCount();
        canvas.save();

        int weekOffset = getSelectedWeek() - 1;
        float position = itemWithPadding * weekOffset;

        canvas.translate(position, getPaddingTop());

        for (int i = 0; i < 3; i++) {
            drawWeek(canvas, i * 7, weekOffset + i);
            canvas.translate(itemWithPadding, 0);
        }

        canvas.restoreToCount(saveCount);

    }

    private void drawWeek(Canvas canvas, int layoutIndex, int weekOffset) {

        int saveCount = canvas.save();

        int labelHeight = dayLabelLayouts[0].getHeight();
        float circleRadius = dayWidth / 3;
        int centerY = layouts[0].getHeight() / 2;
        float dateLineOffset = circleRadius - centerY;

        for (int i = 0; i < 7; i++) {

            int itemIndex = weekOffset * 7 + i;
            BoringLayout layout = layouts[layoutIndex + i];
            BoringLayout labelLayout = dayLabelLayouts[i];

            dayLabelTextPain.setColor(getTextColor(dayLabelTextColor, itemIndex));
            labelLayout.draw(canvas);

            dayTextPaint.setColor(getTextColor(dayTextColor, itemIndex));

            int count = canvas.save();
            canvas.translate(0, labelHeight + dateLineOffset + labelPadding);

            if (dayDrawable != null) {
                dayDrawable.setBounds(backgroundRect);
                dayDrawable.setState(getItemDrawableState(itemIndex));
                dayDrawable.draw(canvas);
            }

            if (indicatorDrawable != null && dayIndicators.get(itemIndex, false)) {
                indicatorDrawable.setBounds(indicatorRect);
                indicatorDrawable.setState(getItemDrawableState(itemIndex));
                indicatorDrawable.draw(canvas);
            }

            layout.draw(canvas);

            canvas.restoreToCount(count);

            canvas.translate(dayWidth, 0);
        }

        canvas.restoreToCount(saveCount);

    }

    private LocalDate getRelativeFirstDay(int weekOffset) {
        weekOffset = getSelectedWeek() + weekOffset;
        return getFirstDay(weekOffset);
    }

    private LocalDate getFirstDay(int weekOffset) {
        return today.plusWeeks(weekOffset).with(TemporalAdjusters.previousOrSame(firstDayOfWeek));
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        textDir = getTextDirectionHeuristic();
    }

    private TextDirectionHeuristicCompat getTextDirectionHeuristic() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {

            return TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR;

        } else {

            // Always need to resolve layout direction first
            final boolean defaultIsRtl = (getLayoutDirection() == LAYOUT_DIRECTION_RTL);

            switch (getTextDirection()) {
                default:
                case TEXT_DIRECTION_FIRST_STRONG:
                    return (defaultIsRtl ? TextDirectionHeuristicsCompat.FIRSTSTRONG_RTL :
                            TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR);
                case TEXT_DIRECTION_ANY_RTL:
                    return TextDirectionHeuristicsCompat.ANYRTL_LTR;
                case TEXT_DIRECTION_LTR:
                    return TextDirectionHeuristicsCompat.LTR;
                case TEXT_DIRECTION_RTL:
                    return TextDirectionHeuristicsCompat.RTL;
                case TEXT_DIRECTION_LOCALE:
                    return TextDirectionHeuristicsCompat.LOCALE;
            }
        }
    }

    private void remakeLayout() {

        if (getWidth() > 0)  {

            LocalDate day = getRelativeFirstDay(-1);

            for (int i = 0; i < layouts.length; i++) {

                String dayText = String.valueOf(day.getDayOfMonth());
                if (layouts[i] == null) {
                    layouts[i] = BoringLayout.make(dayText, dayTextPaint, dayWidth,
                            Layout.Alignment.ALIGN_CENTER, 1f, 1f, dayMetrics, false, ellipsize,
                            dayWidth);
                } else {
                    layouts[i].replaceOrMake(dayText, dayTextPaint, dayWidth,
                            Layout.Alignment.ALIGN_CENTER, 1f, 1f, dayMetrics, false, ellipsize,
                            dayWidth);
                }

                day = day.plusDays(1);
            }

            DayOfWeek dayOfWeek = firstDayOfWeek; // first index is 1
            for (int i = 0; i < dayLabelLayouts.length; i++) {

                CharSequence name;
                if (labelNames == null) {
                    name = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault());
                } else {
                    int index = dayOfWeek.getValue() - 1;
                    name = labelNames[index];
                }


                if (dayLabelLayouts[i] == null) {
                    dayLabelLayouts[i] = BoringLayout.make(name, dayLabelTextPain, dayWidth,
                            Layout.Alignment.ALIGN_CENTER, 1f, 1f, dayLabelMetrics, false, ellipsize,
                            dayWidth);
                } else {
                    dayLabelLayouts[i].replaceOrMake(name, dayLabelTextPain, dayWidth,
                            Layout.Alignment.ALIGN_CENTER, 1f, 1f, dayLabelMetrics, false, ellipsize,
                            dayWidth);
                }

                dayOfWeek = dayOfWeek.plus(1);

            }

        }

    }

    /**
     * Calculates text color for specified item based on its position and state.
     *
     * @param item Index of item to get text color for
     * @return Item text color
     */
    private int getTextColor(ColorStateList color, int item) {

        List<Integer> states = new ArrayList<>();

        states.add(android.R.attr.state_enabled);

        if (isItemaPressed(item)) {
            states.add(android.R.attr.state_pressed);
        }

        if (isItemSelected(item)) {
            states.add(android.R.attr.state_selected);
        }

        int[] finalState = new int[states.size()];
        if (states.size() > 0) {
            for (int i = 0; i < states.size(); i++) {
                finalState[i] = states.get(i);
            }
        }

        return color.getColorForState(finalState, color.getDefaultColor());

    }

    private int[] getItemDrawableState(int item) {

        if (isItemSelected(item)) {
            return new int[] { android.R.attr.state_selected, android.R.attr.state_enabled };
        } else if (isItemaPressed(item)) {
            return new int[] { android.R.attr.state_pressed, android.R.attr.state_enabled };
        } else {
            return new int[] { android.R.attr.state_enabled };
        }

    }

    private boolean isItemaPressed(int item) {
        return item == pressedDay;
    }

    private boolean isItemSelected(int item) {
        return item == selectedDay;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        calculateItemSize(w, h);
        calculateBackgroundRect();
        calculateIndicatorRect();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(!isEnabled()) {
            return false;
        }

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_MOVE:

                float currentMoveX = event.getX();

                int deltaMoveX = (int) (lastDownEventX - currentMoveX);

                if(scrollingX || Math.abs(deltaMoveX) > touchSlop) {

                    if(!scrollingX) {
                        deltaMoveX = 0;
                        pressedDay = Integer.MIN_VALUE;
                        scrollingX = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                        scrollPositionStart = getScrollX();
                    }

                    scrollBy(deltaMoveX, 0);

                    lastDownEventX = currentMoveX;
                    invalidate();

                }

                break;
            case MotionEvent.ACTION_DOWN:

                if(!adjustScrollerX.isFinished()) {
                    adjustScrollerX.forceFinished(true);
                } else if(!flingScrollerX.isFinished()) {
                    flingScrollerX.forceFinished(true);
                } else {
                    scrollingX = false;
                }

                lastDownEventX = event.getX();

                if(!scrollingX) {
                    pressedDay = getDayPositionFromTouch(event.getX());
                }
                invalidate();

                break;
            case MotionEvent.ACTION_UP:

                VelocityTracker velocityTracker = this.velocityTracker;
                velocityTracker.computeCurrentVelocity(500, maximumFlingVelocity);
                int initialVelocityX = (int) velocityTracker.getXVelocity();

                if(scrollingX && Math.abs(initialVelocityX) > minimumFlingVelocity) {
                    flingX(initialVelocityX);
                } else {
                    float positionX = event.getX();
                    if(!scrollingX) {
                        int itemPos = getDayPositionFromTouch(positionX);
                        selectDay(itemPos);
                    } else if(scrollingX) {
                        finishScrolling();
                    }
                }

                this.velocityTracker.recycle();
                this.velocityTracker = null;

            case MotionEvent.ACTION_CANCEL:
                pressedDay = Integer.MIN_VALUE;
                invalidate();
                break;
        }

        return true;
    }

    public void selectDay(@NonNull LocalDate date) {
        int day = getDayForDate(date);
        selectDay(day);
    }

    private void selectDay(final int day) {

        if (selectedDay != day) {

            selectedDay = day;

            // post to the UI Thread to avoid potential interference with the OpenGL Thread
            if (onDateSelected != null) {
                final LocalDate date = firstDay.plusDays(day);
                post(new Runnable() {
                    @Override
                    public void run() {
                        onDateSelected.onDateSelected(date);
                    }
                });
            }

            invalidate();

        }

        int week = selectedDay / 7;
        if (selectedDay < 0 && selectedDay % 7 != 0) {
            week -= 1;
        }

        if (week != selectedWeek) {
            adjustToNearestWeekX(week);
        }

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (!isEnabled()) {
            return super.onKeyDown(keyCode, event);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
//                selectDay();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                smoothScrollBy(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                smoothScrollBy(1);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }

    }

//    @Override
//    protected boolean dispatchHoverEvent(MotionEvent event) {
//
//        if (mTouchHelper.dispatchHoverEvent(event)) {
//            return true;
//        }
//
//        return super.dispatchHoverEvent(event);
//    }

    @Override
    public void computeScroll() {
        computeScrollX();
    }

    @Override
    public void getFocusedRect(Rect r) {
        super.getFocusedRect(r); // TODO this should only be current item
    }

    public void setOnWeekChangedListener(OnWeekChanged onWeekChanged) {
        this.onWeekChanged = onWeekChanged;
    }

    public void setOnDateSelectedListener(OnDateSelected onDateSelected) {
        this.onDateSelected = onDateSelected;
    }

    public int getSelectedWeek() {
        int x = getScrollX();
        return getWeekPositionFromCoordinates(x);
    }

    public void scrollToWeek(int index) {
        selectedWeek = index;
        scrollToItem(index);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {

        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        scrollToWeek(ss.mSelItem);


    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        SavedState savedState = new SavedState(superState);
        savedState.mSelItem = selectedWeek;

        return savedState;

    }

    public TextUtils.TruncateAt getEllipsize() {
        return ellipsize;
    }

    public void setEllipsize(TextUtils.TruncateAt ellipsize) {
        if (this.ellipsize != ellipsize) {
            this.ellipsize = ellipsize;

            remakeLayout();
            invalidate();
        }
    }

    public void setDateIndicator(@NonNull LocalDate date, boolean enabled) {
        int itemIndex = getDayForDate(date);
        if (enabled) {
            dayIndicators.put(itemIndex, true);
        } else {
            dayIndicators.delete(itemIndex);
        }
    }

    private int getDayForDate(@NonNull LocalDate date) {
        return firstDay.until(date).getDays();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged(); //TODO
    }

    @Override protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);

        if (getWeekPositionFromCoordinates(l) != getWeekPositionFromCoordinates(oldl)) {
            remakeLayout();
        }
    }

    private int getDayPositionFromTouch(float x) {
        int weekPositionFromTouch = getWeekPositionFromTouch(x);
        int position = weekPositionFromTouch * 7 + getRelativeDayPositionFromTouch(x);
        if (weekPositionFromTouch < 0) {
            position += 6;
        }
        return position;
    }

    private int getRelativeDayPositionFromTouch(float x) {
        return getRelativeDayPositionFromCoordinates((int) (getScrollX() + x));
    }

    private int getWeekPositionFromTouch(float x) {
        return getWeekPositionFromCoordinates((int) (getScrollX() - (weekWidth + dividerSize) * .5f + x));
    }

    /**
     * Returns day of the week for passed coordinates.
     */
    private int getRelativeDayPositionFromCoordinates(int x) {
        float relativePosition = x % (weekWidth + dividerSize);
        return (int) (relativePosition / dayWidth);
    }

    private void computeScrollX() {
        OverScroller scroller = flingScrollerX;
        if(scroller.isFinished()) {
            scroller = adjustScrollerX;
            if(scroller.isFinished()) {
                return;
            }
        }

        if(scroller.computeScrollOffset()) {

            int currentScrollerX = scroller.getCurrX();
            if(previousScrollerX == Integer.MIN_VALUE) {
                previousScrollerX = scroller.getStartX();
            }

            scrollBy(currentScrollerX - previousScrollerX, 0);
            previousScrollerX = currentScrollerX;

            if(scroller.isFinished()) {
                onScrollerFinishedX(scroller);
            }

            postInvalidate();
        }
    }

    private void flingX(int velocityX) {

        int signum= Integer.signum(velocityX);

        int currentWeekPosition = getWeekPositionFromCoordinates(scrollPositionStart);
        float weekWidth = this.weekWidth + dividerSize;

        int finalPosition;
        switch (signum) {
            case -1:
                finalPosition = (int) weekWidth * (currentWeekPosition + 1);
                break;
            default:
            case 0:
                finalPosition = (int) weekWidth * (currentWeekPosition);
                break;
            case 1:
                finalPosition = (int) weekWidth * (currentWeekPosition - 1);
                break;
        }

        int dx = finalPosition - getScrollX();

        previousScrollerX = Integer.MIN_VALUE;
        flingScrollerX.startScroll(getScrollX(), getScrollY(), dx, 0);

        invalidate();
    }

    private void adjustToNearestWeekX() {

        int x = getScrollX();
        int week = Math.round(x / (weekWidth + dividerSize * 1f));
        adjustToNearestWeekX(week);

    }

    private void adjustToNearestWeekX(int week) {

        int x = getScrollX();

        if (selectedWeek != week) {
            selectedWeek = week;
            notifyWeekChange();
        }

        int weekPosition = (weekWidth + (int) dividerSize) * week;

        int deltaX = weekPosition - x;

        previousScrollerX = Integer.MIN_VALUE;
        adjustScrollerX.startScroll(x, 0, deltaX, 0, SELECTOR_ADJUSTMENT_DURATION_MILLIS);
        invalidate();
    }

    private void calculateItemSize(int w, int h) {

        int items = 1;
        int totalPadding = ((int) dividerSize * (items - 1));
        weekWidth = (w - totalPadding) / items;
        dayWidth = weekWidth / 7;

        scrollToItem(selectedWeek);

        buildFontMetrics();
        buildLabelFontMetrics();
        remakeLayout();

    }

    private void calculateBackgroundRect() {

        if (dayDrawable != null) {
            float circleRadius = dayWidth / 3;
            int centerX = layouts[0].getWidth() / 2;
            int centerY = layouts[0].getHeight() / 2;

            backgroundRect = new Rect((int) (centerX - circleRadius), (int) (centerY - circleRadius),
                    (int) (centerX + circleRadius), (int) (centerY + circleRadius));
        } else {
            backgroundRect = null;
        }

    }

    private void calculateIndicatorRect() {

        if (indicatorDrawable != null) {

            float circleRadius = dayWidth / 3;
            int centerX = layouts[0].getWidth() / 2;
            int centerY = layouts[0].getHeight() / 2;

            int indicatorDotWidth = indicatorDrawable.getIntrinsicWidth();
            int indicatorDotHeight = indicatorDrawable.getIntrinsicHeight();
            indicatorRect = new Rect(centerX - indicatorDotWidth / 2,
                    (int) (centerY + circleRadius - indicatorDotHeight),
                    centerX + indicatorDotWidth / 2, (int) (centerY + circleRadius));

        } else {
            indicatorRect = null;
        }

    }

    private void onScrollerFinishedX(OverScroller scroller) {
        if(scroller == flingScrollerX) {
            finishScrolling();
        }
    }

    private void finishScrolling() {

        adjustToNearestWeekX();
        scrollingX = false;
    }

    private int getPositionOnScreen(float x) {
        return (int) (x / (weekWidth + dividerSize));
    }

    private void smoothScrollBy(int i) {
        int deltaMoveX = (weekWidth + (int) dividerSize) * i;

        previousScrollerX = Integer.MIN_VALUE;
        flingScrollerX.startScroll(getScrollX(), 0, deltaMoveX, 0);
        invalidate();
    }

    /**
     * Sets text size for items
     * @param size New item text size in px.
     */
    private void setTextSize(float size) {
        if(size != dayTextPaint.getTextSize()) {
            dayTextPaint.setTextSize(size);

            buildFontMetrics();
            requestLayout();
            invalidate();
        }
    }

    private void setLabelTextSize(float size) {
        if (size != dayLabelTextPain.getTextSize()) {
            dayLabelTextPain.setTextSize(size);

            buildLabelFontMetrics();
            remakeLayout();
            invalidate();
        }
    }

    /**
     * Calculates item from x coordinate position.
     * @param x Scroll position to calculate.
     * @return Selected item from scrolling position in {param x}
     */
    private int getWeekPositionFromCoordinates(int x) {
        return Math.round(x / (weekWidth + dividerSize));
    }

    /**
     * Scrolls to specified item.
     * @param index Index of an item to scroll to
     */
    private void scrollToItem(int index) {
        scrollTo((weekWidth + (int) dividerSize) * index, 0);
    }

    private void notifyWeekChange() {

        // post to the UI Thread to avoid potential interference with the OpenGL Thread
        if (onWeekChanged != null) {
            post(new Runnable() {
                @Override
                public void run() {
                    LocalDate firstDay = getFirstDay(getWeekPositionFromCoordinates(getScrollX()));
                    onWeekChanged.onItemSelected(firstDay);
                }
            });
        }

    }

    private void buildFontMetrics() {
        FontMetricsInt fontMetricsInt = dayTextPaint.getFontMetricsInt();
        dayMetrics = WeekDatePicker.toBoringFontMetrics(fontMetricsInt, dayMetrics);
        dayMetrics.width = weekWidth;
    }

    private void buildLabelFontMetrics() {
        FontMetricsInt fontMetricsInt = dayLabelTextPain.getFontMetricsInt();
        dayLabelMetrics = WeekDatePicker.toBoringFontMetrics(fontMetricsInt, dayLabelMetrics);
        dayLabelMetrics.width = weekWidth;
    }

    public interface OnWeekChanged {

        void onItemSelected(LocalDate firstDay);

    }

    public interface OnDateSelected {

        void onDateSelected(LocalDate date);

    }

    private static BoringLayout.Metrics toBoringFontMetrics(FontMetricsInt metrics,
            @Nullable BoringLayout.Metrics fontMetrics) {

        if (fontMetrics == null) {
            fontMetrics = new BoringLayout.Metrics();
        }

        fontMetrics.ascent = metrics.ascent;
        fontMetrics.bottom = metrics.bottom;
        fontMetrics.descent = metrics.descent;
        fontMetrics.leading = metrics.leading;
        fontMetrics.top = metrics.top;
        return fontMetrics;
    }

    public static class SavedState extends BaseSavedState {

        private int mSelItem;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            mSelItem = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);

            dest.writeInt(mSelItem);
        }

        @Override
        public String toString() {
            return  "HorizontalPicker.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " selItem=" + mSelItem
                    + "}";
        }

        @SuppressWarnings("hiding")
        public static final Creator<SavedState> CREATOR
                = new Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

}
