package com.akylas.documentscanner

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.akylas.documentscanner.utils.FunctionCallback
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.math.*

/** Single-finger drawing, two-finger pan/zoom. All stroke coordinates are image-relative. */
class RepairView(context: Context) : View(context) {
    var listener: FunctionCallback? = null
    var brushSize: Float = 20f
    var comparing = false
        set(value) { field = value; invalidate() }
    var maskColor: Int = 0x667c73d1
    private var source: Bitmap? = null
    private var preview: Bitmap? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val strokes = mutableListOf<JSONObject>()
    private val redos = mutableListOf<JSONObject>()
    private var points: JSONArray? = null
    private var radius = 0f
    private var busy = false
    private var disposed = false
    private var rotation = 0f
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var lastSpan = 0f
    private var multiTouch = false
    private val matrix = Matrix()
    private val inverse = Matrix()

    fun setColorMatrix(values: String) {
        val array = JSONArray(values)
        bitmapPaint.colorFilter = if (array.length() == 20) ColorMatrixColorFilter(FloatArray(20) { array.getDouble(it).toFloat() }) else null
        invalidate()
    }

    fun loadImage(path: String, degrees: Float) {
        rotation = degrees
        busy = true
        notifyState()
        executor.execute {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > 1800) sample *= 2
                val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                    inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
                }) ?: error("Unable to read image")
                post {
                    if (disposed) bitmap.recycle() else {
                        source = bitmap; busy = false; updateMatrix(); invalidate(); notifyState()
                    }
                }
            } catch (error: Exception) { post { busy = false; if (!disposed) listener?.onResult(error, null) } }
        }
    }

    private fun updateMatrix() {
        val bitmap = source ?: return
        val rotated = abs(rotation.toInt()) % 180 == 90
        val w = if (rotated) bitmap.height else bitmap.width
        val h = if (rotated) bitmap.width else bitmap.height
        val fit = min(width.toFloat() / w, height.toFloat() / h)
        matrix.reset()
        matrix.postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
        matrix.postRotate(rotation)
        matrix.postScale(fit * zoom, fit * zoom)
        matrix.postTranslate(width / 2f + panX, height / 2f + panY)
        matrix.invert(inverse)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { updateMatrix() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = source ?: return
        canvas.save()
        canvas.concat(matrix)
        canvas.drawBitmap(if (comparing) bitmap else preview ?: bitmap, 0f, 0f, bitmapPaint)
        if (!comparing) {
            canvas.clipRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
            points?.let {
                RepairProcessor.drawStrokes(canvas, JSONArray().put(JSONObject().put("points", it).put("radius", radius)), bitmap.width.toFloat(), bitmap.height.toFloat(), maskColor)
            }
        }
        canvas.restore()
    }

    private fun append(x: Float, y: Float) {
        val bitmap = source ?: return
        val position = floatArrayOf(x, y)
        inverse.mapPoints(position)
        // Do not snap touches in the surrounding letterbox to the document edge.
        if (position[0] < 0 || position[1] < 0 || position[0] > bitmap.width || position[1] > bitmap.height) return
        points?.put(JSONArray().put(position[0] / bitmap.width).put(position[1] / bitmap.height))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (source == null || busy || comparing) return true
        parent?.requestDisallowInterceptTouchEvent(true)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                multiTouch = false
                points = JSONArray()
                val unit = floatArrayOf(brushSize * resources.displayMetrics.density / 2, 0f)
                inverse.mapVectors(unit)
                radius = hypot(unit[0], unit[1]) / source!!.width
                append(event.x, event.y)
                notifyState()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger cancels the tentative stroke, avoiding accidental marks while zooming.
                points = null; multiTouch = true
                lastFocusX = (event.getX(0) + event.getX(1)) / 2
                lastFocusY = (event.getY(0) + event.getY(1)) / 2
                lastSpan = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 2 && multiTouch) {
                    val fx = (event.getX(0) + event.getX(1)) / 2
                    val fy = (event.getY(0) + event.getY(1)) / 2
                    val span = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
                    val nextZoom = (zoom * if (lastSpan > 0) span / lastSpan else 1f).coerceIn(1f, 10f)
                    val ratio = nextZoom / zoom
                    panX = fx - width / 2f - (lastFocusX - width / 2f - panX) * ratio
                    panY = fy - height / 2f - (lastFocusY - height / 2f - panY) * ratio
                    zoom = nextZoom
                    panX = panX.coerceIn(-width * zoom / 2, width * zoom / 2)
                    panY = panY.coerceIn(-height * zoom / 2, height * zoom / 2)
                    if (zoom == 1f) { panX = 0f; panY = 0f }
                    lastFocusX = fx; lastFocusY = fy; lastSpan = span
                    updateMatrix()
                } else if (!multiTouch) {
                    for (i in 0 until event.historySize) append(event.getHistoricalX(i), event.getHistoricalY(i))
                    append(event.x, event.y)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!multiTouch) {
                    append(event.x, event.y)
                    points?.takeIf { it.length() > 0 }?.let {
                        strokes.add(JSONObject().put("points", it).put("radius", radius))
                        redos.clear()
                        points = null
                        render()
                    }
                }
                points = null; multiTouch = false; notifyState()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> { points = null; multiTouch = false; notifyState() }
        }
        invalidate()
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    fun undo() { if (!busy && strokes.isNotEmpty()) { redos.add(strokes.removeAt(strokes.lastIndex)); render() } }
    fun redo() { if (!busy && redos.isNotEmpty()) { strokes.add(redos.removeAt(redos.lastIndex)); render() } }
    fun getStrokes(): String = JSONArray(strokes).toString()
    fun resetZoom() { zoom = 1f; panX = 0f; panY = 0f; updateMatrix(); invalidate() }
    private fun notifyState() {
        if (!disposed) listener?.onResult(null, JSONObject().put("busy", busy).put("drawing", points != null).put("undos", strokes.size).put("redos", redos.size).toString())
    }
    private fun render() {
        val bitmap = source ?: return
        val snapshot = JSONArray(strokes)
        busy = true; notifyState()
        executor.execute {
            try {
                val result = RepairProcessor.preview(bitmap, snapshot)
                post {
                    if (disposed) result.recycle() else {
                        preview?.recycle(); preview = result; busy = false; invalidate(); notifyState()
                    }
                }
            } catch (error: Exception) {
                post { busy = false; if (!disposed) { notifyState(); listener?.onResult(error, null) } }
            }
        }
    }
    fun release() {
        disposed = true; listener = null
        executor.execute { source?.recycle(); source = null }
        executor.shutdown()
        preview?.recycle(); preview = null
    }
}
