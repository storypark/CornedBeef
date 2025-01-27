package com.swiftkey.cornedbeef;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static com.swiftkey.cornedbeef.PunchHoleCoachMark.POSITION_CONTENT_ABOVE;
import static com.swiftkey.cornedbeef.PunchHoleCoachMark.POSITION_CONTENT_AUTOMATICALLY;
import static com.swiftkey.cornedbeef.PunchHoleCoachMark.PunchHoleCoachMarkBuilder;
import static com.swiftkey.cornedbeef.TestHelper.dismissCoachMark;
import static com.swiftkey.cornedbeef.TestHelper.moveTargetView;
import static com.swiftkey.cornedbeef.TestHelper.showCoachMark;
import static com.swiftkey.cornedbeef.TestHelper.waitUntilStatusBarHidden;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.test.core.app.ActivityScenario;

import com.swiftkey.cornedbeef.test.R;
import com.swiftkey.cornedbeef.test.SpamActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ExecutionException;

public class PunchHoleCoachMarkTestCase {

    private SpamActivity mActivity;
    private CoachMark mCoachMark;
    private View mAnchor;
    private View mTargetView;

    @Mock
    private View.OnClickListener mMockTargetClickListener;
    @Mock
    private View.OnClickListener mMockCoachMarkClickListener;

    private TextView mTextView;
    private static final String MESSAGE = "spam spam spam";
    private static final int PADDING = 10;
    private final int OVERLAY_COLOR = Color.BLACK;

    @Before
    public void setUp() throws ExecutionException, InterruptedException {
        MockitoAnnotations.initMocks(this);

        final ActivityScenario<SpamActivity> activityScenario = ActivityScenario.launch(SpamActivity.class);
        mActivity = TestHelper.getActivity(activityScenario);

        getInstrumentation().runOnMainSync(() -> {
            mActivity.getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
            mActivity.setContentView(R.layout.coach_mark_test_activity);
            mAnchor = mActivity.findViewById(R.id.coach_mark_test_layout_anchor);
            mTargetView = mActivity.findViewById(R.id.coach_mark_test_target);
        });
        getInstrumentation().waitForIdleSync();
        waitUntilStatusBarHidden(mActivity);

        mTextView = (TextView) LayoutInflater.from(mActivity)
                .inflate(R.layout.sample_customised_punchhole_content, null);
        mTextView.setText(MESSAGE);
        mTextView.setTextColor(Color.WHITE); // to make visual debugging easier
    }

    @After
    public void tearDown() {
        dismissCoachMark(getInstrumentation(), mCoachMark);
        mCoachMark = null;
        mAnchor = null;
        mTargetView = null;
        mActivity = null;

        mMockTargetClickListener = null;
        mMockCoachMarkClickListener = null;
    }

    @Test
    public void testViewsCreatedAndVisible_noAnimation() {
        setupCoachmark(false);
        checkViewsCreatedAndVisible();
    }

    @Test
    public void testViewsCreatedAndVisible_animation() {
        setupCoachmark(true);
        checkViewsCreatedAndVisible();
    }

    @Test
    public void testOverlayCorrectColor() {
        setupCoachmark(false);
        final View container = mCoachMark.getContentView();
        int color = ((ColorDrawable) container.getBackground()).getColor();
        assertEquals(OVERLAY_COLOR, color);
    }

    /**
     * Test the view creation and visibility.
     */
    private void checkViewsCreatedAndVisible() {
        showCoachMark(getInstrumentation(), mCoachMark);

        final View container = mCoachMark.getContentView();

        // Check the creation
        assertNotNull(mActivity);
        assertNotNull(mCoachMark);
        assertNotNull(container);
        assertNotNull(mTextView);

        // Check the visibility
        assertThat(mTextView, isCompletelyDisplayed());

        // Check the resources which passed by builder
        assertEquals(MESSAGE, mTextView.getText().toString());
    }

    @Test
    public void testTargetClick_noAnimation() {
        setupCoachmark(false);
        checkTargetClick();
    }

    @Test
    public void testTargetClick_animation() {
        setupCoachmark(true);
        checkTargetClick();
    }

    /**
     * Test the target's click listener
     */
    private void checkTargetClick() {
        showCoachMark(getInstrumentation(), mCoachMark);

        final View container = mCoachMark.getContentView();
        //noinspection unused
        final View target = container.findViewById(R.id.punch_hole_coach_mark_target);

        onView(is(mTargetView)).perform(click());

        // Touching textview should not propagated to global view.
        verify(mMockTargetClickListener, times(1)).onClick(container);
        verify(mMockCoachMarkClickListener, never()).onClick(container);
    }

    @Test
    public void testCoachMarkClick_noAnimation() {
        setupCoachmark(false);
        checkCoachMarkClick();
    }

    @Test
    public void testCoachMarkClick_animation() {
        setupCoachmark(true);
        checkCoachMarkClick();
    }

    /**
     * Test the coachmark's click listener
     */
    private void checkCoachMarkClick() {
        showCoachMark(getInstrumentation(), mCoachMark);

        final View container = mCoachMark.getContentView();
        onView(is(container)).inRoot(isPlatformPopup()).perform(click());

        verify(mMockTargetClickListener, never()).onClick(container);
        verify(mMockCoachMarkClickListener, times(1)).onClick(container);

        // Check whether global listener is working on tapping textview(message)
        // The container view should be checked whether it is clicked or not.
        // Because tap event propagated to parent view when target view don't have listener.
        onView(is(mTextView)).inRoot(isPlatformPopup()).perform(click());

        verify(mMockTargetClickListener, never()).onClick(any(View.class));
        verify(mMockCoachMarkClickListener, times(2)).onClick(any(View.class));
    }

    @Test
    public void testCircleIsOverlayed_noAnimation() {
        setupCoachmark(false);
        checkCircleIsOverlayed(false);
    }

    @Test
    public void testCircleIsOverlayed_animationCannotHappen() {
        setupCoachmark(true);
        // the target view is too small for the animation to happen so we just
        // centre the punch hole on the target view
        checkCircleIsOverlayed(false);
    }

    @Test
    public void testCircleIsOverlayed_animationCanHappen() {
        mTargetView = mActivity.findViewById(R.id.coach_mark_test_target_wide);
        setupCoachmark(true);
        // the target view is wide enough for the animation to happen
        checkCircleIsOverlayed(true);
    }

    /**
     * Test the location of punch hole to target view.
     */
    private void checkCircleIsOverlayed(final boolean animationShouldHappen) {
        showCoachMark(getInstrumentation(), mCoachMark);

        final PunchHoleView container = (PunchHoleView) mCoachMark.getContentView();

        final float diameterGap = mActivity.getResources()
                .getDimension(com.swiftkey.cornedbeef.R.dimen.punchhole_coach_mark_gap);

        // Get the coach mark and target view's coordinates of location on screen
        int[] containerScreenLoc = new int[2];
        container.getLocationOnScreen(containerScreenLoc);
        int[] targetScreenLoc = new int[2];
        mTargetView.getLocationOnScreen(targetScreenLoc);

        final int width = mTargetView.getWidth();
        final int height = mTargetView.getHeight();

        final int expectedPadding = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                PADDING,
                mActivity.getResources().getDisplayMetrics());
        final float expectedCircleRadius = ((height + diameterGap) / 2) + expectedPadding;

        final int expectedCircleStartOffsetX = animationShouldHappen
                ?  targetScreenLoc[0] + (int) expectedCircleRadius
                : width / 2;
        final int expectedCircleStartX = targetScreenLoc[0] + expectedCircleStartOffsetX - containerScreenLoc[0];
        final int expectedCircleStartY = targetScreenLoc[1] + (height / 2) - containerScreenLoc[1];

        // When the animation happens, the circle should not be in the start
        // position. Unfortunately we can't test that it ever was in the right
        // start position in this case as it starts moving when it's shown.
        assertEquals(
                animationShouldHappen,
                container.setCircle(expectedCircleStartX, expectedCircleStartY, expectedCircleRadius));
    }

    @Test
    public void testMessageLocatedAbove_noAnimation() {
        setupCoachmark(false);
        checkMessageLocatedAbove();
    }

    @Test
    public void testMessageLocatedAbove_animation() {
        setupCoachmark(true);
        checkMessageLocatedAbove();
    }

    @Test
    public void testMessageLocatedBelow_noAnimation() {
        setupCoachmark(false);
        checkMessageLocatedBelow();
    }

    @Test
    public void testMessageLocatedBelow_animation() {
        setupCoachmark(true);
        checkMessageLocatedBelow();
    }

    @Test
    public void testLayoutParamsAreSet() {
        final int contentWidth = 10;
        final int contentHeight = 20;
        mCoachMark = new PunchHoleCoachMarkBuilder(mActivity, mAnchor, mTextView)
                .setTargetView(mTargetView)
                .setContentLayoutParams(contentWidth, contentHeight, POSITION_CONTENT_ABOVE)
                .build();

        checkMessageLocatedAbove();

        assertEquals(contentWidth, mTextView.getLayoutParams().width);
        assertEquals(contentHeight, mTextView.getLayoutParams().height);
    }

    /**
     * Test that the coach mark text color is set correctly
     */
    @Test
    public void testSetTextColor() {
        final @ColorInt int color = Color.RED;

        mCoachMark = new PunchHoleCoachMarkBuilder(mActivity, mAnchor, mTextView)
                .setTargetView(mTargetView)
                .setHorizontalTranslationDuration(1000)
                .setOnTargetClickListener(mMockTargetClickListener)
                .setOnGlobalClickListener(mMockCoachMarkClickListener)
                .setOverlayColor(OVERLAY_COLOR)
                .setContentLayoutParams(MATCH_PARENT, MATCH_PARENT, POSITION_CONTENT_AUTOMATICALLY)
                .setPunchHolePadding(PADDING)
                .setTextColor(color)
                .build();

        showCoachMark(getInstrumentation(), mCoachMark);

        assertEquals(color, mTextView.getCurrentTextColor());
    }

    /**
     * Verify that setting the coach mark text color on a non-text coach mark throws exception
     */
    @Test(expected = IllegalStateException.class)
    public void testSetTextColorOnNonTextCoachMark() {
        mCoachMark = new PunchHoleCoachMarkBuilder(mActivity, mAnchor, new ImageView(mActivity))
                .setTargetView(mTargetView)
                .setHorizontalTranslationDuration(1000)
                .setOnTargetClickListener(mMockTargetClickListener)
                .setOnGlobalClickListener(mMockCoachMarkClickListener)
                .setOverlayColor(OVERLAY_COLOR)
                .setContentLayoutParams(MATCH_PARENT, MATCH_PARENT, POSITION_CONTENT_AUTOMATICALLY)
                .setPunchHolePadding(PADDING)
                .setTextColor(Color.RED)
                .build();
    }

    /**
     * Verify that a non-text coach mark is shown correctly
     */
    @Test
    public void testNonTextCoachMark() {
        final ImageView imageView = new ImageView(mActivity);
        imageView.setImageResource(com.swiftkey.cornedbeef.R.drawable.ic_pointy_mark_up);
        mCoachMark = new PunchHoleCoachMarkBuilder(mActivity, mAnchor, imageView)
                .setTargetView(mTargetView)
                .setHorizontalTranslationDuration(1000)
                .setOnTargetClickListener(mMockTargetClickListener)
                .setOnGlobalClickListener(mMockCoachMarkClickListener)
                .setOverlayColor(OVERLAY_COLOR)
                .setContentLayoutParams(MATCH_PARENT, MATCH_PARENT, POSITION_CONTENT_AUTOMATICALLY)
                .setPunchHolePadding(PADDING)
                .build();

        showCoachMark(getInstrumentation(), mCoachMark);

        assertTrue(mCoachMark.isShowing());

        final ViewGroup content = (ViewGroup) mCoachMark.getContentView();
        assertTrue(content.getChildAt(0) instanceof ImageView);
    }

    /**
     * Test that the message is shown below when target view located in top side.
     */
    private void checkMessageLocatedBelow() {
        showCoachMark(getInstrumentation(), mCoachMark);

        final PunchHoleView container = (PunchHoleView) mCoachMark.getContentView();

        moveTargetView(getInstrumentation(), mTargetView, 0, 0); // Move target view to top
        checkMessageIsOnTheCorrectSide(container, 1);
    }

    /**
     * Test that the message is shown above when target view located in bottom side.
     */
    private void checkMessageLocatedAbove() {
        showCoachMark(getInstrumentation(), mCoachMark);

        final PunchHoleView container = (PunchHoleView) mCoachMark.getContentView();

        // Move target view to bottom
        moveTargetView(
                getInstrumentation(),
                mTargetView,
                0,
                container.getHeight() - mTargetView.getHeight());

        checkMessageIsOnTheCorrectSide(container, -1);
    }

    private Rect getRectFromPositionOnScreen(final View view) {
        int[] xy = new int[2];
        view.getLocationOnScreen(xy);
        return new Rect(xy[0], xy[1], xy[0] + view.getWidth(), xy[1] + view.getHeight());
    }

    private void checkMessageIsOnTheCorrectSide(
            final View container, final int expectedSide) {

        // Get the coach mark and textview's coordinates of location on screen
        Rect containerCoords = getRectFromPositionOnScreen(container);
        Rect textViewCoords = getRectFromPositionOnScreen(mTextView);
        Rect targetCoords = getRectFromPositionOnScreen(mTargetView);

        Rect intersection = new Rect(textViewCoords);
        assertFalse(intersection.intersect(targetCoords));
        assertEquals(
                expectedSide,
                Integer.compare(textViewCoords.centerY(), containerCoords.centerY()));
    }

    private void setupCoachmark(final boolean animation) {
        mCoachMark = new PunchHoleCoachMarkBuilder(mActivity, mAnchor, mTextView)
                .setTargetView(mTargetView)
                .setHorizontalTranslationDuration(animation ? 1000 : 0)
                .setOnTargetClickListener(mMockTargetClickListener)
                .setOnGlobalClickListener(mMockCoachMarkClickListener)
                .setOverlayColor(OVERLAY_COLOR)
                .setContentLayoutParams(MATCH_PARENT, MATCH_PARENT, POSITION_CONTENT_AUTOMATICALLY)
                .setPunchHolePadding(PADDING)
                .build();
    }
}
