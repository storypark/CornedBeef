package com.swiftkey.cornedbeef;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.view.WindowManager;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.LayoutRes;
import androidx.annotation.Px;
import androidx.annotation.StyleRes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A CoachMark is a temporary popup that can be positioned above a {@link View}
 * to notify the user about a new feature, proposition or other information.
 * <p>
 * CoachMarks are dismissed in two ways:
 * 1) A pre-set timeout passed
 * 2) The {@link CoachMark#dismiss()} method is called
 * <p>
 * Coach marks can be very annoying to the user, SO PLEASE USE SPARINGLY!
 *
 * @author lachie
 */
public abstract class CoachMark {

    @IntDef({COACHMARK_PUNCHHOLE, COACHMARK_LAYERED, COACHMARK_HIGHLIGHT, COACHMARK_BUBBLE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface CoachmarkType {
    }

    public static final int COACHMARK_PUNCHHOLE = 0;
    public static final int COACHMARK_LAYERED = 1;
    public static final int COACHMARK_HIGHLIGHT = 2;
    public static final int COACHMARK_BUBBLE = 3;

    public static final int NO_ANIMATION = 0;

    /**
     * Interface used to allow the creator of a coach mark to run some code when the
     * coach mark is dismissed.
     */
    public interface OnDismissListener {
        /**
         * This method will be invoked when the coach mark is dismissed.
         */
        void onDismiss();
    }

    /**
     * Interface used to allow the creator of a coach mark to run some code when the
     * coach mark is shown.
     */
    public interface OnShowListener {
        /**
         * This method will be invoked when the coach mark is shown.
         */
        void onShow();
    }

    /**
     * Interface used to allow the creator of a coach mark to run some code when the
     * coach mark's given timeout is expired.
     */
    public interface OnTimeoutListener {
        /**
         * This method will be invoked when the coach mark's given timeout is expired.
         */
        void onTimeout();
    }

    protected final PopupWindow mPopup;
    protected final Context mContext;
    protected final View mTokenView;
    protected final View mAnchor;
    @Px protected final int mPadding;

    private final OnPreDrawListener mPreDrawListener;
    private final OnDismissListener mDismissListener;
    private final OnShowListener mShowListener;
    private final OnAttachStateChangeListener mOnAttachStateChangeListener;
    private final OnTimeoutListener mTimeoutListener;
    private final long mTimeoutInMs;
    private final boolean mShouldDismissOnAnchorDetach;
    protected final boolean mPopupFitsSystemWindows;

    private Runnable mTimeoutDismissRunnable;

    protected Rect mDisplayFrame;

    protected CoachMark(CoachMarkBuilder builder) {
        mAnchor = builder.anchor;
        mContext = builder.context;
        mTimeoutInMs = builder.timeout;
        mDismissListener = builder.dismissListener;
        mShowListener = builder.showListener;
        mTimeoutListener = builder.timeoutListener;
        mTokenView = builder.tokenView != null ? builder.tokenView : mAnchor;
        mPadding = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, builder.padding,
                mContext.getResources().getDisplayMetrics());
        mShouldDismissOnAnchorDetach = builder.shouldDismissOnAnchorDetach;
        mPopupFitsSystemWindows = builder.popupWindowFitToWindow;

        // Create the coach mark view
        View view = createContentView(builder.content, builder);

        // Create and initialise the PopupWindow
        mPopup = createNewPopupWindow(view);
        if (mPopupFitsSystemWindows) {
            mPopup.setWidth(WindowManager.LayoutParams.MATCH_PARENT);
            mPopup.setHeight(WindowManager.LayoutParams.MATCH_PARENT);
            mPopup.setFocusable(true); // full screen must be focusable
        }
        mPopup.setAnimationStyle(builder.animationStyle);
        mPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        if (builder.popupWindowBackgroundColor != null) {
            mPopup.setBackgroundDrawable(new ColorDrawable(builder.popupWindowBackgroundColor));
        } else {
            mPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        mPreDrawListener = new CoachMarkPreDrawListener();
        mOnAttachStateChangeListener = new CoachMarkOnAttachStateChangeListener();
    }

    /**
     * Create the coach mark view
     */
    protected abstract View createContentView(View content, CoachMarkBuilder builder);

    /**
     * Create and initialise a new {@link PopupWindow}
     */
    protected abstract PopupWindow createNewPopupWindow(View contentView);

    /**
     * Get the dimensions of the anchor view
     */
    protected abstract CoachMarkDimens<Integer> getAnchorDimens();

    /**
     * Get the current dimensions of the popup window
     */
    protected abstract CoachMarkDimens<Integer> getPopupDimens(CoachMarkDimens<Integer> anchorDimens);

    /**
     * Perform any necessary updates to the view when popupDimens or anchorDimens have changed
     */
    protected abstract void updateView(CoachMarkDimens<Integer> popupDimens, CoachMarkDimens<Integer> anchorDimens);

    /**
     * Show the coach mark and start listening for changes to the anchor view
     */
    public void show() {
        // It is assumed that the displayFrame will not change for as long as
        // the coach mark is visible - otherwise, the positioning may be off
        mDisplayFrame = getDisplayFrame(mAnchor);
        final CoachMarkDimens<Integer> anchorDimens = getAnchorDimens();

        final CoachMarkDimens<Integer> popupDimens = getPopupDimens(anchorDimens);
        updateView(popupDimens, anchorDimens);

        // Dismiss coach mark after the timeout has passed if it is greater than 0.
        if (mTimeoutInMs > 0) {
            mTimeoutDismissRunnable = () -> {
                if (mPopup.isShowing()) {
                    if (mTimeoutListener != null) {
                        mTimeoutListener.onTimeout();
                    }
                    try {
                        dismiss();
                    } catch (IllegalArgumentException e) {
                        // Closes #19 - popup has already been removed outside of CornedBeef's
                        // control
                    }
                }
            };
            getContentView().postDelayed(mTimeoutDismissRunnable, mTimeoutInMs);
        }

        if (mPopupFitsSystemWindows) {
            mPopup.showAtLocation(mTokenView, Gravity.NO_GRAVITY, 0, 0);
        } else {
            mPopup.showAtLocation(mTokenView, Gravity.NO_GRAVITY, popupDimens.x, popupDimens.y);
            mPopup.setWidth(popupDimens.width);
        }

        mAnchor.getViewTreeObserver().addOnPreDrawListener(mPreDrawListener);
        if (mShowListener != null) {
            mShowListener.onShow();
        }
        mAnchor.addOnAttachStateChangeListener(mOnAttachStateChangeListener);
    }

    /**
     * Dismiss the coach mark and stop listening for changes to the anchor view
     */
    public void dismiss() {
        mAnchor.destroyDrawingCache();
        mAnchor.removeOnAttachStateChangeListener(mOnAttachStateChangeListener);
        mAnchor.getViewTreeObserver().removeOnPreDrawListener(mPreDrawListener);
        mPopup.getContentView().removeCallbacks(mTimeoutDismissRunnable);

        mPopup.dismiss();

        if (mDismissListener != null) {
            mDismissListener.onDismiss();
        }
    }

    /**
     * Exposes the {@link PopupWindow#getContentView()} method of {@link CoachMark#mPopup}
     */
    public View getContentView() {
        return mPopup.getContentView();
    }

    /**
     * Exposes the {@link PopupWindow#isShowing()} method of {@link CoachMark#mPopup}
     */
    public boolean isShowing() {
        return mPopup.isShowing();
    }

    /**
     * Exposes the {@link PopupWindow#setFocusable(boolean)} method of {@link CoachMark#mPopup}
     * <p>
     * Set whether the coach mark is focusable or not
     * If we set the coach mark focusable, the behaviour changes as follows:
     * For Explore-by-Touch mode
     * 1. Outside of this coach mark becomes un-touchable
     * 2. The coach mark can be dismissed by pressing the hardware back button
     * 3. Focus can only be traversed through elements inside the coach mark area
     * For Non Explore-by-Touch mode
     * 1. Touching outside the coach mark will dismiss it
     * This doesn't apply to HighlightCoachMarks, as they always have touchable set to false
     * 2. The coach mark can be dismissed by pressing the hardware back button
     *
     * @param focusable whether or not this coach mark can be focused
     */
    public void setFocusable(boolean focusable) {
        mPopup.setFocusable(focusable);
    }

    /**
     * Exposes the {@link PopupWindow#isFocusable()} method of {@link CoachMark#mPopup}
     */
    public boolean isFocusable() {
        return mPopup.isFocusable();
    }

    /**
     * Get the visible display size of the window this view is attached to
     */
    private static Rect getDisplayFrame(View view) {
        final Rect displayFrame = new Rect();
        view.getWindowVisibleDisplayFrame(displayFrame);
        return displayFrame;
    }

    /**
     * Listener which is used to update the position of the coach mark when the
     * position of the anchor view is about to change
     */
    private class CoachMarkPreDrawListener implements OnPreDrawListener {

        @Override
        public boolean onPreDraw() {
            if (mAnchor != null && mAnchor.isShown()) {
                CoachMarkDimens<Integer> anchorDimens = getAnchorDimens();
                CoachMarkDimens<Integer> popupDimens = getPopupDimens(anchorDimens);
                updateView(popupDimens, anchorDimens);
                if (!mPopupFitsSystemWindows) {
                    mPopup.update(popupDimens.x, popupDimens.y, popupDimens.width, popupDimens.height);
                }
            } else {
                dismiss();
            }
            return true;
        }
    }

    /**
     * Listener may be used to dismiss the coach mark when its anchor detaches
     */
    protected class CoachMarkOnAttachStateChangeListener implements OnAttachStateChangeListener {

        @Override
        public void onViewAttachedToWindow(View view) {
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            if (mShouldDismissOnAnchorDetach) {
                dismiss();
            }
        }
    }

    /**
     * Listener may be used to dismiss the coach mark when it is touched
     */
    protected class CoachMarkOnTouchListener implements OnTouchListener {

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_UP:
                    dismiss();
                case MotionEvent.ACTION_DOWN:
                    return true;
                default:
                    return false;
            }
        }
    }

    /**
     * An {@link android.view.View.OnClickListener} which wraps an
     * existing listener with a call to {@link CoachMark#dismiss()}
     *
     * @author lachie
     */
    protected class CoachMarkOnClickListener implements View.OnClickListener {

        private final View.OnClickListener mListener;

        public CoachMarkOnClickListener(View.OnClickListener listener) {
            mListener = listener;
        }

        @Override
        public void onClick(View v) {
            dismiss();

            if (mListener != null) {
                mListener.onClick(v);
            }
        }
    }

    public static class CoachMarkDimens<T extends Number> {
        public final T width;
        public final T height;
        public final T x;
        public final T y;

        public CoachMarkDimens(T x, T y, T width, T height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public Point getPos() {
            return new Point(x.intValue(), y.intValue());
        }
    }

    public abstract static class CoachMarkBuilder {

        // Required parameters
        protected Context context;
        protected View anchor;
        protected View content;

        // Optional parameters with default values
        protected long timeout = 10000;
        protected OnDismissListener dismissListener;
        protected int padding = 0;
        protected int animationStyle = R.style.CoachMarkAnimation;
        protected View tokenView;
        protected OnShowListener showListener;
        protected OnTimeoutListener timeoutListener;
        protected boolean shouldDismissOnAnchorDetach = true;
        private Integer popupWindowBackgroundColor;
        private boolean popupWindowFitToWindow;

        public CoachMarkBuilder(Context context, View anchor, String message) {
            this(context, anchor, LayoutInflater.from(context).inflate(R.layout.coach_mark_text, null, false));
            ((TextView) content).setTextColor(Color.WHITE);
            ((TextView) content).setText(message);
        }

        public CoachMarkBuilder(Context context, View anchor, @LayoutRes int contentResId) {
            this(context, anchor, LayoutInflater.from(context).inflate(contentResId, null));
        }

        public CoachMarkBuilder(Context context, View anchor, View content) {
            this.context = context;
            this.anchor = anchor;
            this.content = content;
        }

        /**
         * If the desired anchor view does not contain a valid window token then
         * the token of an alternative view may be used to display the coach mark
         *
         * @param tokenView the view who's window token should be used
         */
        public CoachMarkBuilder setTokenView(View tokenView) {
            this.tokenView = tokenView;
            return this;
        }

        /**
         * Set the period of time after which the coach mark should be
         * automatically dismissed
         *
         * @param timeoutInMs the time in milliseconds after which to dismiss the coach
         *                    mark (defaults to 10 seconds)
         */
        public CoachMarkBuilder setTimeout(long timeoutInMs) {
            this.timeout = timeoutInMs;
            return this;
        }

        /**
         * Set how much padding there should be between the left and right edges
         * of the coach mark and the screen
         *
         * @param padding the amount of left/right padding in density-independent pixels (dip)
         */
        public CoachMarkBuilder setPadding(int padding) {
            this.padding = padding;
            return this;
        }

        /**
         * Set an {@link CoachMark.OnDismissListener} to be called when the
         * coach mark is dismissed
         *
         * @param listener the onDismissListener
         */
        public CoachMarkBuilder setOnDismissListener(OnDismissListener listener) {
            this.dismissListener = listener;
            return this;
        }

        /**
         * Set an {@link CoachMark.OnTimeoutListener} to be called when the
         * coach mark's display timeout has been expired
         * <p>
         * This listener will be called before the coach mark dismissed
         *
         * @param listener the timeout listener
         */
        public CoachMarkBuilder setOnTimeoutListener(OnTimeoutListener listener) {
            this.timeoutListener = listener;
            return this;
        }

        /**
         * Set which animation will be used when displaying/hiding the coach mark
         *
         * @param animationStyle the resource ID of the Style to be used for showing and hiding the coach mark
         */
        public CoachMarkBuilder setAnimation(@StyleRes int animationStyle) {
            this.animationStyle = animationStyle;
            return this;
        }

        /**
         * Set an {@link CoachMark.OnShowListener} to be called when the
         * coach mark is shown
         *
         * @param listener
         */
        public CoachMarkBuilder setOnShowListener(OnShowListener listener) {
            this.showListener = listener;
            return this;
        }

        /**
         * Set whether the coach mark should dismiss itself when the anchor view detaches
         *
         * @param shouldDismissOnAnchorDetach whether or not to dismiss on anchor detach
         */
        public CoachMarkBuilder setDismissOnAnchorDetach(boolean shouldDismissOnAnchorDetach) {
            this.shouldDismissOnAnchorDetach = shouldDismissOnAnchorDetach;
            return this;
        }

        public CoachMarkBuilder setPopupWindowBackgroundColor(@ColorInt int popupWindowBackgroundColor) {
            this.popupWindowBackgroundColor = popupWindowBackgroundColor;
            return this;
        }

        public CoachMarkBuilder setPopupWindowFitsSystemWindows(boolean popupWindowFitToWindow) {
            this.popupWindowFitToWindow = popupWindowFitToWindow;
            return this;
        }

        /**
         * Set the coach mark's text color.
         *
         * @param textColor new text color
         */
        public CoachMarkBuilder setTextColor(@ColorInt int textColor) {
            if (this.content instanceof TextView) {
                ((TextView) this.content).setTextColor(textColor);
                return this;
            } else {
                throw new IllegalStateException(
                        "Can't set a text color in a CoachMark whose content is not a TextView");
            }
        }

        public abstract CoachMark build();
    }
}
