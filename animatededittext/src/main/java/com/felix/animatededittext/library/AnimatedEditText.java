package com.felix.animatededittext.library;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;

import java.lang.ref.WeakReference;

/**
 * @author Felix
 */
public class AnimatedEditText extends EditText {
    private int mStrokeWidth;
    private int mStrokeColor;
    private float unit = 12;
    private long mDuration;
    private float mLengthSearch;
    private float mAnimatedValue;
    private Paint mPaint;
    private Path mSearchPath;
    private Path mClosePath;
    private Path mDst;
    private PathMeasure mPathMeasure;
    private Rect mLineRect = new Rect();
    private State mCurrentState;
    private Handler mHandler = new CustomHandler(this);
    private boolean isOpen;
    private boolean isEditable;
    private ValueAnimator mOpenAnimator;
    private ValueAnimator mCloseAnimator;
    private ValueAnimator mVisibleAnimator;
    private ValueAnimator mInVisibleAnimator;
    private ValueAnimator.AnimatorUpdateListener mUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator valueAnimator) {
            mAnimatedValue = (float) valueAnimator.getAnimatedValue();
            invalidate();
        }
    };
    private Animator.AnimatorListener mAnimatorListener = new Animator.AnimatorListener() {
        @Override
        public void onAnimationEnd(Animator animator) {
            mHandler.sendEmptyMessage(0);
        }

        @Override
        public void onAnimationStart(Animator animator) {
        }

        @Override
        public void onAnimationCancel(Animator animator) {
        }

        @Override
        public void onAnimationRepeat(Animator animator) {
        }
    };

    public AnimatedEditText(Context context) {
        super(context);
    }

    public AnimatedEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray array = context.obtainStyledAttributes(R.styleable.AnimatedEditText);
        mStrokeColor = array.getColor(R.styleable.AnimatedEditText_strokeColor, 0xFFFFFFFF);
        mStrokeWidth = array.getDimensionPixelSize(R.styleable.AnimatedEditText_strokeWidth, 4);
        mDuration = array.getInteger(R.styleable.AnimatedEditText_duration, 1500);
        array.recycle();
        initPaint();
        initViewState();
        initAnimator();
        initListener();
    }

    private void initAnimator() {
        mOpenAnimator = ValueAnimator.ofFloat(0, 1).setDuration(mDuration);
        mOpenAnimator.setInterpolator(new OvershootInterpolator(2));
        mCloseAnimator = ValueAnimator.ofFloat(1, 0).setDuration(mDuration - 600);
        mCloseAnimator.setInterpolator(new DecelerateInterpolator());
        mVisibleAnimator = ValueAnimator.ofFloat(0, 1).setDuration(200);
        mInVisibleAnimator = ValueAnimator.ofFloat(1, 0).setDuration(200);
    }

    private void initListener() {
        mOpenAnimator.addUpdateListener(mUpdateListener);
        mOpenAnimator.addListener(mAnimatorListener);
        mCloseAnimator.addUpdateListener(mUpdateListener);
        mCloseAnimator.addListener(mAnimatorListener);
        mVisibleAnimator.addUpdateListener(mUpdateListener);
        mVisibleAnimator.addListener(mAnimatorListener);
        mInVisibleAnimator.addUpdateListener(mUpdateListener);
        mInVisibleAnimator.addListener(mAnimatorListener);
    }

    private void initPaint() {
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStrokeWidth(mStrokeWidth);
        mPaint.setColor(mStrokeColor);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    private void initViewState() {
        setPaddingRelative(24, 24, 24, 24);
        setClickable(true);
        mCurrentState = State.NORMAL;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        initPath();
    }

    private void initPath() {
        mSearchPath = new Path();
        mSearchPath.moveTo(-unit, -unit);
        float radius = (float) Math.hypot(unit, unit);
        mSearchPath.arcTo(new RectF(-radius - 2 * unit, -radius - 2 * unit, radius - 2 * unit, radius - 2 * unit),
                45, 359.999f, false);
        mSearchPath.lineTo(0, 0);
        mPathMeasure = new PathMeasure();
        mPathMeasure.setPath(mSearchPath, false);
        mLengthSearch = mPathMeasure.getLength();
        mSearchPath.lineTo(getPaddingLeft() + getPaddingRight() - getRight(), 0);
        mDst = new Path();
        mClosePath = new Path();
        mClosePath.moveTo(-radius - 2 * unit, -2 * unit);
        mClosePath.lineTo(radius - 2 * unit, -2 * unit);
        mClosePath.moveTo(radius - 2 * unit, -2 * unit);
        mClosePath.lineTo(-2 * unit, -radius - 2 * unit);
        mClosePath.moveTo(radius - 2 * unit, -2 * unit);
        mClosePath.lineTo(-2 * unit, radius - 2 * unit);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        getLineBounds(getLineCount() - 1, mLineRect);
        if (isEditable) {
            super.onDraw(canvas);
        }
        drawSearch(canvas);
    }

    private void drawSearch(Canvas canvas) {
        float lengthX = getRight() - getPaddingRight();
        canvas.translate(lengthX, mLineRect.bottom + 1);
        mDst.reset();
        mPathMeasure.setPath(mSearchPath, false);
        float start;
        float end;
        switch (mCurrentState) {
            case NORMAL:
                mPathMeasure.getSegment(0, mLengthSearch, mDst, true);
                canvas.drawPath(mDst, mPaint);
                break;
            case OPEN_OR_CLOSE:
                start = mAnimatedValue * (mLengthSearch + 4 * unit + 24);
                end = mLengthSearch + mPathMeasure.getLength() * mAnimatedValue;
                mPathMeasure.getSegment(start, end, mDst, true);
                canvas.drawPath(mDst, mPaint);
                break;
            case CANCELABLE:
                start = mLengthSearch + 4 * unit + 24;
                end = mLengthSearch + mPathMeasure.getLength();
                mPathMeasure.getSegment(start, end, mDst, true);
                canvas.drawPath(mDst, mPaint);
                mPaint.setAlpha((int) (255 * mAnimatedValue));
                canvas.drawPath(mClosePath, mPaint);
                mPaint.setAlpha(255);
                break;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (event.getX() > getRight() - 24 - getPaddingRight() - getPaddingLeft()) {
                    if (!isOpen) {
                        mCurrentState = State.OPEN_OR_CLOSE;
                        mOpenAnimator.start();
                    } else {
                        isEditable = false;
                        mInVisibleAnimator.start();
                    }
                }
        }
        return !isEditable || super.onTouchEvent(event);
    }

    private void initState() {
        setText(null);
    }

    private enum State {
        NORMAL,
        OPEN_OR_CLOSE,
        CANCELABLE

    }

    private static class CustomHandler extends Handler {

        private WeakReference<AnimatedEditText> mReference;

        private CustomHandler(AnimatedEditText view) {
            mReference = new WeakReference<>(view);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            AnimatedEditText v = mReference.get();
            if (v != null) {
                switch (v.mCurrentState) {
                    case OPEN_OR_CLOSE:
                        if (!v.isOpen) {
                            v.mCurrentState = State.CANCELABLE;
                            v.mVisibleAnimator.start();
                        } else {
                            v.mCurrentState = State.NORMAL;
                            v.isOpen = false;
                        }
                        break;
                    case CANCELABLE:
                        if (!v.isOpen) {
                            v.isOpen = true;
                            v.isEditable = true;
                            v.invalidate();
                        } else {
                            v.initState();
                            v.mCurrentState = State.OPEN_OR_CLOSE;
                            v.mCloseAnimator.start();
                        }
                        break;
                }
            }
        }

    }
}
