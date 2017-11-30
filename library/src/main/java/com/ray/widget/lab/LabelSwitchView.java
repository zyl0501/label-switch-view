package com.ray.widget.lab;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Scroller;

public class LabelSwitchView extends View {

    public interface OnIndexChangeListener {
        void onIndexChange(int oldIndex, int newIndex);
    }

    private static final String TAG = "LabelSwitchView";

    private static final int INVALID_ID = -1;
    //adjust 时间
    private static final int SELECTOR_ADJUSTMENT_DURATION_MILLIS = 800;
    private static final int DEFAULT_NORMAL_COLOR = 0xFF888888;
    private static final int DEFAULT_SELECTED_COLOR = 0xFF0000FF;
    private static final int DEFAULT_TEXT_SIZE = 16;//sp
    private static final int DEFAULT_DRAG_OUT_DIST = 10;//dp
    private static final boolean DEFAULT_SHOW_RELATIVE = false;

    private int mElementWidth;
    private int textPaddingTop;
    private String[] labels;
    private int selectedIndex;
    private int normalColor, selectedColor;
    private int labelTextSize;
    //选中的thumb的背景id，当背景bitmap为空时，从这个id加载图片
    private int selectedBgResId;
    //选中的thumb的背景，用于防止每次都重新加载
    private Bitmap selectedBg;
    //当前thumb的x轴偏移量
    private int mCurrentOffsetX;
    private int mPreviousScrollerX;
    private boolean hasLabelChanged = false;
    private boolean showSwipeRelative;

    //大力拖动的时候，最大能拖出边界的距离
    private int dragOutDist;

    private int threshold;
    private float[] initPointLocation = new float[2];
    private boolean isDragging = false;
    private GestureDetector gestureDetector;
    private Scroller mAdjustScroller;

    private TextPaint textPaint;
    private Paint bgPatient;

    private OnIndexChangeListener mOnIndexChangeListener;

    public LabelSwitchView(Context context) {
        this(context, null);
    }

    public LabelSwitchView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LabelSwitchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        this.gestureDetector = new GestureDetector(getContext(),
                new TouchGestureListener());
        mAdjustScroller = new Scroller(getContext(), new DecelerateInterpolator(2.5f));
        textPaint = new TextPaint();
        bgPatient = new Paint();

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.LabelSwitchView);
        normalColor = typedArray.getColor(R.styleable.LabelSwitchView_normalColor, DEFAULT_NORMAL_COLOR);
        selectedColor = typedArray.getColor(R.styleable.LabelSwitchView_selectedColor, DEFAULT_SELECTED_COLOR);
        labelTextSize = typedArray.getDimensionPixelSize(R.styleable.LabelSwitchView_labelTextSize, DisplayUtil.sp2px(context, DEFAULT_TEXT_SIZE));
        selectedBgResId = typedArray.getResourceId(R.styleable.LabelSwitchView_selectedBackground, INVALID_ID);
        dragOutDist = typedArray.getDimensionPixelSize(R.styleable.LabelSwitchView_dragOutDist, DisplayUtil.dip2px(context, DEFAULT_DRAG_OUT_DIST));
        showSwipeRelative = typedArray.getBoolean(R.styleable.LabelSwitchView_showSwipeRelative, DEFAULT_SHOW_RELATIVE);
        CharSequence[] entries = typedArray.getTextArray(R.styleable.LabelSwitchView_labels);
        if (entries != null) {
            labels = new String[entries.length];
            for (int i = 0; i < entries.length; i++) {
                labels[i] = entries[i].toString();
            }
        }
        typedArray.recycle();

        initArgs();
        textPaint.setTextSize(labelTextSize);
    }

    private void initArgs() {
        selectedIndex = 0;
        mCurrentOffsetX = 0;
        mPreviousScrollerX = 0;
        threshold = DisplayUtil.dip2px(getContext(), 5);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (labels == null) {
            return;
        }
        drawLabelText(canvas);
        drawSelectedBg(canvas);
        if (showSwipeRelative) {
            drawRelativeText(canvas);
        }
        drawSelectedText(canvas);
    }

    private void drawLabelText(Canvas canvas) {
        int labelWidth = getElementWidth();
        float paddingTop = getTextPaddingTop();
        for (int i = 0; i < labels.length; i++) {
            textPaint.setColor(normalColor);
            String text = labels[i];
            float paddingLeft = labelWidth * i + (labelWidth - textPaint.measureText(text)) / 2 + getPaddingLeft();
            paddingLeft = Math.max(0, paddingLeft);
            canvas.drawText(text, paddingLeft, paddingTop, textPaint);
        }
    }

    private void drawSelectedBg(Canvas canvas) {
        if (selectedBgResId == INVALID_ID && selectedBg == null) {
            return;
        }
        if (selectedBg == null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            options.outWidth = getElementWidth();
            options.outHeight = getHeight();
            selectedBg = BitmapFactory.decodeResource(getResources(), selectedBgResId, options);
            if (selectedBg == null) {
                Drawable drawable = getResources().getDrawable(selectedBgResId);
                selectedBg = Utils.drawableToBitmap(drawable, getElementWidth(), getHeight() - getPaddingTop() - getPaddingBottom());
            }
        }
        canvas.drawBitmap(selectedBg, mCurrentOffsetX + getPaddingLeft(), getPaddingTop(), bgPatient);
    }

    private void drawSelectedText(Canvas canvas) {
        String selectedText = labels[selectedIndex];
        float paddingTop = getTextPaddingTop();
        float paddingLeft = mCurrentOffsetX + (getElementWidth() - textPaint.measureText(selectedText)) / 2 + getPaddingLeft();
        textPaint.setColor(selectedColor);
        canvas.drawText(selectedText, paddingLeft, paddingTop, textPaint);
    }

    private void drawRelativeText(Canvas canvas) {
        drawRelativeTextL(canvas);//绘制左侧relative text
        drawRelativeTextR(canvas);//绘制右侧relative text
    }

    private void drawRelativeTextL(Canvas canvas) {
        String relativeText;
        relativeText = getRelativeTextL();
        if (TextUtils.isEmpty(relativeText)) {
            return;
        }
        float relativeOff = getRelativeOffsetL(relativeText);
        if (relativeOff == -1) {
            return;
        }
        float paddingTop = getTextPaddingTop();
        float paddingLeft = mCurrentOffsetX + relativeOff + getPaddingLeft();
        textPaint.setColor(selectedColor);
        float textWidth = textPaint.measureText(relativeText);
        float temp = (getElementWidth() - textWidth) / 2;
        float offsetRate = Math.abs((temp - relativeOff) / textWidth);
        if (offsetRate == 0) {
            //offsetRate为0时，说明没有移动
            //此时relative text应该不显示
            return;
        }
        int alpha = (int) Math.abs((1 - offsetRate) * 255) / 2;
        textPaint.setAlpha(alpha);
        canvas.drawText(relativeText, paddingLeft, paddingTop, textPaint);
        textPaint.setAlpha(255);
    }

    private void drawRelativeTextR(Canvas canvas) {
        String relativeText;
        relativeText = getRelativeTextR();
        if (TextUtils.isEmpty(relativeText)) {
            return;
        }
        float relativeOff = getRelativeOffsetR(relativeText);
        if (relativeOff == -1) {
            return;
        }
        float paddingTop = getTextPaddingTop();
        float paddingLeft = mCurrentOffsetX + relativeOff + getPaddingLeft();
        textPaint.setColor(selectedColor);
        float textWidth = textPaint.measureText(relativeText);
        float temp = (getElementWidth() - textWidth) / 2;
        float offsetRate = Math.abs((temp - relativeOff) / textWidth);
        if (offsetRate == 0) {
            //offsetRate为0时，说明没有移动
            //此时relative text应该不显示
            return;
        }
        int alpha = (int) Math.abs((1 - offsetRate) * 255) / 2;
        textPaint.setAlpha(alpha);
        canvas.drawText(relativeText, paddingLeft, paddingTop, textPaint);
        textPaint.setAlpha(255);
    }

    private String getRelativeTextL() {
        float curIndexLeftEdge = selectedIndex * getElementWidth();
        if (curIndexLeftEdge > mCurrentOffsetX) {
            return labels[Math.max(0, selectedIndex - 1)];
        } else {
            return labels[selectedIndex];
        }
    }

    private String getRelativeTextR() {
        float curIndexLeftEdge = selectedIndex * getElementWidth();
        if (curIndexLeftEdge > mCurrentOffsetX) {
            return labels[selectedIndex];
        } else {
            return labels[Math.min(labels.length - 1, selectedIndex + 1)];
        }
    }

    private float getRelativeOffsetL(String relativeTxt) {
        if (TextUtils.isEmpty(relativeTxt)) {
            return -1;
        }
        boolean isOnCurLeft = false;
        //向左
        float curIndexLeftEdge = selectedIndex * getElementWidth();
        if (curIndexLeftEdge > mCurrentOffsetX) {
            if (selectedIndex <= 0) {
                return -1;
            }
            isOnCurLeft = true;
        }
        //向右
        if (curIndexLeftEdge < mCurrentOffsetX) {
            if (selectedIndex >= labels.length - 1) {
                return -1;
            }
        }
        float offset = mCurrentOffsetX - curIndexLeftEdge;
        float textWidth = textPaint.measureText(relativeTxt);
        float rate = Math.abs(offset / getElementWidth());
        if (isOnCurLeft) {
            return (getElementWidth() - textWidth) / 2 - textWidth * (1 - rate);
        } else {
            return (getElementWidth() - textWidth) / 2 - textWidth * rate;
        }
    }

    private float getRelativeOffsetR(String relativeTxt) {
        if (TextUtils.isEmpty(relativeTxt)) {
            return -1;
        }
        boolean isOnCurLeft = false;
        //向左
        float curIndexLeftEdge = selectedIndex * getElementWidth();
        if (curIndexLeftEdge > mCurrentOffsetX) {
            if (selectedIndex <= 0) {
                return -1;
            }
            isOnCurLeft = true;
        }
        //向右
        if (curIndexLeftEdge < mCurrentOffsetX) {
            if (selectedIndex >= labels.length - 1) {
                return -1;
            }
        }
        float offset = mCurrentOffsetX - curIndexLeftEdge;
        float textWidth = textPaint.measureText(relativeTxt);
        float rate = Math.abs(offset / getElementWidth());
        if (isOnCurLeft) {
            return (getElementWidth() - textWidth) / 2 + textWidth * rate;
        } else {
            return (getElementWidth() - textWidth) / 2 + textWidth * (1 - rate);
        }
    }

    @Override
    public void computeScroll() {
        Scroller scroller = mAdjustScroller;
        if (scroller.isFinished()) {
            return;
        }
        scroller.computeScrollOffset();
        int currentScrollerX = scroller.getCurrX();
        if (mPreviousScrollerX == 0) {
            mPreviousScrollerX = scroller.getStartX();
        }
        scrollBy(currentScrollerX - mPreviousScrollerX, 0);
        mPreviousScrollerX = currentScrollerX;
        if (scroller.isFinished()) {
//            onScrollerFinished(scroller);
        } else {
            invalidate();
        }
    }

    @Override
    public void scrollBy(int x, int y) {
        mCurrentOffsetX += x;
        mCurrentOffsetX = getValidOffsetX(mCurrentOffsetX);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
//                removeAllCallbacks();
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                initPointLocation[0] = event.getRawX();
                initPointLocation[1] = event.getRawY();
                return gestureDetector.onTouchEvent(event);
            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    return gestureDetector.onTouchEvent(event);
                }
                float x = event.getRawX();
                float y = event.getRawY();
                float dx = Math.abs(x - initPointLocation[0]);
                float dy = Math.abs(y - initPointLocation[1]);
                if (dx > threshold) {
                    isDragging = true;
                    return gestureDetector.onTouchEvent(event);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    onTouchEnd();
                }
                isDragging = false;
                initPointLocation[0] = 0f;
                initPointLocation[1] = 0f;
                return gestureDetector.onTouchEvent(event);
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    private void onTouchEnd() {
        ensureAdjustThumb();
    }

    private void onTouchMove(float x) {
        mCurrentOffsetX += x;
        mCurrentOffsetX = getValidOffsetX(mCurrentOffsetX);
        int touchIndex = (int) ((float) mCurrentOffsetX / getElementWidth() + 0.5f);
        int oldSelectedIndex = selectedIndex;
        selectedIndex = Math.max(0, Math.min(getLabelCount() - 1, touchIndex));
        if (oldSelectedIndex != selectedIndex) {
            notifyIndexChanged(oldSelectedIndex, selectedIndex);
        }
        invalidate();
    }

    private boolean ensureAdjustThumb() {
        int deltaX = getElementWidth() * selectedIndex - mCurrentOffsetX;
        if (deltaX != 0) {
            mPreviousScrollerX = 0;
            mAdjustScroller.startScroll(0, 0, deltaX, 0, SELECTOR_ADJUSTMENT_DURATION_MILLIS);
            invalidate();
            return true;
        }
        return false;
    }

    private boolean canScrollLeft() {
        return mCurrentOffsetX > 0;
    }

    private boolean canScrollRight() {
        return mCurrentOffsetX < getWidth() - getElementWidth() - getPaddingRight() - getPaddingLeft();
    }

    private int getValidOffsetX(int currentOffsetX) {
        return Math.min(Math.max(-dragOutDist, currentOffsetX), getWidth() - getElementWidth() - getPaddingLeft() - getPaddingRight() + dragOutDist);
    }

    private void notifyIndexChanged(int oldIndex, int newIndex) {
        if (mOnIndexChangeListener != null) {
            mOnIndexChangeListener.onIndexChange(oldIndex, newIndex);
        }
    }

    private int getElementWidth() {
        if (mElementWidth <= 0) {
            int labelCount = getLabelCount();
            int width = getWidth() - getPaddingLeft() - getPaddingRight();
            if (labelCount == 0) {
                mElementWidth = width;
            } else {
                mElementWidth = width / labelCount;
            }
        }
        return mElementWidth;
    }

    private int getTextPaddingTop() {
        if (textPaddingTop <= 0) {
            textPaddingTop = (int) ((getHeight() - getPaddingTop() - getPaddingBottom() + Math.abs(textPaint.getFontMetrics().ascent)) / 2 + getPaddingTop());
        }
        return textPaddingTop;
    }

    private int getLabelCount() {
        return labels == null ? 0 : labels.length;
    }

    private int getIndexByTouch(float x) {
        if (labels == null) {
            return 0;
        }
        return (int) Math.min(labels.length - 1, Math.max(0, x / getElementWidth()));
    }

    private void changeIndex(int newIndex, boolean notify) {
        int oldSelectedIndex = selectedIndex;
        selectedIndex = newIndex;
        ensureAdjustThumb();
        if (notify && oldSelectedIndex != selectedIndex) {
            notifyIndexChanged(oldSelectedIndex, selectedIndex);
        }
    }

    public void setSelectedIndex(int index) {
        changeIndex(index, true);
    }

    public void setLabels(String[] labels) {
        this.labels = labels;
    }

    public void setOnIndexChangeListener(OnIndexChangeListener onIndexChangeListener) {
        this.mOnIndexChangeListener = onIndexChangeListener;
    }

    private class TouchGestureListener extends GestureDetector.SimpleOnGestureListener {
        private boolean thumbInTouch = false;

        @Override
        public boolean onDown(MotionEvent e) {
            thumbInTouch = selectedIndex == getIndexByTouch(e.getX());
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            changeIndex(getIndexByTouch(e.getX()), true);
            return super.onSingleTapUp(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (!thumbInTouch) {
                return false;
            }
            if ((distanceX < 0 && canScrollRight())
                    || (distanceX > 0 && canScrollLeft())) {
                onTouchMove(-distanceX);
            }
            return super.onScroll(e1, e2, distanceX, distanceY);
        }
    }

}
