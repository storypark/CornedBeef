package com.swiftkey.cornedbeef

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.annotation.ColorInt
import androidx.annotation.LayoutRes
import androidx.annotation.Px
import com.swiftkey.cornedbeef.BubbleCoachMark.BubbleCoachMarkBuilder
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/**
 */
@Suppress("RedundantRequireNotNullCall")
class PunchedBubbleCoachMark(
    builder: PunchedBubbleCoachMarkBuilder
) : InternallyAnchoredCoachMark(builder) {
    private val target: Float
    private val showBelowAnchor: Boolean
    private val minArrowMargin: Int
    private var minWidth = 0
    private var arrowWidth = 0

    private val targetView: WeakReference<View>
    private val targetViewLoc = IntArray(2)
    private val targetViewOutline = Outline()
    private lateinit var targetViewRect: RectF
    private val punchHoleExtension: Float
    private val punchHoleRadiusOverride: Float

    private lateinit var punchedContainer: PunchedLayout
    private lateinit var bubbleContainer: View
    private lateinit var topArrow: ImageView
    private lateinit var bottomArrow: ImageView
    private lateinit var contentHolder: ViewGroup

    private var entranceAnimator: Animator? = null

    init {
        target = builder.target
        targetView = WeakReference(builder.targetView)
        showBelowAnchor = builder.showBelowAnchor
        minArrowMargin =
            mContext.resources.getDimensionPixelSize(R.dimen.coach_mark_border_radius) +
                    MIN_ARROW_MARGIN.dpToPx(mContext)
        punchHoleExtension = builder.extendPunchHole
        punchHoleRadiusOverride = builder.punchHoleRadiusOverride

        punchedContainer.punchHoleClickListener = builder.targetClickListener
        punchedContainer.globalClickListener = builder.globalClickListener

        requireNotNull(punchedContainer)
        requireNotNull(topArrow)
        requireNotNull(bottomArrow)
        requireNotNull(contentHolder)

        builder.overlayColor?.let { punchedContainer.overlayColor = it }

        // Set the bubble color, if possible. We could change the color in lower APIs but we'd
        // have to use the support library, increasing the size of the CornedBeef library.
        ColorStateList.valueOf(builder.bubbleColor).apply {
            topArrow.imageTintList = this
            bottomArrow.imageTintList = this
            (contentHolder.background.mutate() as GradientDrawable).color = this
        }
    }

    override fun createContentView(content: View, builder: CoachMarkBuilder): View {
        @Suppress("NAME_SHADOWING") val builder = builder as PunchedBubbleCoachMarkBuilder
        // Inflate the coach mark layout and add the content
        val view = LayoutInflater.from(mContext).inflate(R.layout.punched_bubble_coach_mark, null)
        punchedContainer = view.findViewById(R.id.punched_container)
        punchedContainer.rect = RectF().also { targetViewRect = it }
        bubbleContainer = view.findViewById(R.id.bubble_container)
        val contentHolder =
            view.findViewById<ViewGroup>(R.id.coach_mark_content).also { contentHolder = it }
        contentHolder.addView(content)

        // Measure the coach mark to get the minimum width (constrained by screen width and padding)
        val maxWidth =
            if (builder.bubbleMaxWidth != 0) {
                builder.bubbleMaxWidth
            } else {
                Int.MAX_VALUE
            }.coerceAtMost(
                mContext.resources
                    .displayMetrics.widthPixels - (2 * mPadding)
            )

        bubbleContainer.measure(
            View.MeasureSpec.makeMeasureSpec(
                maxWidth,
                View.MeasureSpec.AT_MOST
            ), 0
        )
        minWidth = bubbleContainer.measuredWidth
        topArrow = bubbleContainer.findViewById(R.id.top_arrow)
        bottomArrow = bubbleContainer.findViewById(R.id.bottom_arrow)

        // Ensure that content holder expands to fill the coach mark
        contentHolder.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        bubbleContainer.layoutParams.width = minWidth

        // It is assumed that the top and bottom arrows are identical
        arrowWidth = bottomArrow.measuredWidth
        return view
    }

    override fun createNewPopupWindow(contentView: View): PopupWindow {
        val popup = PopupWindow(
            contentView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        popup.isClippingEnabled = false // We will handle clipping ourselves
        popup.isTouchable = true
        return popup
    }

    override fun getPopupDimens(anchorDimens: CoachMarkDimens<Int>): CoachMarkDimens<Int> {
        val screenWidth = mDisplayFrame.width()
        val screenHeight = mDisplayFrame.height()
        val popupWidth =
            minWidth

        val popupHeight = bubbleContainer.measuredHeight
        val popupPos =
            CoachMarkUtils.getPopupPosition(
                /* anchorDimens = */ anchorDimens,
                /* popupWidth = */ popupWidth,
                /* popupHeight = */ popupHeight,
                /* screenWidth = */ screenWidth,
                /* screenHeight = */ screenHeight,
                /* padding = */ mPadding,
                /* showBelow = */ showBelowAnchor
            )
        return CoachMarkDimens(
            /* x = */ popupPos.x,
            /* y = */ popupPos.y,
            /* width = */ popupWidth,
            /* height = */ popupHeight
        )
    }

    override fun updateView(popupDimens: CoachMarkDimens<Int>, anchorDimens: CoachMarkDimens<Int>) {
        // Check if the popup is being shown above or below the anchor
        val currentArrow: View =
            if (popupDimens.pos.y > anchorDimens.y) {
                topArrow.visibility = View.VISIBLE
                bottomArrow.visibility = View.GONE
                topArrow
            } else {
                bottomArrow.visibility = View.VISIBLE
                topArrow.visibility = View.GONE
                bottomArrow
            }
        CoachMarkUtils.getArrowLeftMargin(
            /* target = */ target,
            /* anchorWidth = */ anchorDimens.width,
            /* arrowWidth = */ arrowWidth,
            /* anchorX = */ anchorDimens.x,
            /* popupX = */ popupDimens.pos.x,
            /* minMargin = */ minArrowMargin,
            /* maxMargin = */ popupDimens.width - minArrowMargin - arrowWidth
        ).apply {
            val params = currentArrow.layoutParams as MarginLayoutParams
            if (this != params.leftMargin) {
                params.leftMargin = this
                currentArrow.layoutParams = params
            }
        }

        // Update punched container padding
        punchedContainer.setPaddingRelative(
            /* start = */ popupDimens.x,
            /* top = */ popupDimens.y + punchHoleExtension.roundToInt(),
            /* end = */ popupDimens.x,
            /* bottom = */ 0
        )

        // Update punch
        targetView.get()?.run {
            getLocationOnScreen(targetViewLoc)

            if (punchHoleRadiusOverride == 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                targetViewOutline.setEmpty()
                outlineProvider.getOutline(this, targetViewOutline)

                val radius = targetViewOutline.radius
                punchedContainer.cornerRadius = radius
            } else {
                punchedContainer.cornerRadius = punchHoleRadiusOverride
            }
            targetViewRect.set(
                /* left = */ targetViewLoc[0].toFloat() - punchHoleExtension,
                /* top = */ targetViewLoc[1].toFloat() - punchHoleExtension,
                /* right = */ targetViewLoc[0].toFloat() + width.toFloat() + punchHoleExtension,
                /* bottom = */ targetViewLoc[1].toFloat() + height.toFloat() + punchHoleExtension
            )
            punchedContainer.postInvalidate()
        }
    }

    override fun show() {
        // Setup punched entrace animation
        if (isShowing) {
            return
        }
        entranceAnimator = AnimatorSet().apply {
            play(
                ObjectAnimator.ofFloat(contentView, "alpha", 0f, 1f)
                    .setDuration(300)
                    .also { it.interpolator = LinearInterpolator() }
            ).with(
                ObjectAnimator.ofFloat(
                    bubbleContainer,
                    "translationY",
                    40.dpToPx(mContext).toFloat(),
                    0f
                )
                    .setDuration(1000)
                    .also { it.interpolator = OvershootInterpolator(1.5f) }
            )
        }.also { it.start() }
        super.show()
    }

    @Suppress("unused")
    class PunchedBubbleCoachMarkBuilder : BubbleCoachMarkBuilder {
        internal var overlayColor: Int? = null
        internal var globalClickListener: View.OnClickListener? = null
        internal var targetClickListener: View.OnClickListener? = null
        internal var targetView: View? = null
        internal var extendPunchHole = 0f
        internal var punchHoleRadiusOverride = 0f
        internal var bubbleMaxWidth: Int = 0

        constructor(context: Context, anchor: View, message: String?) :
                super(context, anchor, message)

        constructor(context: Context, anchor: View, content: View?) :
                super(context, anchor, content)

        constructor(context: Context, anchor: View, @LayoutRes contentResId: Int) :
                super(context, anchor, contentResId)

        init {
            setAnimation(R.style.CoachMarkAnimation_PunchedBubble)
        }

        /**
         * Set a target view where the "punch hole" will display.
         * @param view
         */
        fun setTargetView(view: View): PunchedBubbleCoachMarkBuilder {
            this.targetView = view
            return this
        }

        fun setBubbleMaxWidth(@Px bubbleMaxWidth: Int): PunchedBubbleCoachMarkBuilder {
            this.bubbleMaxWidth = bubbleMaxWidth
            return this
        }

        fun setOverlayColor(@ColorInt overlayColor: Int): PunchedBubbleCoachMarkBuilder {
            this.overlayColor = overlayColor
            return this
        }

        fun setExtendPunchHole(@Px amount: Float): PunchedBubbleCoachMarkBuilder {
            this.extendPunchHole = amount
            return this
        }

        fun setPunchHoleRadiusOverride(@Px radius: Float): PunchedBubbleCoachMarkBuilder {
            this.punchHoleRadiusOverride = radius
            return this
        }

        /**
         * Set a listener to be called when the target view is clicked.
         * @param listener
         */
        fun setOnTargetClickListener(listener: View.OnClickListener): PunchedBubbleCoachMarkBuilder {
            this.targetClickListener = listener
            return this
        }

        /**
         * Set a listener to be called when the coach mark is clicked.
         * @param listener
         */
        fun setOnGlobalClickListener(listener: View.OnClickListener): PunchedBubbleCoachMarkBuilder {
            this.globalClickListener = listener
            return this
        }

        override fun setShowBelowAnchor(showBelowAnchor: Boolean): PunchedBubbleCoachMarkBuilder {
            super.setShowBelowAnchor(showBelowAnchor)
            return this
        }

        override fun setTargetOffset(target: Float): PunchedBubbleCoachMarkBuilder {
            super.setTargetOffset(target)
            return this
        }

        override fun setBubbleColor(bubbleColor: Int): PunchedBubbleCoachMarkBuilder {
            super.setBubbleColor(bubbleColor)
            return this
        }

        override fun setTokenView(tokenView: View?): PunchedBubbleCoachMarkBuilder {
            super.setTokenView(tokenView)
            return this
        }

        override fun setTimeout(timeoutInMs: Long): PunchedBubbleCoachMarkBuilder {
            super.setTimeout(timeoutInMs)
            return this
        }

        override fun setPadding(padding: Int): PunchedBubbleCoachMarkBuilder {
            super.setPadding(padding)
            return this
        }

        override fun setOnDismissListener(listener: OnDismissListener?): PunchedBubbleCoachMarkBuilder {
            super.setOnDismissListener(listener)
            return this
        }

        override fun setOnTimeoutListener(listener: OnTimeoutListener?): PunchedBubbleCoachMarkBuilder {
            super.setOnTimeoutListener(listener)
            return this
        }

        override fun setAnimation(animationStyle: Int): PunchedBubbleCoachMarkBuilder {
            super.setAnimation(animationStyle)
            return this
        }

        override fun setOnShowListener(listener: OnShowListener?): PunchedBubbleCoachMarkBuilder {
            super.setOnShowListener(listener)
            return this
        }

        override fun setDismissOnAnchorDetach(shouldDismissOnAnchorDetach: Boolean): PunchedBubbleCoachMarkBuilder {
            super.setDismissOnAnchorDetach(shouldDismissOnAnchorDetach)
            return this
        }

        override fun setPopupWindowBackgroundColor(popupWindowBackgroundColor: Int): PunchedBubbleCoachMarkBuilder {
            super.setPopupWindowBackgroundColor(popupWindowBackgroundColor)
            return this
        }

        override fun setPopupWindowFitsSystemWindows(popupWindowFitToWindow: Boolean): PunchedBubbleCoachMarkBuilder {
            super.setPopupWindowFitsSystemWindows(popupWindowFitToWindow)
            return this
        }

        override fun setTextColor(textColor: Int): PunchedBubbleCoachMarkBuilder {
            super.setTextColor(textColor)
            return this
        }

        override fun setInternalAnchor(
            x: Float,
            y: Float,
            width: Float,
            height: Float
        ): InternallyAnchoredCoachMarkBuilder {
            super.setInternalAnchor(x, y, width, height)
            return this
        }

        override fun build(): CoachMark {
            return PunchedBubbleCoachMark(this)
        }
    }

    companion object {
        private const val MIN_ARROW_MARGIN = 10
    }
}

@Px
private fun Int.dpToPx(context: Context): Int =
    (context.resources.displayMetrics.density * this).roundToInt()