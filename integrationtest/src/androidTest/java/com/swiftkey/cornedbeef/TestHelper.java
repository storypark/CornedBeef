package com.swiftkey.cornedbeef;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.core.internal.deps.guava.util.concurrent.SettableFuture;

import java.util.concurrent.ExecutionException;

public final class TestHelper {

    private static final int TIMEOUT = 1000;
    private static final int SLEEP = 100;

    public TestHelper() {
    }

    /**
     * Call {@link CoachMark#show()} on the given {@link CoachMark}
     */
    public static void showCoachMark(final Instrumentation instrumentation, final CoachMark coachMark) {
        instrumentation.runOnMainSync(() -> {
            coachMark.show();
        });
        instrumentation.waitForIdleSync();
        try {
            // Don't trust that the waitForIdleSync actually waited long enough to be ready for touch events
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Call {@link CoachMark#dismiss()}
     * on the given {@link CoachMark}
     */
    public static void dismissCoachMark(final Instrumentation instrumentation, final CoachMark coachMark) {
        instrumentation.runOnMainSync(() -> {
            if (coachMark != null) {
                coachMark.dismiss();
            }
        });
        instrumentation.waitForIdleSync();
    }

    /**
     * Wait until the status bar is fully hidden
     */
    @SuppressWarnings("BusyWait")
    public static void waitUntilStatusBarHidden(final Activity activity) {
        final Rect rect = new Rect();
        final long startTime = SystemClock.uptimeMillis();
        do {
            try {
                activity.getWindow().getDecorView()
                        .getWindowVisibleDisplayFrame(rect);
                Thread.sleep(SLEEP);
            } catch (InterruptedException e) {
                break;
            }
        } while (SystemClock.uptimeMillis() - startTime < TIMEOUT && rect.top != 0);
    }

    /**
     * Move the anchor view to the specified location
     */
    public static void moveAnchor(final Instrumentation instrumentation, final View anchor,
                            final int x, final int y) {
        final ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) anchor.getLayoutParams();
        params.leftMargin = x;
        params.topMargin = y;

        instrumentation.runOnMainSync(() -> anchor.setLayoutParams(params));
        instrumentation.waitForIdleSync();
    }

    /**
     * Move the anchor view to the specified location
     */
    public static void moveTargetView(final Instrumentation instrumentation, final View mTargetView,
                                      final int x, final int y) {
        final ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) mTargetView.getLayoutParams();
        params.leftMargin = x;
        params.topMargin = y;

        instrumentation.runOnMainSync(() -> mTargetView.setLayoutParams(params));
        instrumentation.waitForIdleSync();
    }

    @SuppressWarnings("unchecked")
    public static  <T extends Activity> T getActivity(ActivityScenario<T> activityScenario) throws ExecutionException, InterruptedException {
        SettableFuture<T> activityRef = SettableFuture.create();
        activityScenario.onActivity(activityRef::set);
        return (T) activityRef.get();
    }
}
