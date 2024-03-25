package dev.sajidali.guide

import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Scroller
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import dev.sajidali.guide.data.Channel
import dev.sajidali.guide.data.DataProvider
import dev.sajidali.guide.data.Event
import org.joda.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.*

class GuideView : ViewGroup {

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = -1) : super(
        context, attrs, defStyleAttr
    ) {
        init(attrs, defStyleAttr)
    }

    constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = -1, defStyleRes: Int = -1
    ) : super(
        context, attrs, defStyleAttr, defStyleRes
    ) {
        init(attrs, defStyleAttr, defStyleRes)
    }


    private var dataProvider: DataProvider? = null
    private var timeFormat: String = "HH:mm"

    private val mClipRect: Rect
    private val mDrawingRect: Rect
    private val mMeasuringRect: Rect
    private val mPaint: Paint
    private val mScroller: Scroller
    private val mGestureDetector: GestureDetector

    private var mChannelLayoutMargin: Int
    private var mChannelLayoutPadding: Int
    private var mChannelLayoutHeight: Int
    private var mChannelLayoutWidth: Int
    private var mChannelLayoutBackground: Int = Color.TRANSPARENT
    private var mEventLayoutBackground: Int
    private var mEventLayoutBackgroundCurrent: Int
    var eventLayoutBackgroundSelected: Int
    var eventLayoutTextColor: Int
    var eventLayoutTextSize: Int
    private var mTimeBarLineWidth: Int
    private var mTimeBarLineColor: Int
    private var mTimeBarHeight: Int
    private var mTimeBarTextSize: Int
    private var mEpgTimebarBackColor: Int
    private var mHoursInViewPort: Long
    private var mDaysBack: Long
    private var mDaysForward: Long
    private var mTimeSpacing: Long

    private val mResetButtonSize: Int
    private val mResetButtonMargin: Int
    private var screenWidth = 0
    private var screenHeight = 0


    private var mainBackground: Drawable? = null

    @ColorInt
    private var backgroundTint: Int

    private var channelLayoutBackground: Bitmap? = null
        get() {
            if (field == null) {
                mainBackground ?: return null
                DrawableCompat.setTint(mainBackground!!, backgroundTint)
                val bitmap = mainBackground!!.toBitmap(screenWidth, screenHeight)
                val location = IntArray(2)
                getLocationOnScreen(location)
                if (location[1] + height < screenHeight) {
                    field = Bitmap.createBitmap(
                        bitmap, location[0], location[1], mChannelLayoutWidth, height
                    )
                }
            }
            return field
        }

    private var channelRowBackground: Bitmap? = null
        get() {
            if (field == null) {
                mainBackground ?: return null
                DrawableCompat.setTint(mainBackground!!, backgroundTint)
                val bitmap = mainBackground!!.toBitmap(screenWidth, screenHeight)
                val location = IntArray(2)
                getLocationOnScreen(location)
                field = Bitmap.createBitmap(
                    bitmap,
                    location[0],
                    location[1],
                    bitmap.width - location[0],
                    mChannelLayoutHeight
                )
            }
            return field
        }

    private var timebarBackground: Bitmap? = null
        get() {
            if (field == null) {
                DrawableCompat.setTint(mainBackground!!, backgroundTint)
                val bitmap = mainBackground!!.toBitmap(screenWidth, screenHeight)
                val location = IntArray(2)
                getLocationOnScreen(location)
                field = Bitmap.createBitmap(
                    bitmap, location[0], location[1], bitmap.width - location[0], mTimeBarHeight
                )
            }
            return field
        }

    private var mClickListener: ClickListener? = null
    private var mMaxHorizontalScroll: Int = 0
    private var mMaxVerticalScroll: Int = 0
    private var mMillisPerPixel: Long = 0
    private var mTimeOffset: Long = 0
    private var mTimeLowerBoundary: Long = 0
    private var mTimeUpperBoundary: Long = 0

    private val loadThreshold: Int
        get() {
            val x = measuredHeight * 2
            return mMaxVerticalScroll - x
        }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
//        if (t > loadThreshold) {
//            pagingDataDiffer.refresh()
//        }
    }

    private var selectedEvent: Int = -1
    private var selectedChannel = 0

    private val currentChannel: Channel?
        get() {
            return dataProvider?.let { provider ->
                if (selectedChannel in 0 until provider.size()) {
                    provider.channelAt(selectedChannel)
                } else null
            }

        }

    private val currentEvent: Event?
        get() = dataProvider?.eventOfChannelAt(selectedChannel, selectedEvent)

    private var isEventHandled: Boolean = false
    private var waitForLongClick: Boolean = false
    private var isLongPressed = false

    private val channelAreaWidth: Int
        get() = mChannelLayoutWidth + mChannelLayoutPadding + mChannelLayoutMargin

    private val programAreaWidth: Int
        get() = width - channelAreaWidth

    private val firstVisibleChannelPosition: Int
        get() {
            val y = scrollY

            var position =
                (y - mChannelLayoutMargin - mTimeBarHeight) / (mChannelLayoutHeight + mChannelLayoutMargin)

            if (position < 0) {
                position = 0
            }
            return position
        }

    private// Add one extra row if we don't fill screen with current..
    val lastVisibleChannelPosition: Int
        get() {
            val y = scrollY
            val totalChannelCount = dataProvider?.size() ?: 0
            val screenHeight = height
            var position =
                (y + screenHeight + mTimeBarHeight - mChannelLayoutMargin) / (mChannelLayoutHeight + mChannelLayoutMargin)

            if (position > totalChannelCount - 1) {
                position = totalChannelCount - 1
            }
            return if (y + screenHeight > position * mChannelLayoutHeight && position < totalChannelCount - 1) position + 1 else position
        }

    private val xPositionStart: Int
        get() = getXFrom(System.currentTimeMillis() - mHoursInViewPort / 2)

    init {
        setWillNotDraw(false)

        mDrawingRect = Rect()
        mClipRect = Rect()
        mMeasuringRect = Rect()
        mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mPaint.strokeWidth = 0.5f
        mGestureDetector = GestureDetector(context, OnGestureListener())

        // Adding some friction that makes the epg less flappy.
        mScroller = Scroller(context)
        mScroller.setFriction(0.2f)
        mainBackground = ColorDrawable(ContextCompat.getColor(context, R.color.gv_background))
        mChannelLayoutMargin = resources.getDimensionPixelSize(R.dimen.gv_channel_layout_margin)
        mChannelLayoutPadding = resources.getDimensionPixelSize(R.dimen.gv_channel_layout_padding)
        mChannelLayoutHeight = resources.getDimensionPixelSize(R.dimen.gv_channel_layout_height)
        mChannelLayoutWidth = resources.getDimensionPixelSize(R.dimen.gv_channel_layout_width)
        mChannelLayoutBackground = context.getThemedAttribute(android.R.attr.colorPrimaryDark)
        mEventLayoutBackground = Color.TRANSPARENT
        mEventLayoutBackgroundCurrent = context.getThemedAttribute(android.R.attr.colorPrimaryDark)
        eventLayoutBackgroundSelected = context.getThemedAttribute(android.R.attr.colorPrimaryDark)
        eventLayoutTextColor = Color.parseColor("#ffd6d6d6")
        eventLayoutTextSize = resources.getDimensionPixelSize(R.dimen.gv_event_layout_text)
        mTimeBarHeight = resources.getDimensionPixelSize(R.dimen.gv_time_bar_height)
        mTimeBarTextSize = resources.getDimensionPixelSize(R.dimen.gv_time_bar_text)
        mTimeBarLineWidth = resources.getDimensionPixelSize(R.dimen.gv_time_bar_line_width)
        mTimeBarLineColor = context.getThemedAttribute(android.R.attr.colorAccent)
        mEpgTimebarBackColor = Color.BLUE
        mResetButtonSize = resources.getDimensionPixelSize(R.dimen.gv_reset_button_size)
        mResetButtonMargin = resources.getDimensionPixelSize(R.dimen.gv_reset_button_margin)
        mEpgTimebarBackColor = Color.TRANSPARENT
        mHoursInViewPort = TimeUnit.HOURS.toMillis(1) + TimeUnit.MINUTES.toMillis(30)
        mDaysBack = TimeUnit.MINUTES.toMillis(30)
        mDaysForward = TimeUnit.DAYS.toMillis(1)
        mTimeSpacing = TimeUnit.MINUTES.toMillis(30)
        backgroundTint = context.getThemedAttribute(android.R.attr.colorPrimary)
        screenDimenInitialization()

    }

    private fun screenDimenInitialization() {
        screenWidth = resources.displayMetrics.widthPixels
        screenHeight = resources.displayMetrics.heightPixels
        if (screenWidth < screenHeight) {
            screenWidth = screenHeight
            screenHeight = resources.displayMetrics.widthPixels
        }
    }

    private fun init(attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0) {
        context.withStyledAttributes(attrs, R.styleable.GuideView, defStyleAttr, defStyleRes) {
            mainBackground = getColorOrDrawable(
                R.styleable.GuideView_mainBackground,
                ContextCompat.getColor(context, R.color.gv_background)
            )
            mChannelLayoutMargin = getDimensionPixelSize(
                R.styleable.GuideView_gv_ChannelMargin,
                resources.getDimensionPixelSize(R.dimen.gv_channel_layout_margin)
            )
            mChannelLayoutPadding = getDimensionPixelSize(
                R.styleable.GuideView_gv_ChannelPadding,
                resources.getDimensionPixelSize(R.dimen.gv_channel_layout_padding)
            )
            mChannelLayoutHeight = getDimensionPixelSize(
                R.styleable.GuideView_gv_ChannelHeight,
                resources.getDimensionPixelSize(R.dimen.gv_channel_layout_height)
            )

            mChannelLayoutWidth = getDimensionPixelSize(
                R.styleable.GuideView_gv_ChannelWidth,
                resources.getDimensionPixelSize(R.dimen.gv_channel_layout_width)
            )
            mChannelLayoutBackground =
                getColor(R.styleable.GuideView_gv_ChannelBackground, mChannelLayoutBackground)
            mEventLayoutBackground =
                getColor(R.styleable.GuideView_gv_EventBackground, mEventLayoutBackground)
            mEventLayoutBackgroundCurrent = getColor(
                R.styleable.GuideView_gv_EventLayoutCurrentBackground, mEventLayoutBackgroundCurrent
            )
            eventLayoutBackgroundSelected = getColor(
                R.styleable.GuideView_gv_EventLayoutSelectedBackground,
                eventLayoutBackgroundSelected
            )
            eventLayoutTextColor =
                getColor(R.styleable.GuideView_gv_EventTextColor, eventLayoutTextColor)
            eventLayoutTextSize = getDimensionPixelSize(
                R.styleable.GuideView_gv_EventTextSize,
                resources.getDimensionPixelSize(R.dimen.gv_event_layout_text)
            )
            mTimeBarHeight = getDimensionPixelSize(
                R.styleable.GuideView_gv_TimebarHeight,
                resources.getDimensionPixelSize(R.dimen.gv_time_bar_height)
            )
            mTimeBarTextSize = getDimensionPixelSize(
                R.styleable.GuideView_gv_TimebarTextSize,
                resources.getDimensionPixelSize(R.dimen.gv_time_bar_text)
            )
            mTimeBarLineWidth = getDimensionPixelSize(
                R.styleable.GuideView_gv_TimebarLineWidth,
                resources.getDimensionPixelSize(R.dimen.gv_time_bar_line_width)
            )
            mTimeBarLineColor =
                getColor(R.styleable.GuideView_gv_TimebarLineColor, mTimeBarLineColor)
            mEpgTimebarBackColor =
                getColor(R.styleable.GuideView_gv_TimebarBackColor, mEpgTimebarBackColor)
            mHoursInViewPort =
                TimeUnit.HOURS.toMillis(getInt(R.styleable.GuideView_gv_HoursToShow, 2).toLong())
            mTimeSpacing = TimeUnit.MINUTES.toMillis(
                getInt(
                    R.styleable.GuideView_gv_TimeSpacingInMinutes, 30
                ).toLong()
            )
            backgroundTint = getColor(R.styleable.GuideView_mainBackgroundTint, backgroundTint)
        }

        screenDimenInitialization()
    }

    fun setDataProvider(provider: DataProvider) {
        this.dataProvider = provider
    }

    public override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val epgState = EPGState(superState!!)
        epgState.currentEvent = this.selectedEvent
        return epgState
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        if (state !is EPGState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        this.selectedEvent = state.currentEvent
    }

    override fun onDraw(canvas: Canvas) {
        if ((dataProvider?.size() ?: 0) > 0) {
            mTimeLowerBoundary = getTimeFrom(scrollX)
            mTimeUpperBoundary = getTimeFrom(scrollX + width)

            val drawingRect = mDrawingRect
            drawingRect.left = scrollX
            drawingRect.top = scrollY
            drawingRect.right = drawingRect.left + width
            drawingRect.bottom = drawingRect.top + height

            drawChannelListItems(canvas, drawingRect)
            drawEvents(canvas, drawingRect)
            drawTimebar(canvas, drawingRect)
            drawTimeLine(canvas, drawingRect)
            //drawResetButton(canvas, drawingRect);

            // If scroller is scrolling/animating do scroll. This applies when doing a fling.
            if (mScroller.computeScrollOffset()) {
                scrollTo(mScroller.finalX, mScroller.finalY)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        timebarBackground = null
        channelLayoutBackground = null
        channelRowBackground = null
        recalculateAndRedraw(this.selectedEvent, false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return mGestureDetector.onTouchEvent(event)
    }


    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        //return mGame.handleMotionEvent(event);
        return false
    }


    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}

    private fun drawTimebar(canvas: Canvas, drawingRect: Rect) {
        drawingRect.left = scrollX
        drawingRect.top = scrollY
        drawingRect.right = drawingRect.left + width
        drawingRect.bottom = drawingRect.top + mTimeBarHeight

        mPaint.style = Paint.Style.FILL
//        mPaint.color = mEpgTimebarBackColor
//        canvas.drawRect(drawingRect, mPaint)

        if (timebarBackground != null) {
            canvas.drawBitmap(
                timebarBackground!!, drawingRect.left.toFloat(), drawingRect.top.toFloat(), mPaint
            )
        }

        drawingRect.left = scrollX + mChannelLayoutWidth + mChannelLayoutMargin
        drawingRect.top = scrollY
        drawingRect.right = drawingRect.left + width
        drawingRect.bottom = drawingRect.top + mTimeBarHeight

        mClipRect.left = scrollX + mChannelLayoutWidth + mChannelLayoutMargin
        mClipRect.top = scrollY
        mClipRect.right = scrollX + width
        mClipRect.bottom = mClipRect.top + mTimeBarHeight

        canvas.save()
        canvas.clipRect(mClipRect)

        // Background
        drawStrokedRectangle(canvas, drawingRect)

        // Time stamps
        mPaint.color = eventLayoutTextColor
        mPaint.textSize = mTimeBarTextSize.toFloat()

        for (i in 0 until mHoursInViewPort / mTimeSpacing) {
            // Get time and round to nearest half hour
            val time =
                mTimeSpacing * ((mTimeLowerBoundary + mTimeSpacing * i + mTimeSpacing / 2) / mTimeSpacing)

            canvas.drawText(
                time.formatToPattern(timeFormat),
                getXFrom(time).toFloat(),
                (drawingRect.top + ((drawingRect.bottom - drawingRect.top) / 2 + mTimeBarTextSize / 2)).toFloat(),
                mPaint
            )
        }

        canvas.restore()

        drawTimebarDayIndicator(canvas, drawingRect)
//        drawTimebarBottomStroke(canvas, drawingRect)
    }

    fun setTimeFormat(format: String) {
        this.timeFormat = format
        redraw()
    }

    private fun drawTimebarDayIndicator(canvas: Canvas, drawingRect: Rect) {
        drawingRect.left = scrollX
        drawingRect.top = scrollY
        drawingRect.right = drawingRect.left + mChannelLayoutWidth
        drawingRect.bottom = drawingRect.top + mTimeBarHeight
//
        mPaint.style = Paint.Style.FILL
//        mPaint.color = mEpgTimebarBackColor
//        canvas.drawRect(drawingRect, mPaint)

        // Background
        drawStrokedRectangle(canvas, drawingRect)

        // Text
        mPaint.color = eventLayoutTextColor
        mPaint.textSize = mTimeBarTextSize.toFloat()
        mPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            mTimeLowerBoundary.toDayName(),
            (drawingRect.left + (drawingRect.right - drawingRect.left) / 2).toFloat(),
            (drawingRect.top + ((drawingRect.bottom - drawingRect.top) / 2 + mTimeBarTextSize / 2)).toFloat(),
            mPaint
        )

        mPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawTimeLine(canvas: Canvas, drawingRect: Rect) {
        val now = System.currentTimeMillis()

        if (shouldDrawTimeLine(now)) {
            drawingRect.left = getXFrom(now)
            drawingRect.top = scrollY
            drawingRect.right = drawingRect.left + mTimeBarLineWidth
            drawingRect.bottom = drawingRect.top + height

            mPaint.color = mTimeBarLineColor
            canvas.drawRect(drawingRect, mPaint)
        }

    }

    private fun drawEvents(canvas: Canvas, drawingRect: Rect) {
        val firstPos = firstVisibleChannelPosition
        val lastPos = lastVisibleChannelPosition

        for (channelPos in firstPos..lastPos) {
            // Set clip rectangle
            mClipRect.left = scrollX + mChannelLayoutWidth + mChannelLayoutMargin
            mClipRect.top = getTopFrom(channelPos)
            mClipRect.right = scrollX + width
            mClipRect.bottom = mClipRect.top + mChannelLayoutHeight

            canvas.save()
            canvas.clipRect(mClipRect)

            // Draw each event
            var foundFirst = false

            val epgEvents = dataProvider?.eventsOfChannel(channelPos)
            epgEvents?.takeIf { it.isNotEmpty() }?.let { events ->
                for ((index, event) in events.withIndex()) {
                    if (isEventVisible(event.start, event.end)) {
                        drawEvent(canvas, channelPos, index, drawingRect)
                        foundFirst = true
                    } else if (foundFirst) {
                        break
                    }
                }
            }
//            if (epgEvents != null && epgEvents.size > 0) {
//                for (index in 0 until epgEvents.size) {
//                    val event = epgEvents.getOrNull(index) ?: continue
//                    if (isEventVisible(event.startTimestamp, event.stopTimestamp)) {
//                        drawEvent(canvas, channelPos, index, drawingRect)
//                        foundFirst = true
//                    } else if (foundFirst) {
//                        break
//                    }
//                }
//            }

            canvas.restore()
        }

    }

    private fun drawEvent(
        canvas: Canvas, channelPosition: Int, eventPosition: Int, drawingRect: Rect
    ) {
        val event = dataProvider?.eventOfChannelAt(channelPosition, eventPosition) ?: return
        setEventDrawingRectangle(
            channelPosition, event.start, event.end, drawingRect
        )
        // Background
        if (channelPosition == selectedChannel && selectedEvent != -1 && eventPosition == selectedEvent) {
            mPaint.color = eventLayoutBackgroundSelected
        } else {
            mPaint.color = Color.TRANSPARENT
        }
        if (drawingRect.left < scrollX + channelAreaWidth) {
            drawingRect.left = scrollX + channelAreaWidth
        }
        canvas.drawRect(drawingRect, mPaint)
//        mPaint.style = Paint.Style.STROKE
//        mPaint.strokeWidth = 0.3f
        mPaint.color = Color.LTGRAY
//        canvas.drawRect(drawingRect, mPaint)
        mPaint.style = Paint.Style.FILL

        canvas.drawLine(
            drawingRect.left.toFloat(),
            drawingRect.top.toFloat(),
            drawingRect.left.toFloat(),
            drawingRect.bottom.toFloat(),
            mPaint
        )

        canvas.drawLine(
            drawingRect.left.toFloat(),
            drawingRect.bottom.toFloat(),
            drawingRect.right.toFloat(),
            drawingRect.bottom.toFloat(),
            mPaint
        )

        // Add left and right inner padding
        drawingRect.left += mChannelLayoutPadding + 16
        drawingRect.right -= mChannelLayoutPadding

        // Text
        mPaint.color = eventLayoutTextColor
        mPaint.textSize = eventLayoutTextSize.toFloat()

        // Move drawing.top so text will be centered (text is drawn bottom>up)
        mPaint.getTextBounds(event.title, 0, event.title?.length ?: 0, mMeasuringRect)
        drawingRect.top += (drawingRect.bottom - drawingRect.top) / 2 + mMeasuringRect.height() / 2

        var title = event.title
        title = title.substring(
            0, mPaint.breakText(title, true, (drawingRect.right - drawingRect.left).toFloat(), null)
        )
        canvas.drawText(title, drawingRect.left.toFloat(), drawingRect.top.toFloat(), mPaint)

    }

    private fun setEventDrawingRectangle(
        channelPosition: Int, start: Long, end: Long, drawingRect: Rect
    ) {
        drawingRect.left = getXFrom(start)
        drawingRect.top = getTopFrom(channelPosition)
        drawingRect.right = getXFrom(end) - mChannelLayoutMargin
        drawingRect.bottom = drawingRect.top + mChannelLayoutHeight
    }

    private fun drawChannelListItems(canvas: Canvas, drawingRect: Rect) {
        // Background
        mMeasuringRect.left = scrollX
        mMeasuringRect.top = scrollY
        mMeasuringRect.right = drawingRect.left + mChannelLayoutWidth
        mMeasuringRect.bottom = mMeasuringRect.top + height

        if (channelLayoutBackground != null) {
            canvas.drawBitmap(
                channelLayoutBackground!!,
                mMeasuringRect.left.toFloat(),
                mMeasuringRect.top.toFloat(),
                mPaint
            )
        }

//        mPaint.color = Color.LTGRAY
//        mPaint.strokeWidth = 1f
//        mPaint.style = Paint.Style.STROKE
//        canvas.drawRect(mMeasuringRect, mPaint)
//        mPaint.style = Paint.Style.FILL

        val firstPos = firstVisibleChannelPosition
        val lastPos = lastVisibleChannelPosition

        for (pos in firstPos..lastPos) {
            drawChannelItem(canvas, pos, drawingRect)
        }
    }

    private fun drawChannelItem(canvas: Canvas, position: Int, drawingRect: Rect) {

        //Draw Full Row Background
        drawingRect.left = scrollX
        drawingRect.top = getTopFrom(position)
        drawingRect.right = drawingRect.left + programAreaWidth + mChannelLayoutWidth
        drawingRect.bottom = drawingRect.top + mChannelLayoutHeight

        if (selectedChannel == position && channelRowBackground != null) {
            mPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            mPaint.colorFilter = LightingColorFilter(0xFFFFFF, 0x00222222)
            canvas.drawBitmap(
                channelRowBackground!!,
                drawingRect.left.toFloat(),
                drawingRect.top.toFloat(),
                mPaint
            )
            mPaint.xfermode = null
            mPaint.colorFilter = null
        }

        drawingRect.left = scrollX
        drawingRect.top = getTopFrom(position)
        drawingRect.right = drawingRect.left + mChannelLayoutWidth
        drawingRect.bottom = drawingRect.top + mChannelLayoutHeight

        mPaint.color = Color.LTGRAY
        mPaint.style = Paint.Style.FILL

        canvas.drawLine(
            drawingRect.left.toFloat(),
            drawingRect.bottom.toFloat(),
            drawingRect.right.toFloat(),
            drawingRect.bottom.toFloat(),
            mPaint
        )

        drawingRect.left += 16

        //Draw Channel Name
        mPaint.color = eventLayoutTextColor
        mPaint.textSize = eventLayoutTextSize.toFloat()

        var title = dataProvider?.channelAt(position)?.title ?: ""

        // Move drawing.top so text will be centered (text is drawn bottom>up)
        mPaint.getTextBounds(title, 0, title.length, mMeasuringRect)
        drawingRect.top += (drawingRect.bottom - drawingRect.top) / 2 + mMeasuringRect.height() / 2

        title = title.substring(
            0, mPaint.breakText(title, true, (drawingRect.right - drawingRect.left).toFloat(), null)
        )
        canvas.drawText(title ?: "", drawingRect.left.toFloat(), drawingRect.top.toFloat(), mPaint)
    }

    private fun drawStrokedRectangle(canvas: Canvas, drawingRect: Rect) {
        mPaint.strokeWidth = 0.3f
        mPaint.color = Color.WHITE
        mPaint.style = Paint.Style.STROKE
        canvas.drawRect(drawingRect, mPaint)
        mPaint.style = Paint.Style.FILL
    }

    private fun getDrawingRectForChannelImage(drawingRect: Rect, image: Bitmap): Rect {
        drawingRect.left += mChannelLayoutPadding
        drawingRect.top += mChannelLayoutPadding
        drawingRect.right -= mChannelLayoutPadding
        drawingRect.bottom -= mChannelLayoutPadding

        val imageWidth = image.width
        val imageHeight = image.height
        val imageRatio = imageHeight / imageWidth.toFloat()

        val rectWidth = drawingRect.right - drawingRect.left
        val rectHeight = drawingRect.bottom - drawingRect.top

        // Keep aspect ratio.
        if (imageWidth > imageHeight) {
            val padding = (rectHeight - rectWidth * imageRatio).toInt() / 2
            drawingRect.top += padding
            drawingRect.bottom -= padding
        } else if (imageWidth <= imageHeight) {
            val padding = (rectWidth - rectHeight / imageRatio).toInt() / 2
            drawingRect.left += padding
            drawingRect.right -= padding
        }

        return drawingRect
    }

    private fun getDrawingRectForChannelName(drawingRect: Rect, image: Bitmap): Rect {
        drawingRect.left += mChannelLayoutPadding
        drawingRect.top += mChannelLayoutPadding
        drawingRect.right -= mChannelLayoutPadding
        drawingRect.bottom -= mChannelLayoutPadding

        val imageWidth = image.width
        val imageHeight = image.height
        val imageRatio = imageHeight / imageWidth.toFloat()

        val rectWidth = drawingRect.right - drawingRect.left
        val rectHeight = drawingRect.bottom - drawingRect.top

        // Keep aspect ratio.
        if (imageWidth > imageHeight) {
            val padding = (rectHeight - rectWidth * imageRatio).toInt() / 2
            drawingRect.top += padding
            drawingRect.bottom -= padding
        } else if (imageWidth <= imageHeight) {
            val padding = (rectWidth - rectHeight / imageRatio).toInt() / 2
            drawingRect.left += padding
            drawingRect.right -= padding
        }

        return drawingRect
    }

    private fun shouldDrawTimeLine(now: Long): Boolean {
        return now in mTimeLowerBoundary until mTimeUpperBoundary
    }

    fun isTimelineVisible(): Boolean {
        return System.currentTimeMillis() in mTimeLowerBoundary until mTimeUpperBoundary
    }

    private fun isEventVisible(start: Long, end: Long): Boolean {
        return (start in mTimeLowerBoundary..mTimeUpperBoundary || end in mTimeLowerBoundary..mTimeUpperBoundary || start <= mTimeLowerBoundary && end >= mTimeUpperBoundary)
    }

    private fun calculatedBaseLine(): Long {
        return LocalDateTime.now().toDateTime().minusMillis(mDaysBack.toInt()).millis
    }

    private fun calculateMaxHorizontalScroll() {
        mMaxHorizontalScroll =
            ((mDaysBack + mDaysForward - mHoursInViewPort) / mMillisPerPixel).toInt()
    }

    private fun calculateMaxVerticalScroll() {
        val maxVerticalScroll = getTopFrom((dataProvider?.size() ?: 0) - 1) + mChannelLayoutHeight
        mMaxVerticalScroll = if (maxVerticalScroll < height) 0 else maxVerticalScroll - height
    }

    private fun getXFrom(time: Long): Int {
        return (((time - mTimeOffset) / mMillisPerPixel).toInt() + mChannelLayoutMargin + mChannelLayoutWidth)
    }

    private fun getTopFrom(position: Int): Int {
        return (position * (mChannelLayoutHeight + mChannelLayoutMargin) + mChannelLayoutMargin + mTimeBarHeight)
    }

    private fun getTimeFrom(x: Int): Long {
        return x * mMillisPerPixel + mTimeOffset
    }

    private fun calculateMillisPerPixel(): Long {
        return (mHoursInViewPort / (measuredWidth - mChannelLayoutWidth - mChannelLayoutMargin))
    }

    private fun resetBoundaries() {
        mMillisPerPixel = calculateMillisPerPixel()
        mTimeOffset = calculatedBaseLine()
        mTimeLowerBoundary = getTimeFrom(0)
        mTimeUpperBoundary = getTimeFrom(width)
    }

    private fun calculateChannelsHitArea(): Rect {
        mMeasuringRect.top = mTimeBarHeight
        val visibleChannelsHeight =
            (dataProvider?.size() ?: 0) * (mChannelLayoutHeight + mChannelLayoutMargin)
        mMeasuringRect.bottom =
            if (visibleChannelsHeight < height) visibleChannelsHeight else height
        mMeasuringRect.left = 0
        mMeasuringRect.right = mChannelLayoutWidth
        return mMeasuringRect
    }

    private fun calculateProgramsHitArea(): Rect {
        mMeasuringRect.top = mTimeBarHeight
        val visibleChannelsHeight =
            dataProvider.itemCount * (mChannelLayoutHeight + mChannelLayoutMargin)
        mMeasuringRect.bottom =
            if (visibleChannelsHeight < height) visibleChannelsHeight else height
        mMeasuringRect.left = mChannelLayoutWidth
        mMeasuringRect.right = width
        return mMeasuringRect
    }

    private fun getChannelPosition(y: Int): Int {
        var y1 = y
        y1 -= mTimeBarHeight
        val channelPosition = (y1) / (mChannelLayoutHeight + mChannelLayoutMargin)

        return if (dataProvider.itemCount == 0) -1 else channelPosition
    }

    private fun getProgramPosition(channelPosition: Int, time: Long): Int {
        val events = dataProvider?.eventsOfChannel(channelPosition) ?: return 0
//        for ((index, event) in events.withIndex()) {
//            if (event.start <= time && event.end >= time) {
//                return index
//            }
//        }
        return events.indexOfFirst { event ->
            event.start <= time && event.end >= time
        }
//        for (eventPos in 0 until events.size) {
//            val event = events.getOrNull(eventPos) ?: continue
//
//            if (event.startTimestamp <= time && event.stopTimestamp >= time) {
//                return eventPos
//            }
//        }
//        return 0
    }

    private fun getProgramAtTime(channelPosition: Int, time: Long): Event? {
        return dataProvider?.eventsOfChannel(channelPosition)?.find { event ->
            event.start <= time && event.end >= time
        }
//
//
//        for (() in 0 until events.size) {
//            val event = events.getOrNull(eventPos) ?: continue
//
//            if (event.startTimestamp <= time && event.stopTimestamp >= time) {
//                return event
//            }
//        }
//        return null
    }

    fun gotoNow() {
        var dT: Long
        var dX = 0
        var dY = 0

        // calculate optimal Y position

        val minYVisible =
            scrollY // is 0 when scrolled completely to top (first channel fully visible)
        val maxYVisible = minYVisible + height

        val currentChannelTop =
            mTimeBarHeight + selectedChannel * (mChannelLayoutHeight + mChannelLayoutMargin)
        val currentChannelBottom = currentChannelTop + mChannelLayoutHeight

        if (currentChannelTop < minYVisible) {
            dY = currentChannelTop - minYVisible - mTimeBarHeight
        } else if (currentChannelBottom > maxYVisible) {
            dY = currentChannelBottom - maxYVisible
        }

        // calculate optimal X position
        mTimeLowerBoundary = getTimeFrom(scrollX)
        mTimeUpperBoundary = getTimeFrom(scrollX + programAreaWidth)
        val centerPosition = mTimeLowerBoundary + (mTimeUpperBoundary - mTimeLowerBoundary) / 2
        if (now > centerPosition) {
            //we need to scroll the grid to the left
            dT = (centerPosition - now) * -1
            dX = if (mMillisPerPixel > 0) (dT / mMillisPerPixel).toFloat().roundToInt() else 0
        }
        if (now < centerPosition) {
            //we need to scroll the grid to the right
            dT = now - centerPosition
            dX = (dT / mMillisPerPixel).toFloat().roundToInt()
        }

        if (scrollX + dX < 0) {
            dX = 0 - scrollX
        }
        if (scrollY + dY < 0) {
            dY = 0 - scrollY
        }

        if (scrollX + dX > mMaxHorizontalScroll) {
            dX = mMaxHorizontalScroll - scrollX
        }

        if (scrollY + dY > mMaxVerticalScroll) {
            dY = mMaxVerticalScroll - scrollY
        }

        if (dX != 0 || dY != 0) {
            mScroller.startScroll(scrollX, scrollY, dX, dY, 0)
        }
    }

    /**
     * Add click listener to the EPG.
     *
     * @param epgClickListener to add.
     */
    fun setEPGClickListener(clickListener: ClickListener) {
        mClickListener = clickListener
    }

    fun updateSelection(channel: Int, selectedEvent: Int = -1) {
        selectedChannel = channel
        this.selectedEvent = selectedEvent
        recalculateAndRedraw(selectedEvent, withAnimation = false)
    }

//    fun updateSelection(channel: Channel) {
//
//        val position = data?.indexOfFirst { it.streamId == channel.streamId } ?: -1
//        if (position > 0) {
//            updateSelection(position, null)
//        }
//    }

    /**
     * This will recalculate boundaries, maximal scroll and scroll to start position which is current time.
     * To be used on device rotation etc since the device height and width will change.
     *
     * @param withAnimation true if scroll to current position should be animated.
     */
    fun recalculateAndRedraw(selectedEvent: Int, withAnimation: Boolean) {
        var selectedEvent2 = selectedEvent
        if (dataProvider.itemCount > 0) {
            resetBoundaries()

            calculateMaxVerticalScroll()
            calculateMaxHorizontalScroll()

            //Select initial event
            if (selectedEvent2 == -1) {
                selectedEvent2 = this.selectedEvent
            }
            if (selectedEvent2 != -1) {
                selectEvent(selectedEvent2, withAnimation)
            } else {
                selectedEvent2 = getProgramPosition(0, now)
                selectEvent(selectedEvent2, withAnimation)
                if (selectedEvent2 == -1) {
                    gotoNow()
                }
            }

            redraw()
        } else {
            redraw()
        }
    }

    /**
     * Does a invalidate() and requestLayout() which causes a redraw of screen.
     */
    fun redraw() {
        invalidate()
        requestLayout()
    }

    fun selectEvent(epgEvent: Int, withAnimation: Boolean) {
        this.selectedEvent = epgEvent
        optimizeVisibility()
        notifyListener()
        //redraw to get the coloring of the selected event
        redraw()
    }

    fun selectCurrentEvent() {
        dataProvider?.eventsOfChannel(selectedChannel)
            ?.indexOfFirst { it.start <= now && it.end >= now }?.let { selectEvent(it, false) }
//        if (pagingDataDiffer.itemCount == 0) return
//        val channel = pagingDataDiffer.getItem(selectedChannel)
//        val program =
//            channel?.programs?.firstOrNull { it.startTimestamp <= now && it.stopTimestamp >= now }
//        val position = channel?.programs?.indexOf(program) ?: -1
//        selectEvent(position, false)
    }

    fun notifyListener() {
        mClickListener?.onEventSelected(currentChannel, currentEvent)
    }

    var justGotFocus = false

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) {
            justGotFocus = true
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
//        if (event.action == KeyEvent.ACTION_DOWN) {
//            if (event.isLongPress) isLongPressed = true
//            return handleNavigationEvent(event) || handleClickEvent(event)
//        } else if (event.action == KeyEvent.ACTION_UP && !isLongPressed) {
//            logd("called action up")
//            return if (isLongPressed) {
//                isLongPressed = false
//                true
//            } else {
//                handleClickEvent(event)
//            }
//        }
        if (handleNavigationEvent(event) || handleClickEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        mTimeLowerBoundary = getTimeFrom(scrollX)
        mTimeUpperBoundary = getTimeFrom(scrollX + width)
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
            mClickListener?.onEventLongClicked(
                selectedChannel, 0, currentChannel, this.currentEvent
            )
        }
        //notifyListener()
        redraw()
        isEventHandled = true
        waitForLongClick = false
        return true
    }

    private fun handleNavigationEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        var rv = false
        mTimeLowerBoundary = getTimeFrom(scrollX)
        mTimeUpperBoundary = getTimeFrom(scrollX + programAreaWidth)
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if ((currentEvent?.end ?: 0) > mTimeUpperBoundary) {
                    moveMinutesForward(30)
                } else {
                    showNextEvent()
                }
                rv = true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                rv = when {
                    !isBackMoveable() -> if ((currentEvent?.start
                            ?: 0) > mTimeLowerBoundary
                    ) showPreviousEvent() else false

                    (currentEvent?.start?.plus(TimeUnit.MINUTES.toMillis(30))
                        ?: 0) < mTimeLowerBoundary -> {
                        moveMinutesBack(30)
                        true
                    }

                    else -> {
                        showPreviousEvent()
                    }
                }
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                prevChannel()
                rv = true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                nextChannel()
                rv = true
            }
        }
        notifyListener()
        redraw()
        return rv
    }

    private fun handleClickEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (isLongPressed) {
                    isLongPressed = false
                } else {
                    mClickListener?.onChannelClicked(
                        selectedChannel, currentChannel
                    )
                }
            } else {
                if (event.isLongPress) {
                    isLongPressed = true
                    mClickListener?.onEventLongClicked(
                        selectedChannel, 0, currentChannel, this.currentEvent
                    )
                }
            }
            return true
        }
        return false
    }

    fun isBackMoveable(): Boolean {
        val dT30 = TimeUnit.MINUTES.toMillis(30)
        val dX = round(dT30 / mMillisPerPixel.toFloat()).toInt()
        return scrollX - dX > 0
    }

    fun nextChannel(shouldCallListener: Boolean = false) {
        val pair = findEventBelow(selectedChannel)
        pair.run {
            selectedChannel = first
            selectedEvent = second
            optimizeVisibility()
            if (shouldCallListener) {
                mClickListener?.onChannelClicked(
                    selectedChannel, currentChannel
                )
            }
        }
    }

    fun prevChannel(shouldCallListener: Boolean = false) {
        val pair = findEventAbove(selectedChannel)
        pair.run {
            selectedChannel = first
            selectedEvent = second
            optimizeVisibility()
            if (shouldCallListener) {
                mClickListener?.onChannelClicked(
                    selectedChannel, currentChannel
                )
            }
        }
    }

    private fun findNextEvent(): Int {
        val programs = dataProvider?.eventsOfChannel(selectedChannel) ?: return -1
        val nextPos = selectedEvent + 1
        if (nextPos >= programs.size) {
            return selectedEvent
        }
        return nextPos
    }

    private fun findPreviousEvent(): Int {
        val prevPos = selectedEvent - 1
        if (prevPos < 0) {
            return -1
        }
        return prevPos
    }

    private fun findEventAbove(channel: Int): Pair<Int, Int> {
        val previous = channel - 1
        if (previous < 0) return Pair(selectedChannel, selectedEvent)
        val current = this.currentEvent ?: return Pair(selectedChannel, selectedEvent)
        val lowerBoundary = max(mTimeLowerBoundary, current.start)
        val upperBoundary = min(mTimeUpperBoundary, current.end)
        val eventMiddleTime = (lowerBoundary + upperBoundary) / 2
        val event = getProgramPosition(previous, eventMiddleTime)
        return if (event != -1) {
            Pair(previous, event)
        } else {
            Pair(previous, -1)
        }
    }

    private fun findEventBelow(channel: Int): Pair<Int, Int> {
        val next = channel + 1
        if (next >= dataProvider.itemCount) return Pair(selectedChannel, selectedEvent)
        val current = this.currentEvent ?: return Pair(selectedChannel, selectedEvent)
        val lowerBoundary = max(mTimeLowerBoundary, current.start)
        val upperBoundary = min(mTimeUpperBoundary, current.end)
        val eventMiddleTime = (lowerBoundary + upperBoundary) / 2
        val event = getProgramPosition(next, eventMiddleTime)
        return if (event != -1) {
            Pair(next, event)
        } else {
            Pair(next, -1)
        }
    }

    private fun showNextEvent() {
        mTimeLowerBoundary = getTimeFrom(scrollX)
        mTimeUpperBoundary = getTimeFrom(scrollX + programAreaWidth)
        val nextEvent = findNextEvent()
        if (nextEvent != -1) {
            this.selectedEvent = nextEvent
            var dX: Int
            var dT: Long
            val currentStart = currentEvent?.start ?: 0
            dT = currentStart - mTimeLowerBoundary

            dX = round(dT / mMillisPerPixel.toFloat()).toInt()
            if (scrollX + dX < 0) {
                dX = 0 - scrollX
            }
            if (scrollX + dX > mMaxHorizontalScroll) {
                dX = mMaxHorizontalScroll - scrollX
            }
            mScroller.startScroll(scrollX, scrollY, dX, 0, 400)
        }
    }

    private fun showPreviousEvent(): Boolean {
        mTimeLowerBoundary = getTimeFrom(scrollX)
        mTimeUpperBoundary = getTimeFrom(scrollX + programAreaWidth)
        val prevEvent = findPreviousEvent()
        return if (prevEvent != -1) {
            this.selectedEvent = prevEvent
            var dX: Int
            var dT: Long
            val currentStart = currentEvent?.start ?: 0
            dT = currentStart - mTimeLowerBoundary
            if (dT < 0 && abs(dT) > TimeUnit.MINUTES.toMillis(30)) {
                dT = -TimeUnit.MINUTES.toMillis(30)
            }
            dX = round(dT / mMillisPerPixel.toFloat()).toInt()
            if (scrollX + dX < 0) {
                dX = 0 - scrollX
            }
            if (scrollX + dX > mMaxHorizontalScroll) {
                dX = mMaxHorizontalScroll - scrollX
            }
            mScroller.startScroll(scrollX, scrollY, dX, 0, 400)
            true
        } else false
    }

    private fun moveMinutesBack(minutes: Long) {
        mTimeLowerBoundary = getTimeFrom(scrollX)
        mTimeUpperBoundary = getTimeFrom(scrollX + programAreaWidth)
        val dT = -TimeUnit.MINUTES.toMillis(minutes)
        var dX = round(dT / mMillisPerPixel.toFloat()).toInt()
        if (scrollX + dX < 0) {
            dX = 0 - scrollX
        }
        if (scrollX + dX > mMaxHorizontalScroll) {
            dX = mMaxHorizontalScroll - scrollX
        }
        mScroller.startScroll(scrollX, scrollY, dX, 0, 200)
    }

    private fun moveMinutesForward(minutes: Long) {
        mTimeLowerBoundary = getTimeFrom(scrollX)
        mTimeUpperBoundary = getTimeFrom(scrollX + programAreaWidth)
        val dT = TimeUnit.MINUTES.toMillis(minutes)
        var dX = round(dT / mMillisPerPixel.toFloat()).toInt()
        if (scrollX + dX < 0) {
            dX = 0 - scrollX
        }
        if (scrollX + dX > mMaxHorizontalScroll) {
            dX = mMaxHorizontalScroll - scrollX
        }
        mScroller.startScroll(scrollX, scrollY, dX, 0, 200)
    }

    fun optimizeVisibility(vertical: Boolean = true, isTouched: Boolean = false) {
        var dT: Long
        var dX = 0
        var dY = 0

        // calculate optimal Y position
        if (vertical) {
            val minYVisible =
                scrollY // is 0 when scrolled completely to top (first channel fully visible)
            val currentChannelTop =
                mTimeBarHeight + selectedChannel * (mChannelLayoutHeight + mChannelLayoutMargin)
            val bottomPos = minYVisible + height - mChannelLayoutHeight
            dY = currentChannelTop - minYVisible - mTimeBarHeight
            if (isTouched) {
                dY = 0
                if (currentChannelTop > bottomPos) dY =
                    bottomPos - currentChannelTop + mTimeBarHeight
                else if (currentChannelTop - (mChannelLayoutHeight + mChannelLayoutMargin) < minYVisible) dY =
                    currentChannelTop - minYVisible - mTimeBarHeight
            }
        }

        if (dX != 0 || dY != 0) {
            mScroller.startScroll(scrollX, scrollY, dX, dY, 400)
        }

    }

    private fun startScroll(dX: Int, withAnimation: Boolean) {
        var newDx = 0
        if (dX < 0) {
            newDx = 0 - scrollX
        }
        if (scrollX + dX > mMaxHorizontalScroll) {
            newDx = mMaxHorizontalScroll - scrollX
        }
//        if (dX != 0) {
//            val upperTime = now + TimeUnit.DAYS.toMillis(1)
//            val lowerTime = now - TimeUnit.MINUTES.toMillis(30)
//            var time = getTimeFrom(scrollX + dX)
//            if (time < lowerTime) {
//                time = lowerTime
//            } else if (time > upperTime) {
//                time = upperTime
//            }
//            newDx = getXFrom(time) - scrollX
//        }
        if (newDx != 0) {
            mScroller.startScroll(scrollX, scrollY, newDx, 0, if (withAnimation) 400 else 0)
        }
    }

    /*
     *It will return dX from Now as per given time only if it is in time boundary Otherwise it will return respective time boundary
     *
     *
     */
    fun getDxFromNow(time: Long): Int {
        return (time / mMillisPerPixel).toInt()
    }

    private inner class OnGestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapUp(e: MotionEvent): Boolean {

            // This is absolute coordinate on screen not taking scroll into account.
            val x = e.x.toInt()
            val y = e.y.toInt()

            // Adding scroll to clicked coordinate
            val scrollX = scrollX + x
            val scrollY = scrollY + y
//            requestFocus()
            val channelPosition = getChannelPosition(scrollY)
            if (channelPosition != -1 && mClickListener != null) {
                selectedChannel = channelPosition
                if (calculateChannelsHitArea().contains(x, y)) {
                    // Channel area is clicked
                    mClickListener!!.onChannelClicked(
                        channelPosition, currentChannel
                    )
                    val lowerBoundary = max(
                        mTimeLowerBoundary, currentEvent?.start ?: now
                    )
                    val upperBoundary = Math.min(
                        mTimeUpperBoundary, currentEvent?.end ?: now
                    )
                    val eventMiddleTime = (lowerBoundary + upperBoundary) / 2
                    selectedEvent = getProgramPosition(selectedChannel, eventMiddleTime)
                    if (selectedEvent == -1) {
                        gotoNow()
                    } else {
                        optimizeVisibility(isTouched = true)
                    }
                    notifyListener()
                } else if (calculateProgramsHitArea().contains(x, y)) {
                    // Event area is clicked
                    val programPosition = getProgramPosition(
                        channelPosition,
                        getTimeFrom(getScrollX() + x - calculateProgramsHitArea().left)
                    )
                    if (programPosition != -1) {
                        selectedEvent = programPosition
                        if (selectedEvent == -1) {
                            gotoNow()
                        } else {
                            optimizeVisibility(isTouched = true)
                        }
                        notifyListener()
                        mClickListener?.onEventClicked(
                            channelPosition,
                            programPosition,
                            currentChannel,
                            dataProvider?.eventOfChannelAt(selectedChannel, programPosition)
                        )
                    }
                }
                redraw()
            }

            return true
        }

        override fun onLongPress(e: MotionEvent) {
            // This is absolute coordinate on screen not taking scroll into account.
            val x = e.x.toInt()
            val y = e.y.toInt()

            // Adding scroll to clicked coordinate
            val scrollX = scrollX + x
            val scrollY = scrollY + y

            val channelPosition = getChannelPosition(scrollY)
            if (channelPosition != -1 && mClickListener != null) {
                selectedChannel = channelPosition
                if (calculateChannelsHitArea().contains(x, y)) {
                    // Channel area is clicked
                    mClickListener!!.onChannelClicked(
                        channelPosition, currentChannel
                    )
                    val lowerBoundary = max(
                        mTimeLowerBoundary, currentEvent?.start ?: 0
                    )
                    val upperBoundary = min(
                        mTimeUpperBoundary, currentEvent?.end ?: 0
                    )
                    val eventMiddleTime = (lowerBoundary + upperBoundary) / 2
                    selectedEvent = getProgramPosition(selectedChannel, eventMiddleTime)
                    if (selectedEvent == -1) {
                        gotoNow()
                    } else {
                        optimizeVisibility(isTouched = true)
                    }
                    notifyListener()
                } else if (calculateProgramsHitArea().contains(x, y)) {
                    // Event area is clicked
                    val programPosition = getProgramPosition(
                        channelPosition,
                        getTimeFrom(getScrollX() + x - calculateProgramsHitArea().left)
                    )
                    if (programPosition != -1) {
                        selectedEvent = programPosition
                        if (selectedEvent != -1) {
                            optimizeVisibility(isTouched = true)
                            mClickListener?.onEventLongClicked(
                                selectedChannel, programPosition, currentChannel, currentEvent
                            )
                        }
                        notifyListener()
                    }
                }
                redraw()
            }
        }


        override fun onScroll(
            e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float
        ): Boolean {
            var dx = distanceX.toInt()
            var dy = distanceY.toInt()
            val x = scrollX
            val y = scrollY


            // Avoid over scrolling
            if (x + dx < 0) {
                dx = 0 - x
            }
            if (y + dy < 0) {
                dy = 0 - y
            }
            if (x + dx > mMaxHorizontalScroll) {
                dx = mMaxHorizontalScroll - x
            }
            if (y + dy > mMaxVerticalScroll) {
                dy = mMaxVerticalScroll - y
            }

            scrollBy(dx, dy)
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {

            mScroller.fling(
                scrollX,
                scrollY,
                -vX.toInt(),
                -vY.toInt(),
                0,
                mMaxHorizontalScroll,
                0,
                mMaxVerticalScroll
            )

            redraw()
            return true
        }

        override fun onDown(e: MotionEvent): Boolean {
            if (!mScroller.isFinished) {
                mScroller.forceFinished(true)
                return true
            }
            return true
        }
    }

    interface ClickListener {
        fun onChannelClicked(position: Int, channel: Channel?)
        fun onEventSelected(channel: Channel?, program: Event?)
        fun onEventClicked(
            channelPosition: Int, programPosition: Int, channel: Channel?, program: Event?
        )

        fun onEventLongClicked(
            channelPosition: Int, programPosition: Int, channel: Channel?, program: Event?
        )
    }
}