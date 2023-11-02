package com.devs.vectorchildfinder

import android.content.res.Resources
import android.content.res.Resources.Theme
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.util.Xml
import androidx.core.R
import com.devs.vectorchildfinder.GradientColorCompat.GradientType.Linear
import com.devs.vectorchildfinder.GradientColorCompat.GradientType.Radial
import com.devs.vectorchildfinder.GradientColorCompat.GradientType.Sweep
import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

class GradientColorCompat {
    private var startX: Float = 0f
    private var startY: Float = 0f
    private var endX: Float = 0f
    private var endY: Float = 0f
    private var gradientType: GradientType = Linear

    sealed class GradientType {
        object Linear : GradientType()
        object Radial : GradientType()
        object Sweep : GradientType()
    }

    private val colors = mutableListOf<GradientItem>()

    data class GradientItem(val color: Int, val offset: Float)

    fun getShader(): Shader {
        return when (gradientType) {
            Linear -> {
                LinearGradient(
                    /* x0 = */ startX,
                    /* y0 = */ startY,
                    /* x1 = */ endX,
                    /* y1 = */ endY,
                    /* colors = */ colors.map { it.color }.toIntArray(),
                    /* positions = */ null,
                    /* tile = */ Shader.TileMode.CLAMP
                )
            }
            Radial -> TODO()
            Sweep -> TODO()
        }
    }

    private fun inflate(res: Resources, parser: XmlPullParser, attrs: AttributeSet, theme: Theme) {
        val a = theme.obtainStyledAttributes(attrs, R.styleable.GradientColor, 0, 0)
        try {
            startX = dpToPx(a, R.styleable.GradientColor_android_startX, startX, res)
            startY = dpToPx(a, R.styleable.GradientColor_android_startY, startY, res)
            endX = dpToPx(a, R.styleable.GradientColor_android_endX, endX, res)
            endY = dpToPx(a, R.styleable.GradientColor_android_endY, endY, res)
            val type = a.getInt(R.styleable.GradientColor_android_type, 0)
            gradientType = when (type) {
                0 -> Linear
                1 -> Radial
                2 -> Sweep
                else -> Linear
            }
            inflateInternal(parser, attrs, theme)
        } finally {
            a.recycle()
        }
    }

    private fun dpToPx(typedArray: TypedArray, attr: Int, defValue: Float, res: Resources): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            typedArray.getFloat(attr, defValue),
            res.displayMetrics
        )
    }

    private fun inflateInternal(parser: XmlPullParser, attrs: AttributeSet, theme: Theme) {
        var type = parser.eventType

        val innerDepth = parser.depth + 1
        while (type != XmlPullParser.END_DOCUMENT && (parser.depth >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (type == XmlPullParser.START_TAG) {
                val tagName = parser.name
                if (tagName == "item") {
                    inflateItem(attrs, theme)
                }
            }
            type = parser.next()
        }
    }

    private fun inflateItem(attrs: AttributeSet, theme: Resources.Theme) {
        val a = theme.obtainStyledAttributes(attrs, R.styleable.GradientColorItem, 0, 0)
        try {
            val color = a.getColor(R.styleable.GradientColorItem_android_color, Color.BLACK)
            val offset = a.getFloat(R.styleable.GradientColorItem_android_offset, 0f)
            colors.add(GradientItem(color, offset))
        } finally {
            a.recycle()
        }
    }

    companion object {
        fun createFromXml(res: Resources, resId: Int, theme: Theme): GradientColorCompat? {
            try {
                val parser = res.getXml(resId)
                val attrs = Xml.asAttributeSet(parser)
                var type: Int = parser.next()
                while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
                    type = parser.next()
                }
                if (type != XmlPullParser.START_TAG) {
                    throw XmlPullParserException("No start tag found")
                }
                return createFromXmlInner(res, parser, attrs, theme)
            } catch (e: XmlPullParserException) {
                return null
            } catch (e: IOException) {
                return null
            }
        }

        private fun createFromXmlInner(
            res: Resources,
            parser: XmlPullParser,
            attrs: AttributeSet,
            theme: Theme
        ): GradientColorCompat {
            val name = parser.name
            if (name != "gradient") {
                throw XmlPullParserException(parser.positionDescription + ": invalid gradient color tag " + name)
            }
            return GradientColorCompat().apply {
                inflate(res, parser, attrs, theme)
            }
        }
    }
}