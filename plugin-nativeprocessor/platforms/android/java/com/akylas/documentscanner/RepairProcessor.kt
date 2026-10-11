package com.akylas.documentscanner

import android.graphics.*
import org.json.JSONArray

/** Masks are stored in the crop's unrotated pixel space and projected back to the source. */
object RepairProcessor {
    init { System.loadLibrary("document_detector") }
    private external fun nativeInpaint(source: Bitmap, mask: Bitmap, output: Bitmap)

    fun drawStrokes(canvas: Canvas, strokes: JSONArray, width: Float, height: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (i in 0 until strokes.length()) {
            val stroke = strokes.getJSONObject(i)
            val points = stroke.getJSONArray("points")
            if (points.length() == 0) continue
            paint.strokeWidth = (stroke.getDouble("radius").toFloat() * width * 2).coerceAtLeast(1f)
            val path = Path()
            val first = points.getJSONArray(0)
            val x = first.getDouble(0).toFloat() * width
            val y = first.getDouble(1).toFloat() * height
            path.moveTo(x, y)
            for (j in 1 until points.length()) {
                val point = points.getJSONArray(j)
                path.lineTo(point.getDouble(0).toFloat() * width, point.getDouble(1).toFloat() * height)
            }
            if (points.length() == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x, y, paint.strokeWidth / 2, paint)
                paint.style = Paint.Style.STROKE
            } else canvas.drawPath(path, paint)
        }
    }

    fun preview(source: Bitmap, strokes: JSONArray): Bitmap {
        val mask = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        try {
            drawStrokes(Canvas(mask), strokes, source.width.toFloat(), source.height.toFloat(), Color.WHITE)
            val output = source.copy(Bitmap.Config.ARGB_8888, true)
            try { nativeInpaint(source, mask, output) } catch (error: Throwable) { output.recycle(); throw error }
            return output
        } finally { mask.recycle() }
    }

    fun applyRegions(source: Bitmap, regions: JSONArray): Bitmap {
        val mask = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(mask)
            for (i in 0 until regions.length()) {
                val region = regions.getJSONObject(i)
                val w = region.getDouble("width").toFloat()
                val h = region.getDouble("height").toFloat()
                require(w > 0 && h > 0)
                val quad = region.getJSONArray("quad")
                val target = FloatArray(8)
                for (j in 0..3) {
                    target[j * 2] = quad.getJSONArray(j).getDouble(0).toFloat()
                    target[j * 2 + 1] = quad.getJSONArray(j).getDouble(1).toFloat()
                }
                val matrix = Matrix()
                require(matrix.setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, target, 0, 4))
                canvas.save()
                canvas.concat(matrix)
                canvas.clipRect(0f, 0f, w, h)
                drawStrokes(canvas, region.getJSONArray("strokes"), w, h, Color.WHITE)
                canvas.restore()
            }
            val output = source.copy(Bitmap.Config.ARGB_8888, true)
            try { nativeInpaint(source, mask, output) } catch (error: Throwable) { output.recycle(); throw error }
            return output
        } finally { mask.recycle() }
    }
}
