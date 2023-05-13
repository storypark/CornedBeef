package com.swiftkey.cornedbeef

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.annotation.ColorInt

internal class PunchedLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    internal var rect: RectF? = null
        set(value) {
            if (field != value) {
                field = value
                postInvalidate()
            }
        }
    internal var cornerRadius: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                postInvalidate()
            }
        }

    @ColorInt
    internal var overlayColor: Int = 0xA1000000.toInt()
        set(value) {
            if (field != value) {
                field = value
                postInvalidate()
            }
        }

    // Helpers to punch a hole
    private val paint: Paint = Paint()

    internal var punchHoleClickListener: OnClickListener? = null
    internal var globalClickListener: OnClickListener? = null

    init {
        paint.color = Color.BLACK
        paint.isAntiAlias = true
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

        clipToPadding = false
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Punch a hole to target (x, y) position with given radius.
        rect?.run {
            canvas.drawColor(overlayColor)
            if (cornerRadius == 0f) {
                canvas.drawRect(this, paint)
            } else {
                canvas.drawRoundRect(this, cornerRadius, cornerRadius, paint)
            }
        }
        super.dispatchDraw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> true
            MotionEvent.ACTION_UP -> {
                when {
                    rect?.contains(event.x, event.y) == true
                            && punchHoleClickListener != null ->
                        punchHoleClickListener!!.onClick(this)

                    globalClickListener != null ->
                        globalClickListener!!.onClick(this)

                    else -> return false
                }
                true
            }

            else -> false
        }
    }
}