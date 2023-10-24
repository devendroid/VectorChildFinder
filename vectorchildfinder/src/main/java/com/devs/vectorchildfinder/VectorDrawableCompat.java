package com.devs.vectorchildfinder;

/**
 * Created by ${Deven} on 1/31/18.
 */

/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.LayoutDirection;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.collection.ArrayMap;
import androidx.core.graphics.drawable.DrawableCompat;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;


public class VectorDrawableCompat extends VectorDrawableCommon {
    static final String LOGTAG = "VectorDrawableCompat";

    static final PorterDuff.Mode DEFAULT_TINT_MODE = PorterDuff.Mode.SRC_IN;

    private static final String SHAPE_CLIP_PATH = "clip-path";
    private static final String SHAPE_GROUP = "group";
    private static final String SHAPE_PATH = "path";
    private static final String SHAPE_VECTOR = "vector";

    private static final int LINECAP_BUTT = 0;
    private static final int LINECAP_ROUND = 1;
    private static final int LINECAP_SQUARE = 2;

    private static final int LINEJOIN_MITER = 0;
    private static final int LINEJOIN_ROUND = 1;
    private static final int LINEJOIN_BEVEL = 2;

    // Cap the bitmap size, such that it won't hurt the performance too much
    // and it won't crash due to a very large scale.
    // The drawable will look blurry above this size.
    private static final int MAX_CACHED_BITMAP_SIZE = 2048;

    private static final boolean DBG_VECTOR_DRAWABLE = false;

    private VectorDrawableCompatState mVectorState;

    private PorterDuffColorFilter mTintFilter;
    private ColorFilter mColorFilter;

    private boolean mMutated;

    // AnimatedVectorDrawable needs to turn off the cache all the time, otherwise,
    // caching the bitmap by default is allowed.
    private boolean mAllowCaching = true;

    // The Constant state associated with the <code>mDelegateDrawable</code>.
    private ConstantState mCachedConstantStateDelegate;

    // Temp variable, only for saving "new" operation at the draw() time.
    private final float[] mTmpFloats = new float[9];
    private final Matrix mTmpMatrix = new Matrix();
    private final Rect mTmpBounds = new Rect();

    VectorDrawableCompat() {
        mVectorState = new VectorDrawableCompatState();
    }

    VectorDrawableCompat(@NonNull VectorDrawableCompatState state) {
        mVectorState = state;
        mTintFilter = updateTintFilter(mTintFilter, state.mTint, state.mTintMode);
    }

    @Override
    public Drawable mutate() {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.mutate();
            return this;
        }

        if (!mMutated && super.mutate() == this) {
            mVectorState = new VectorDrawableCompatState(mVectorState);
            mMutated = true;
        }
        return this;
    }

    public Object getTargetByName(String name) {
        return mVectorState.mVPathRenderer.mVGTargetsMap.get(name);
    }

    @Override
    public ConstantState getConstantState() {
        if (mDelegateDrawable != null) {
            // Such that the configuration can be refreshed.
            return new VectorDrawableDelegateState(mDelegateDrawable.getConstantState());
        }
        mVectorState.mChangingConfigurations = getChangingConfigurations();
        return mVectorState;
    }

    @Override
    public void draw(Canvas canvas) {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.draw(canvas);
            return;
        }
        // We will offset the bounds for drawBitmap, so copyBounds() here instead
        // of getBounds().
        copyBounds(mTmpBounds);
        if (mTmpBounds.width() <= 0 || mTmpBounds.height() <= 0) {
            // Nothing to draw
            return;
        }

        // Color filters always override tint filters.
        final ColorFilter colorFilter = (mColorFilter == null ? mTintFilter : mColorFilter);

        // The imageView can scale the canvas in different ways, in order to
        // avoid blurry scaling, we have to draw into a bitmap with exact pixel
        // size first. This bitmap size is determined by the bounds and the
        // canvas scale.
        canvas.getMatrix(mTmpMatrix);
        mTmpMatrix.getValues(mTmpFloats);
        float canvasScaleX = Math.abs(mTmpFloats[Matrix.MSCALE_X]);
        float canvasScaleY = Math.abs(mTmpFloats[Matrix.MSCALE_Y]);

        float canvasSkewX = Math.abs(mTmpFloats[Matrix.MSKEW_X]);
        float canvasSkewY = Math.abs(mTmpFloats[Matrix.MSKEW_Y]);

        // When there is any rotation / skew, then the scale value is not valid.
        if (canvasSkewX != 0 || canvasSkewY != 0) {
            canvasScaleX = 1.0f;
            canvasScaleY = 1.0f;
        }

        int scaledWidth = (int) (mTmpBounds.width() * canvasScaleX);
        int scaledHeight = (int) (mTmpBounds.height() * canvasScaleY);
        scaledWidth = Math.min(MAX_CACHED_BITMAP_SIZE, scaledWidth);
        scaledHeight = Math.min(MAX_CACHED_BITMAP_SIZE, scaledHeight);

        if (scaledWidth <= 0 || scaledHeight <= 0) {
            return;
        }

        final int saveCount = canvas.save();
        canvas.translate(mTmpBounds.left, mTmpBounds.top);

        // Handle RTL mirroring.
        final boolean needMirroring = needMirroring();
        if (needMirroring) {
            canvas.translate(mTmpBounds.width(), 0);
            canvas.scale(-1.0f, 1.0f);
        }

        // At this point, canvas has been translated to the right position.
        // And we use this bound for the destination rect for the drawBitmap, so
        // we offset to (0, 0);
        mTmpBounds.offsetTo(0, 0);

        mVectorState.createCachedBitmapIfNeeded(scaledWidth, scaledHeight);
        if (!mAllowCaching) {
            mVectorState.updateCachedBitmap(scaledWidth, scaledHeight);
        } else {
            if (!mVectorState.canReuseCache()) {
                mVectorState.updateCachedBitmap(scaledWidth, scaledHeight);
                mVectorState.updateCacheStates();
            }
        }
        mVectorState.drawCachedBitmapWithRootAlpha(canvas, colorFilter, mTmpBounds);
        canvas.restoreToCount(saveCount);
    }

    @Override
    public int getAlpha() {
        if (mDelegateDrawable != null) {
            return DrawableCompat.getAlpha(mDelegateDrawable);
        }

        return mVectorState.mVPathRenderer.getRootAlpha();
    }

    @Override
    public void setAlpha(int alpha) {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.setAlpha(alpha);
            return;
        }

        if (mVectorState.mVPathRenderer.getRootAlpha() != alpha) {
            mVectorState.mVPathRenderer.setRootAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.setColorFilter(colorFilter);
            return;
        }

        mColorFilter = colorFilter;
        invalidateSelf();
    }

    /**
     * Ensures the tint filter is consistent with the current tint color and
     * mode.
     */
    PorterDuffColorFilter updateTintFilter(PorterDuffColorFilter tintFilter, ColorStateList tint,
                                           PorterDuff.Mode tintMode) {
        if (tint == null || tintMode == null) {
            return null;
        }
        // setMode, setColor of PorterDuffColorFilter are not public method in SDK v7.
        // Therefore we create a new one all the time here. Don't expect this is called often.
        final int color = tint.getColorForState(getState(), Color.TRANSPARENT);
        return new PorterDuffColorFilter(color, tintMode);
    }

    @SuppressLint("NewApi")
    @Override
    public void setTint(int tint) {
        if (mDelegateDrawable != null) {
            DrawableCompat.setTint(mDelegateDrawable, tint);
            return;
        }

        setTintList(ColorStateList.valueOf(tint));
    }

    @Override
    public void setTintList(ColorStateList tint) {
        if (mDelegateDrawable != null) {
            DrawableCompat.setTintList(mDelegateDrawable, tint);
            return;
        }

        final VectorDrawableCompatState state = mVectorState;
        if (state.mTint != tint) {
            state.mTint = tint;
            mTintFilter = updateTintFilter(mTintFilter, tint, state.mTintMode);
            invalidateSelf();
        }
    }

    @Override
    public void setTintMode(PorterDuff.Mode tintMode) {
        if (mDelegateDrawable != null) {
            DrawableCompat.setTintMode(mDelegateDrawable, tintMode);
            return;
        }

        final VectorDrawableCompatState state = mVectorState;
        if (state.mTintMode != tintMode) {
            state.mTintMode = tintMode;
            mTintFilter = updateTintFilter(mTintFilter, state.mTint, tintMode);
            invalidateSelf();
        }
    }

    @Override
    public boolean isStateful() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.isStateful();
        }

        return super.isStateful() || (mVectorState != null && mVectorState.mTint != null
                && mVectorState.mTint.isStateful());
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.setState(stateSet);
        }

        final VectorDrawableCompatState state = mVectorState;
        if (state.mTint != null && state.mTintMode != null) {
            mTintFilter = updateTintFilter(mTintFilter, state.mTint, state.mTintMode);
            invalidateSelf();
            return true;
        }
        return false;
    }

    @Override
    public int getOpacity() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getOpacity();
        }

        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getIntrinsicWidth();
        }

        return (int) mVectorState.mVPathRenderer.mBaseWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getIntrinsicHeight();
        }

        return (int) mVectorState.mVPathRenderer.mBaseHeight;
    }

    // Don't support re-applying themes. The initial theme loading is working.
    @Override
    public boolean canApplyTheme() {
        if (mDelegateDrawable != null) {
            DrawableCompat.canApplyTheme(mDelegateDrawable);
        }

        return false;
    }

    @Override
    public boolean isAutoMirrored() {
        if (mDelegateDrawable != null) {
            return DrawableCompat.isAutoMirrored(mDelegateDrawable);
        }
        return mVectorState.mAutoMirrored;
    }

    @Override
    public void setAutoMirrored(boolean mirrored) {
        if (mDelegateDrawable != null) {
            DrawableCompat.setAutoMirrored(mDelegateDrawable, mirrored);
            return;
        }
        mVectorState.mAutoMirrored = mirrored;
    }
    /**
     * The size of a pixel when scaled from the intrinsic dimension to the viewport dimension. This
     * is used to calculate the path animation accuracy.
     *
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public float getPixelSize() {
        if (mVectorState == null && mVectorState.mVPathRenderer == null ||
                mVectorState.mVPathRenderer.mBaseWidth == 0 ||
                mVectorState.mVPathRenderer.mBaseHeight == 0 ||
                mVectorState.mVPathRenderer.mViewportHeight == 0 ||
                mVectorState.mVPathRenderer.mViewportWidth == 0) {
            return 1; // fall back to 1:1 pixel mapping.
        }
        float intrinsicWidth = mVectorState.mVPathRenderer.mBaseWidth;
        float intrinsicHeight = mVectorState.mVPathRenderer.mBaseHeight;
        float viewportWidth = mVectorState.mVPathRenderer.mViewportWidth;
        float viewportHeight = mVectorState.mVPathRenderer.mViewportHeight;
        float scaleX = viewportWidth / intrinsicWidth;
        float scaleY = viewportHeight / intrinsicHeight;
        return Math.min(scaleX, scaleY);
    }

    /**
     * Create a VectorDrawableCompat object.
     *
     * @param res   the resources.
     * @param resId the resource ID for VectorDrawableCompat object.
     * @param theme the theme of this vector drawable, it can be null.
     * @return a new VectorDrawableCompat or null if parsing error is found.
     */
    @Nullable
    public static VectorDrawableCompat create(@NonNull Resources res, @DrawableRes int resId,
                                              @Nullable Resources.Theme theme) {
//        if (Build.VERSION.SDK_INT >= 24) {
//            final VectorDrawableCompat drawable = new VectorDrawableCompat();
//            drawable.mDelegateDrawable = ResourcesCompat.getDrawable(res, resId, theme);
//            drawable.mCachedConstantStateDelegate = new VectorDrawableCompat.VectorDrawableDelegateState(
//                    drawable.mDelegateDrawable.getConstantState());
//            return drawable;
//        }

        try {
            final XmlPullParser parser = res.getXml(resId);
            final AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG &&
                    type != XmlPullParser.END_DOCUMENT) {
                // Empty loop
            }
            if (type != XmlPullParser.START_TAG) {
                throw new XmlPullParserException("No start tag found");
            }
            return createFromXmlInner(res, parser, attrs, theme);
        } catch (XmlPullParserException e) {
            Log.e(LOGTAG, "parser error", e);
        } catch (IOException e) {
            Log.e(LOGTAG, "parser error", e);
        }
        return null;
    }

    /**
     * Create a VectorDrawableCompat from inside an XML document using an optional
     * {@link Resources.Theme}. Called on a parser positioned at a tag in an XML
     * document, tries to create a Drawable from that tag. Returns {@code null}
     * if the tag is not a valid drawable.
     */
    @SuppressLint("NewApi")
    public static VectorDrawableCompat createFromXmlInner(Resources r, XmlPullParser parser,
                                                          AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        final VectorDrawableCompat drawable = new VectorDrawableCompat();
        drawable.inflate(r, parser, attrs, theme);
        return drawable;
    }

    static int applyAlpha(int color, float alpha) {
        int alphaBytes = Color.alpha(color);
        color &= 0x00FFFFFF;
        color |= ((int) (alphaBytes * alpha)) << 24;
        return color;
    }

    @SuppressLint("NewApi")
    @Override
    public void inflate(Resources res, XmlPullParser parser, AttributeSet attrs)
            throws XmlPullParserException, IOException {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.inflate(res, parser, attrs);
            return;
        }

        inflate(res, parser, attrs, null);
    }

    @Override
    public void inflate(Resources res, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme)
            throws XmlPullParserException, IOException {
        if (mDelegateDrawable != null) {
            DrawableCompat.inflate(mDelegateDrawable, res, parser, attrs, theme);
            return;
        }

        final VectorDrawableCompatState state = mVectorState;
        final VPathRenderer pathRenderer = new VPathRenderer();
        state.mVPathRenderer = pathRenderer;

        final TypedArray a = obtainAttributes(res, theme, attrs,
                AndroidResources.styleable_VectorDrawableTypeArray);

        updateStateFromTypedArray(a, parser);
        a.recycle();
        state.mChangingConfigurations = getChangingConfigurations();
        state.mCacheDirty = true;
        inflateInternal(res, parser, attrs, theme);

        mTintFilter = updateTintFilter(mTintFilter, state.mTint, state.mTintMode);
    }


    /**
     * Parses a {@link PorterDuff.Mode} from a tintMode
     * attribute's enum value.
     */
    private static PorterDuff.Mode parseTintModeCompat(int value, PorterDuff.Mode defaultMode) {
        switch (value) {
            case 3:
                return PorterDuff.Mode.SRC_OVER;
            case 5:
                return PorterDuff.Mode.SRC_IN;
            case 9:
                return PorterDuff.Mode.SRC_ATOP;
            case 14:
                return PorterDuff.Mode.MULTIPLY;
            case 15:
                return PorterDuff.Mode.SCREEN;
            case 16:
                if (Build.VERSION.SDK_INT >= 11) {
                    return PorterDuff.Mode.ADD;
                } else {
                    return defaultMode;
                }
            default:
                return defaultMode;
        }
    }

    private void updateStateFromTypedArray(TypedArray a, XmlPullParser parser)
            throws XmlPullParserException {
        final VectorDrawableCompatState state = mVectorState;
        final VPathRenderer pathRenderer = state.mVPathRenderer;

        // Account for any configuration changes.
        // state.mChangingConfigurations |= Utils.getChangingConfigurations(a);

        final int mode = TypedArrayUtils.getNamedInt(a, parser, "tintMode",
                AndroidResources.styleable_VectorDrawable_tintMode, -1);
        state.mTintMode = parseTintModeCompat(mode, PorterDuff.Mode.SRC_IN);

        final ColorStateList tint =
                a.getColorStateList(AndroidResources.styleable_VectorDrawable_tint);
        if (tint != null) {
            state.mTint = tint;
        }

        state.mAutoMirrored = TypedArrayUtils.getNamedBoolean(a, parser, "autoMirrored",
                AndroidResources.styleable_VectorDrawable_autoMirrored, state.mAutoMirrored);

        pathRenderer.mViewportWidth = TypedArrayUtils.getNamedFloat(a, parser, "viewportWidth",
                AndroidResources.styleable_VectorDrawable_viewportWidth,
                pathRenderer.mViewportWidth);

        pathRenderer.mViewportHeight = TypedArrayUtils.getNamedFloat(a, parser, "viewportHeight",
                AndroidResources.styleable_VectorDrawable_viewportHeight,
                pathRenderer.mViewportHeight);

        if (pathRenderer.mViewportWidth <= 0) {
            throw new XmlPullParserException(a.getPositionDescription() +
                    "<vector> tag requires viewportWidth > 0");
        } else if (pathRenderer.mViewportHeight <= 0) {
            throw new XmlPullParserException(a.getPositionDescription() +
                    "<vector> tag requires viewportHeight > 0");
        }

        pathRenderer.mBaseWidth = a.getDimension(
                AndroidResources.styleable_VectorDrawable_width, pathRenderer.mBaseWidth);
        pathRenderer.mBaseHeight = a.getDimension(
                AndroidResources.styleable_VectorDrawable_height, pathRenderer.mBaseHeight);
        if (pathRenderer.mBaseWidth <= 0) {
            throw new XmlPullParserException(a.getPositionDescription() +
                    "<vector> tag requires width > 0");
        } else if (pathRenderer.mBaseHeight <= 0) {
            throw new XmlPullParserException(a.getPositionDescription() +
                    "<vector> tag requires height > 0");
        }

        // shown up from API 11.
        final float alphaInFloat = TypedArrayUtils.getNamedFloat(a, parser, "alpha",
                AndroidResources.styleable_VectorDrawable_alpha, pathRenderer.getAlpha());
        pathRenderer.setAlpha(alphaInFloat);

        final String name = a.getString(AndroidResources.styleable_VectorDrawable_name);
        if (name != null) {
            pathRenderer.mRootName = name;
            pathRenderer.mVGTargetsMap.put(name, pathRenderer);
        }
    }

    private void inflateInternal(Resources res, XmlPullParser parser, AttributeSet attrs,
                                 Resources.Theme theme) throws XmlPullParserException, IOException {
        final VectorDrawableCompatState state = mVectorState;
        final VPathRenderer pathRenderer = state.mVPathRenderer;
        boolean noPathTag = true;

        // Use a stack to help to build the group tree.
        // The top of the stack is always the current group.
        final Stack<VGroup> groupStack = new Stack<VGroup>();
        groupStack.push(pathRenderer.mRootGroup);

        int eventType = parser.getEventType();
        final int innerDepth = parser.getDepth() + 1;

        // Parse everything until the end of the vector element.
        while (eventType != XmlPullParser.END_DOCUMENT
                && (parser.getDepth() >= innerDepth || eventType != XmlPullParser.END_TAG)) {
            if (eventType == XmlPullParser.START_TAG) {
                final String tagName = parser.getName();
                final VGroup currentGroup = groupStack.peek();
                if (SHAPE_PATH.equals(tagName)) {
                    final VFullPath path = new VFullPath();
                    path.inflate(res, attrs, theme, parser);
                    currentGroup.mChildren.add(path);
                    if (path.getPathName() != null) {
                        pathRenderer.mVGTargetsMap.put(path.getPathName(), path);
                    }
                    noPathTag = false;
                    state.mChangingConfigurations |= path.mChangingConfigurations;
                } else if (SHAPE_CLIP_PATH.equals(tagName)) {
                    final VClipPath path = new VClipPath();
                    path.inflate(res, attrs, theme, parser);
                    currentGroup.mChildren.add(path);
                    if (path.getPathName() != null) {
                        pathRenderer.mVGTargetsMap.put(path.getPathName(), path);
                    }
                    state.mChangingConfigurations |= path.mChangingConfigurations;
                } else if (SHAPE_GROUP.equals(tagName)) {
                    VGroup newChildGroup = new VGroup();
                    newChildGroup.inflate(res, attrs, theme, parser);
                    currentGroup.mChildren.add(newChildGroup);
                    groupStack.push(newChildGroup);
                    if (newChildGroup.getGroupName() != null) {
                        pathRenderer.mVGTargetsMap.put(newChildGroup.getGroupName(),
                                newChildGroup);
                    }
                    state.mChangingConfigurations |= newChildGroup.mChangingConfigurations;
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                final String tagName = parser.getName();
                if (SHAPE_GROUP.equals(tagName)) {
                    groupStack.pop();
                }
            }
            eventType = parser.next();
        }

        // Print the tree out for debug.
        if (DBG_VECTOR_DRAWABLE) {
            printGroupTree(pathRenderer.mRootGroup, 0);
        }

        if (noPathTag) {
            final StringBuffer tag = new StringBuffer();

            if (tag.length() > 0) {
                tag.append(" or ");
            }
            tag.append(SHAPE_PATH);

            throw new XmlPullParserException("no " + tag + " defined");
        }
    }

    private void printGroupTree(VGroup currentGroup, int level) {
        String indent = "";
        for (int i = 0; i < level; i++) {
            indent += "    ";
        }
        // Print the current node
        Log.v(LOGTAG, indent + "current group is :" + currentGroup.getGroupName()
                + " rotation is " + currentGroup.mRotate);
        Log.v(LOGTAG, indent + "matrix is :" + currentGroup.getLocalMatrix().toString());
        // Then print all the children groups
        for (int i = 0; i < currentGroup.mChildren.size(); i++) {
            Object child = currentGroup.mChildren.get(i);
            if (child instanceof VGroup) {
                printGroupTree((VGroup) child, level + 1);
            } else {
                ((VPath) child).printVPath(level + 1);
            }
        }
    }

    public void setAllowCaching(boolean allowCaching) {
        mAllowCaching = allowCaching;
    }

    // We don't support RTL auto mirroring since the getLayoutDirection() is for API 17+.
    @SuppressLint({"NewApi", "WrongConstant"})
    private boolean needMirroring() {
        if (Build.VERSION.SDK_INT < 17) {
            return false;
        } else {
            return isAutoMirrored() && getLayoutDirection() == LayoutDirection.RTL;
        }
    }

    // Extra override functions for delegation for SDK >= 7.
    @Override
    protected void onBoundsChange(Rect bounds) {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.setBounds(bounds);
        }
    }

    @Override
    public int getChangingConfigurations() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getChangingConfigurations();
        }
        return super.getChangingConfigurations() | mVectorState.getChangingConfigurations();
    }

    @Override
    public void invalidateSelf() {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.invalidateSelf();
            return;
        }
        super.invalidateSelf();
    }

    @Override
    public void scheduleSelf(Runnable what, long when) {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.scheduleSelf(what, when);
            return;
        }
        super.scheduleSelf(what, when);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.setVisible(visible, restart);
        }
        return super.setVisible(visible, restart);
    }

    @Override
    public void unscheduleSelf(Runnable what) {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.unscheduleSelf(what);
            return;
        }
        super.unscheduleSelf(what);
    }

    /**
     * Constant state for delegating the creating drawable job for SDK >= 24.
     * Instead of creating a VectorDrawable, create a VectorDrawableCompat instance which contains
     * a delegated VectorDrawable instance.
     */
    private static class VectorDrawableDelegateState extends ConstantState {
        private final ConstantState mDelegateState;

        public VectorDrawableDelegateState(ConstantState state) {
            mDelegateState = state;
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public Drawable newDrawable() {
            VectorDrawableCompat drawableCompat = new VectorDrawableCompat();
            drawableCompat.mDelegateDrawable = (VectorDrawable) mDelegateState.newDrawable();
            return drawableCompat;
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public Drawable newDrawable(Resources res) {
            VectorDrawableCompat drawableCompat = new VectorDrawableCompat();
            drawableCompat.mDelegateDrawable =  mDelegateState.newDrawable(res);
            return drawableCompat;
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public Drawable newDrawable(Resources res, Resources.Theme theme) {
            VectorDrawableCompat drawableCompat = new VectorDrawableCompat();
            drawableCompat.mDelegateDrawable =
                    (VectorDrawable) mDelegateState.newDrawable(res, theme);
            return drawableCompat;
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public boolean canApplyTheme() {
            return mDelegateState.canApplyTheme();
        }

        @Override
        public int getChangingConfigurations() {
            return mDelegateState.getChangingConfigurations();
        }
    }

    private static class VectorDrawableCompatState extends ConstantState {
        int mChangingConfigurations;
        VPathRenderer mVPathRenderer;
        ColorStateList mTint = null;
        PorterDuff.Mode mTintMode = DEFAULT_TINT_MODE;
        boolean mAutoMirrored;

        Bitmap mCachedBitmap;
        int[] mCachedThemeAttrs;
        ColorStateList mCachedTint;
        PorterDuff.Mode mCachedTintMode;
        int mCachedRootAlpha;
        boolean mCachedAutoMirrored;
        boolean mCacheDirty;

        /**
         * Temporary paint object used to draw cached bitmaps.
         */
        Paint mTempPaint;

        // Deep copy for mutate() or implicitly mutate.
        public VectorDrawableCompatState(VectorDrawableCompatState copy) {
            if (copy != null) {
                mChangingConfigurations = copy.mChangingConfigurations;
                mVPathRenderer = new VPathRenderer(copy.mVPathRenderer);
                if (copy.mVPathRenderer.mFillPaint != null) {
                    mVPathRenderer.mFillPaint = new Paint(copy.mVPathRenderer.mFillPaint);
                }
                if (copy.mVPathRenderer.mStrokePaint != null) {
                    mVPathRenderer.mStrokePaint = new Paint(copy.mVPathRenderer.mStrokePaint);
                }
                mTint = copy.mTint;
                mTintMode = copy.mTintMode;
                mAutoMirrored = copy.mAutoMirrored;
            }
        }

        public void drawCachedBitmapWithRootAlpha(Canvas canvas, ColorFilter filter,
                                                  Rect originalBounds) {
            // The bitmap's size is the same as the bounds.
            final Paint p = getPaint(filter);
            canvas.drawBitmap(mCachedBitmap, null, originalBounds, p);
        }

        public boolean hasTranslucentRoot() {
            return mVPathRenderer.getRootAlpha() < 255;
        }

        /**
         * @return null when there is no need for alpha paint.
         */
        public Paint getPaint(ColorFilter filter) {
            if (!hasTranslucentRoot() && filter == null) {
                return null;
            }

            if (mTempPaint == null) {
                mTempPaint = new Paint();
                mTempPaint.setFilterBitmap(true);
            }
            mTempPaint.setAlpha(mVPathRenderer.getRootAlpha());
            mTempPaint.setColorFilter(filter);
            return mTempPaint;
        }

        public void updateCachedBitmap(int width, int height) {
            mCachedBitmap.eraseColor(Color.TRANSPARENT);
            Canvas tmpCanvas = new Canvas(mCachedBitmap);
            mVPathRenderer.draw(tmpCanvas, width, height, null);
        }

        public void createCachedBitmapIfNeeded(int width, int height) {
            if (mCachedBitmap == null || !canReuseBitmap(width, height)) {
                mCachedBitmap = Bitmap.createBitmap(width, height,
                        Bitmap.Config.ARGB_8888);
                mCacheDirty = true;
            }

        }

        public boolean canReuseBitmap(int width, int height) {
            if (width == mCachedBitmap.getWidth()
                    && height == mCachedBitmap.getHeight()) {
                return true;
            }
            return false;
        }

        public boolean canReuseCache() {
            if (!mCacheDirty
                    && mCachedTint == mTint
                    && mCachedTintMode == mTintMode
                    && mCachedAutoMirrored == mAutoMirrored
                    && mCachedRootAlpha == mVPathRenderer.getRootAlpha()) {
                return true;
            }
            return false;
        }

        public void updateCacheStates() {
            // Use shallow copy here and shallow comparison in canReuseCache(),
            // likely hit cache miss more, but practically not much difference.
            mCachedTint = mTint;
            mCachedTintMode = mTintMode;
            mCachedRootAlpha = mVPathRenderer.getRootAlpha();
            mCachedAutoMirrored = mAutoMirrored;
            mCacheDirty = false;
        }

        public VectorDrawableCompatState() {
            mVPathRenderer = new VPathRenderer();
        }

        @Override
        public Drawable newDrawable() {
            return new VectorDrawableCompat(this);
        }

        @Override
        public Drawable newDrawable(Resources res) {
            return new VectorDrawableCompat(this);
        }

        @Override
        public int getChangingConfigurations() {
            return mChangingConfigurations;
        }
    }

    private static class VPathRenderer {
        /* Right now the internal data structure is organized as a tree.
         * Each node can be a group node, or a path.
         * A group node can have groups or paths as children, but a path node has
         * no children.
         * One example can be:
         *                 Root Group
         *                /    |     \
         *           Group    Path    Group
         *          /     \             |
         *         Path   Path         Path
         *
         */
        // Variables that only used temporarily inside the draw() call, so there
        // is no need for deep copying.
        private final Path mPath;
        private final Path mRenderPath;
        private static final Matrix IDENTITY_MATRIX = new Matrix();
        private final Matrix mFinalPathMatrix = new Matrix();

        private Paint mStrokePaint;
        private Paint mFillPaint;
        private PathMeasure mPathMeasure;

        /////////////////////////////////////////////////////
        // Variables below need to be copied (deep copy if applicable) for mutation.
        private int mChangingConfigurations;
        final VGroup mRootGroup;
        float mBaseWidth = 0;
        float mBaseHeight = 0;
        float mViewportWidth = 0;
        float mViewportHeight = 0;
        int mRootAlpha = 0xFF;
        String mRootName = null;

        final ArrayMap<String, Object> mVGTargetsMap = new ArrayMap<String, Object>();

        public VPathRenderer() {
            mRootGroup = new VGroup();
            mPath = new Path();
            mRenderPath = new Path();
        }

        public void setRootAlpha(int alpha) {
            mRootAlpha = alpha;
        }

        public int getRootAlpha() {
            return mRootAlpha;
        }

        // setAlpha() and getAlpha() are used mostly for animation purpose, since
        // Animator like to use alpha from 0 to 1.
        public void setAlpha(float alpha) {
            setRootAlpha((int) (alpha * 255));
        }

        @SuppressWarnings("unused")
        public float getAlpha() {
            return getRootAlpha() / 255.0f;
        }

        public VPathRenderer(VPathRenderer copy) {
            mRootGroup = new VGroup(copy.mRootGroup, mVGTargetsMap);
            mPath = new Path(copy.mPath);
            mRenderPath = new Path(copy.mRenderPath);
            mBaseWidth = copy.mBaseWidth;
            mBaseHeight = copy.mBaseHeight;
            mViewportWidth = copy.mViewportWidth;
            mViewportHeight = copy.mViewportHeight;
            mChangingConfigurations = copy.mChangingConfigurations;
            mRootAlpha = copy.mRootAlpha;
            mRootName = copy.mRootName;
            if (copy.mRootName != null) {
                mVGTargetsMap.put(copy.mRootName, this);
            }
        }

        private void drawGroupTree(VGroup currentGroup, Matrix currentMatrix,
                                   Canvas canvas, int w, int h, ColorFilter filter) {
            // Calculate current group's matrix by preConcat the parent's and
            // and the current one on the top of the stack.
            // Basically the Mfinal = Mviewport * M0 * M1 * M2;
            // Mi the local matrix at level i of the group tree.
            currentGroup.mStackedMatrix.set(currentMatrix);

            currentGroup.mStackedMatrix.preConcat(currentGroup.mLocalMatrix);

            // Save the current clip information, which is local to this group.
            canvas.save();

            // Draw the group tree in the same order as the XML file.
            for (int i = 0; i < currentGroup.mChildren.size(); i++) {
                Object child = currentGroup.mChildren.get(i);
                if (child instanceof VGroup) {
                   VGroup childGroup = (VGroup) child;
                    drawGroupTree(childGroup, currentGroup.mStackedMatrix,
                            canvas, w, h, filter);
                } else if (child instanceof VPath) {
                    VPath childPath = (VPath) child;
                    drawPath(currentGroup, childPath, canvas, w, h, filter);
                }
            }

            canvas.restore();
        }

        public void draw(Canvas canvas, int w, int h, ColorFilter filter) {
            // Traverse the tree in pre-order to draw.
            drawGroupTree(mRootGroup, IDENTITY_MATRIX, canvas, w, h, filter);
        }

        private void drawPath(VGroup vGroup, VPath vPath, Canvas canvas, int w, int h,
                              ColorFilter filter) {
            final float scaleX = w / mViewportWidth;
            final float scaleY = h / mViewportHeight;
            final float minScale = Math.min(scaleX, scaleY);
            final Matrix groupStackedMatrix = vGroup.mStackedMatrix;

            mFinalPathMatrix.set(groupStackedMatrix);
            mFinalPathMatrix.postScale(scaleX, scaleY);


            final float matrixScale = getMatrixScale(groupStackedMatrix);
            if (matrixScale == 0) {
                // When either x or y is scaled to 0, we don't need to draw anything.
                return;
            }
            vPath.toPath(mPath);
            final Path path = mPath;

            mRenderPath.reset();

            if (vPath.isClipPath()) {
                mRenderPath.addPath(path, mFinalPathMatrix);
                canvas.clipPath(mRenderPath);
            } else {
                VFullPath fullPath = (VFullPath) vPath;
                if (fullPath.mTrimPathStart != 0.0f || fullPath.mTrimPathEnd != 1.0f) {
                    float start = (fullPath.mTrimPathStart + fullPath.mTrimPathOffset) % 1.0f;
                    float end = (fullPath.mTrimPathEnd + fullPath.mTrimPathOffset) % 1.0f;

                    if (mPathMeasure == null) {
                        mPathMeasure = new PathMeasure();
                    }
                    mPathMeasure.setPath(mPath, false);

                    float len = mPathMeasure.getLength();
                    start = start * len;
                    end = end * len;
                    path.reset();
                    if (start > end) {
                        mPathMeasure.getSegment(start, len, path, true);
                        mPathMeasure.getSegment(0f, end, path, true);
                    } else {
                        mPathMeasure.getSegment(start, end, path, true);
                    }
                    path.rLineTo(0, 0); // fix bug in measure
                }
                mRenderPath.addPath(path, mFinalPathMatrix);

                if (fullPath.mFillColor != Color.TRANSPARENT || fullPath.mShader != null) {
                    if (mFillPaint == null) {
                        mFillPaint = new Paint();
                        mFillPaint.setStyle(Paint.Style.FILL);
                        mFillPaint.setAntiAlias(true);
                    }

                    final Paint fillPaint = mFillPaint;

                    if (fullPath.mShader != null) {
                        fillPaint.setShader(fullPath.mShader);
                    } else {
                        fillPaint.setColor(applyAlpha(fullPath.mFillColor, fullPath.mFillAlpha));
                        fillPaint.setColorFilter(filter);
                        fillPaint.setShader(null);
                    }
                    canvas.drawPath(mRenderPath, fillPaint);
                }

                if (fullPath.mStrokeColor != Color.TRANSPARENT) {
                    if (mStrokePaint == null) {
                        mStrokePaint = new Paint();
                        mStrokePaint.setStyle(Paint.Style.STROKE);
                        mStrokePaint.setAntiAlias(true);
                    }

                    final Paint strokePaint = mStrokePaint;
                    if (fullPath.mStrokeLineJoin != null) {
                        strokePaint.setStrokeJoin(fullPath.mStrokeLineJoin);
                    }

                    if (fullPath.mStrokeLineCap != null) {
                        strokePaint.setStrokeCap(fullPath.mStrokeLineCap);
                    }

                    strokePaint.setStrokeMiter(fullPath.mStrokeMiterlimit);
                    strokePaint.setColor(applyAlpha(fullPath.mStrokeColor, fullPath.mStrokeAlpha));
                    strokePaint.setColorFilter(filter);
                    final float finalStrokeScale = minScale * matrixScale;
                    strokePaint.setStrokeWidth(fullPath.mStrokeWidth * finalStrokeScale);
                    canvas.drawPath(mRenderPath, strokePaint);
                }
            }
        }

        private static float cross(float v1x, float v1y, float v2x, float v2y) {
            return v1x * v2y - v1y * v2x;
        }

        private float getMatrixScale(Matrix groupStackedMatrix) {
            // Given unit vectors A = (0, 1) and B = (1, 0).
            // After matrix mapping, we got A' and B'. Let theta = the angel b/t A' and B'.
            // Therefore, the final scale we want is min(|A'| * sin(theta), |B'| * sin(theta)),
            // which is (|A'| * |B'| * sin(theta)) / max (|A'|, |B'|);
            // If  max (|A'|, |B'|) = 0, that means either x or y has a scale of 0.
            //
            // For non-skew case, which is most of the cases, matrix scale is computing exactly the
            // scale on x and y axis, and take the minimal of these two.
            // For skew case, an unit square will mapped to a parallelogram. And this function will
            // return the minimal height of the 2 bases.
            float[] unitVectors = new float[]{0, 1, 1, 0};
            groupStackedMatrix.mapVectors(unitVectors);
            float scaleX = (float) Math.hypot(unitVectors[0], unitVectors[1]);
            float scaleY = (float) Math.hypot(unitVectors[2], unitVectors[3]);
            float crossProduct = cross(unitVectors[0], unitVectors[1], unitVectors[2],
                    unitVectors[3]);
            float maxScale = Math.max(scaleX, scaleY);

            float matrixScale = 0;
            if (maxScale > 0) {
                matrixScale = Math.abs(crossProduct) / maxScale;
            }
            if (DBG_VECTOR_DRAWABLE) {
                Log.d(LOGTAG, "Scale x " + scaleX + " y " + scaleY + " final " + matrixScale);
            }
            return matrixScale;
        }
    }

    public static class VGroup {
        // mStackedMatrix is only used temporarily when drawing, it combines all
        // the parents' local matrices with the current one.
        private final Matrix mStackedMatrix = new Matrix();

        /////////////////////////////////////////////////////
        // Variables below need to be copied (deep copy if applicable) for mutation.
        final ArrayList<Object> mChildren = new ArrayList<Object>();

        float mRotate = 0;
        private float mPivotX = 0;
        private float mPivotY = 0;
        private float mScaleX = 1;
        private float mScaleY = 1;
        private float mTranslateX = 0;
        private float mTranslateY = 0;

        // mLocalMatrix is updated based on the update of transformation information,
        // either parsed from the XML or by animation.
        private final Matrix mLocalMatrix = new Matrix();
        int mChangingConfigurations;
        private int[] mThemeAttrs;
        private String mGroupName = null;

        public VGroup(VGroup copy, ArrayMap<String, Object> targetsMap) {
            mRotate = copy.mRotate;
            mPivotX = copy.mPivotX;
            mPivotY = copy.mPivotY;
            mScaleX = copy.mScaleX;
            mScaleY = copy.mScaleY;
            mTranslateX = copy.mTranslateX;
            mTranslateY = copy.mTranslateY;
            mThemeAttrs = copy.mThemeAttrs;
            mGroupName = copy.mGroupName;
            mChangingConfigurations = copy.mChangingConfigurations;
            if (mGroupName != null) {
                targetsMap.put(mGroupName, this);
            }

            mLocalMatrix.set(copy.mLocalMatrix);

            final ArrayList<Object> children = copy.mChildren;
            for (int i = 0; i < children.size(); i++) {
                Object copyChild = children.get(i);
                if (copyChild instanceof VGroup) {
                    VGroup copyGroup = (VGroup) copyChild;
                    mChildren.add(new VGroup(copyGroup, targetsMap));
                } else {
                    VPath newPath = null;
                    if (copyChild instanceof VFullPath) {
                        newPath = new VFullPath((VFullPath) copyChild);
                    } else if (copyChild instanceof VClipPath) {
                        newPath = new VClipPath((VClipPath) copyChild);
                    } else {
                        throw new IllegalStateException("Unknown object in the tree!");
                    }
                    mChildren.add(newPath);
                    if (newPath.mPathName != null) {
                        targetsMap.put(newPath.mPathName, newPath);
                    }
                }
            }
        }

        public VGroup() {
        }

        public String getGroupName() {
            return mGroupName;
        }

        public List<Object> getChildren() {
            return mChildren;
        }

        public Matrix getLocalMatrix() {
            return mLocalMatrix;
        }

        public void inflate(Resources res, AttributeSet attrs, Resources.Theme theme, XmlPullParser parser) {
            final TypedArray a = obtainAttributes(res, theme, attrs,
                    AndroidResources.styleable_VectorDrawableGroup);
            updateStateFromTypedArray(a, parser);
            a.recycle();
        }

        private void updateStateFromTypedArray(TypedArray a, XmlPullParser parser) {
            // Account for any configuration changes.
            // mChangingConfigurations |= Utils.getChangingConfigurations(a);

            // Extract the theme attributes, if any.
            mThemeAttrs = null; // TODO TINT THEME Not supported yet a.extractThemeAttrs();

            // This is added in API 11
            mRotate = TypedArrayUtils.getNamedFloat(a, parser, "rotation",
                    AndroidResources.styleable_VectorDrawableGroup_rotation, mRotate);

            mPivotX = a.getFloat(AndroidResources.styleable_VectorDrawableGroup_pivotX, mPivotX);
            mPivotY = a.getFloat(AndroidResources.styleable_VectorDrawableGroup_pivotY, mPivotY);

            // This is added in API 11
            mScaleX = TypedArrayUtils.getNamedFloat(a, parser, "scaleX",
                    AndroidResources.styleable_VectorDrawableGroup_scaleX, mScaleX);

            // This is added in API 11
            mScaleY = TypedArrayUtils.getNamedFloat(a, parser, "scaleY",
                    AndroidResources.styleable_VectorDrawableGroup_scaleY, mScaleY);

            mTranslateX = TypedArrayUtils.getNamedFloat(a, parser, "translateX",
                    AndroidResources.styleable_VectorDrawableGroup_translateX, mTranslateX);
            mTranslateY = TypedArrayUtils.getNamedFloat(a, parser, "translateY",
                    AndroidResources.styleable_VectorDrawableGroup_translateY, mTranslateY);

            final String groupName =
                    a.getString(AndroidResources.styleable_VectorDrawableGroup_name);
            if (groupName != null) {
                mGroupName = groupName;
            }

            updateLocalMatrix();
        }

        private void updateLocalMatrix() {
            // The order we apply is the same as the
            // RenderNode.cpp::applyViewPropertyTransforms().
            mLocalMatrix.reset();
            mLocalMatrix.postTranslate(-mPivotX, -mPivotY);
            mLocalMatrix.postScale(mScaleX, mScaleY);
            mLocalMatrix.postRotate(mRotate, 0, 0);
            mLocalMatrix.postTranslate(mTranslateX + mPivotX, mTranslateY + mPivotY);
        }

        /* Setters and Getters, used by animator from AnimatedVectorDrawable. */
        @SuppressWarnings("unused")
        public float getRotation() {
            return mRotate;
        }

        @SuppressWarnings("unused")
        public void setRotation(float rotation) {
            if (rotation != mRotate) {
                mRotate = rotation;
                updateLocalMatrix();
            }
        }

        @SuppressWarnings("unused")
        public float getPivotX() {
            return mPivotX;
        }

        @SuppressWarnings("unused")
        public void setPivotX(float pivotX) {
            if (pivotX != mPivotX) {
                mPivotX = pivotX;
                updateLocalMatrix();
            }
        }

        @SuppressWarnings("unused")
        public float getPivotY() {
            return mPivotY;
        }

        @SuppressWarnings("unused")
        public void setPivotY(float pivotY) {
            if (pivotY != mPivotY) {
                mPivotY = pivotY;
                updateLocalMatrix();
            }
        }

        @SuppressWarnings("unused")
        public float getScaleX() {
            return mScaleX;
        }

        @SuppressWarnings("unused")
        public void setScaleX(float scaleX) {
            if (scaleX != mScaleX) {
                mScaleX = scaleX;
                updateLocalMatrix();
            }
        }

        @SuppressWarnings("unused")
        public float getScaleY() {
            return mScaleY;
        }

        @SuppressWarnings("unused")
        public void setScaleY(float scaleY) {
            if (scaleY != mScaleY) {
                mScaleY = scaleY;
                updateLocalMatrix();
            }
        }

        @SuppressWarnings("unused")
        public float getTranslateX() {
            return mTranslateX;
        }

        @SuppressWarnings("unused")
        public void setTranslateX(float translateX) {
            if (translateX != mTranslateX) {
                mTranslateX = translateX;
                updateLocalMatrix();
            }
        }

        @SuppressWarnings("unused")
        public float getTranslateY() {
            return mTranslateY;
        }

        @SuppressWarnings("unused")
        public void setTranslateY(float translateY) {
            if (translateY != mTranslateY) {
                mTranslateY = translateY;
                updateLocalMatrix();
            }
        }
    }

    /**
     * Common Path information for clip path and normal path.
     */
    private static class VPath {
        protected PathParser.PathDataNode[] mNodes = null;
        String mPathName;
        int mChangingConfigurations;

        public VPath() {
            // Empty constructor.
        }

        public void printVPath(int level) {
            String indent = "";
            for (int i = 0; i < level; i++) {
                indent += "    ";
            }
            Log.v(LOGTAG, indent + "current path is :" + mPathName +
                    " pathData is " + NodesToString(mNodes));

        }

        public String NodesToString(PathParser.PathDataNode[] nodes) {
            String result = " ";
            for (int i = 0; i < nodes.length; i++) {
                result += nodes[i].type + ":";
                float[] params = nodes[i].params;
                for (int j = 0; j < params.length; j++) {
                    result += params[j] + ",";
                }
            }
            return result;
        }

        public VPath(VPath copy) {
            mPathName = copy.mPathName;
            mChangingConfigurations = copy.mChangingConfigurations;
            mNodes = PathParser.deepCopyNodes(copy.mNodes);
        }

        public void toPath(Path path) {
            path.reset();
            if (mNodes != null) {
                PathParser.PathDataNode.nodesToPath(mNodes, path);
            }
        }

        public String getPathName() {
            return mPathName;
        }

        public boolean canApplyTheme() {
            return false;
        }

        public void applyTheme(Resources.Theme t) {
        }

        public boolean isClipPath() {
            return false;
        }

        /* Setters and Getters, used by animator from AnimatedVectorDrawable. */
        @SuppressWarnings("unused")
        public PathParser.PathDataNode[] getPathData() {
            return mNodes;
        }

        @SuppressWarnings("unused")
        public void setPathData(PathParser.PathDataNode[] nodes) {
            if (!PathParser.canMorph(mNodes, nodes)) {
                // This should not happen in the middle of animation.
                mNodes = PathParser.deepCopyNodes(nodes);
            } else {
                PathParser.updateNodes(mNodes, nodes);
            }
        }
    }

    /**
     * Clip path, which only has name and pathData.
     */
    private static class VClipPath extends VPath {
        public VClipPath() {
            // Empty constructor.
        }

        public VClipPath(VClipPath copy) {
            super(copy);
        }

        public void inflate(Resources r, AttributeSet attrs, Resources.Theme theme, XmlPullParser parser) {
            // TODO TINT THEME Not supported yet
            final boolean hasPathData = TypedArrayUtils.hasAttribute(parser, "pathData");
            if (!hasPathData) {
                return;
            }
            final TypedArray a = obtainAttributes(r, theme, attrs,
                    AndroidResources.styleable_VectorDrawableClipPath);
            updateStateFromTypedArray(a);
            a.recycle();
        }

        private void updateStateFromTypedArray(TypedArray a) {
            // Account for any configuration changes.
            // mChangingConfigurations |= Utils.getChangingConfigurations(a);;

            final String pathName =
                    a.getString(AndroidResources.styleable_VectorDrawableClipPath_name);
            if (pathName != null) {
                mPathName = pathName;
            }

            final String pathData =
                    a.getString(AndroidResources.styleable_VectorDrawableClipPath_pathData);
            if (pathData != null) {
                mNodes = PathParser.createNodesFromPathData(pathData);
            }
        }

        @Override
        public boolean isClipPath() {
            return true;
        }
    }

    /**
     * Normal path, which contains all the fill / paint information.
     */
    public static class VFullPath extends VPath {
        /////////////////////////////////////////////////////
        // Variables below need to be copied (deep copy if applicable) for mutation.
        private int[] mThemeAttrs;

        int mStrokeColor = Color.TRANSPARENT;
        float mStrokeWidth = 0;

        int mFillColor = Color.TRANSPARENT;
        Shader mShader;
        float mStrokeAlpha = 1.0f;
        int mFillRule;
        float mFillAlpha = 1.0f;
        float mTrimPathStart = 0;
        float mTrimPathEnd = 1;
        float mTrimPathOffset = 0;

        Paint.Cap mStrokeLineCap = Paint.Cap.BUTT;
        Paint.Join mStrokeLineJoin = Paint.Join.MITER;
        float mStrokeMiterlimit = 4;

        public VFullPath() {
            // Empty constructor.
        }

        public VFullPath(VFullPath copy) {
            super(copy);
            mThemeAttrs = copy.mThemeAttrs;

            mStrokeColor = copy.mStrokeColor;
            mStrokeWidth = copy.mStrokeWidth;
            mStrokeAlpha = copy.mStrokeAlpha;
            mFillColor = copy.mFillColor;
            mFillRule = copy.mFillRule;
            mFillAlpha = copy.mFillAlpha;
            mTrimPathStart = copy.mTrimPathStart;
            mTrimPathEnd = copy.mTrimPathEnd;
            mTrimPathOffset = copy.mTrimPathOffset;

            mStrokeLineCap = copy.mStrokeLineCap;
            mStrokeLineJoin = copy.mStrokeLineJoin;
            mStrokeMiterlimit = copy.mStrokeMiterlimit;
        }

        private Paint.Cap getStrokeLineCap(int id, Paint.Cap defValue) {
            switch (id) {
                case LINECAP_BUTT:
                    return Paint.Cap.BUTT;
                case LINECAP_ROUND:
                    return Paint.Cap.ROUND;
                case LINECAP_SQUARE:
                    return Paint.Cap.SQUARE;
                default:
                    return defValue;
            }
        }

        private Paint.Join getStrokeLineJoin(int id, Paint.Join defValue) {
            switch (id) {
                case LINEJOIN_MITER:
                    return Paint.Join.MITER;
                case LINEJOIN_ROUND:
                    return Paint.Join.ROUND;
                case LINEJOIN_BEVEL:
                    return Paint.Join.BEVEL;
                default:
                    return defValue;
            }
        }

        @Override
        public boolean canApplyTheme() {
            return mThemeAttrs != null;
        }

        public void inflate(Resources r, AttributeSet attrs, Resources.Theme theme, XmlPullParser parser) {
            final TypedArray a = obtainAttributes(r, theme, attrs,
                    AndroidResources.styleable_VectorDrawablePath);
            updateStateFromTypedArray(a, parser);
            a.recycle();
        }

        private void updateStateFromTypedArray(TypedArray a, XmlPullParser parser) {
            // Account for any configuration changes.
            // mChangingConfigurations |= Utils.getChangingConfigurations(a);

            // Extract the theme attributes, if any.
            mThemeAttrs = null; // TODO TINT THEME Not supported yet a.extractThemeAttrs();

            // In order to work around the conflicting id issue, we need to double check the
            // existence of the attribute.
            // B/c if the attribute existed in the compiled XML, then calling TypedArray will be
            // safe since the framework will look up in the XML first.
            // Note that each getAttributeValue take roughly 0.03ms, it is a price we have to pay.
            final boolean hasPathData = TypedArrayUtils.hasAttribute(parser, "pathData");
            if (!hasPathData) {
                // If there is no pathData in the <path> tag, then this is an empty path,
                // nothing need to be drawn.
                return;
            }

            final String pathName = a.getString(AndroidResources.styleable_VectorDrawablePath_name);
            if (pathName != null) {
                mPathName = pathName;
            }
            final String pathData =
                    a.getString(AndroidResources.styleable_VectorDrawablePath_pathData);
            if (pathData != null) {
                mNodes = PathParser.createNodesFromPathData(pathData);
            }

            mFillColor = TypedArrayUtils.getNamedColor(a, parser, "fillColor",
                    AndroidResources.styleable_VectorDrawablePath_fillColor, mFillColor);
            mFillAlpha = TypedArrayUtils.getNamedFloat(a, parser, "fillAlpha",
                    AndroidResources.styleable_VectorDrawablePath_fillAlpha, mFillAlpha);
            final int lineCap = TypedArrayUtils.getNamedInt(a, parser, "strokeLineCap",
                    AndroidResources.styleable_VectorDrawablePath_strokeLineCap, -1);
            mStrokeLineCap = getStrokeLineCap(lineCap, mStrokeLineCap);
            final int lineJoin = TypedArrayUtils.getNamedInt(a, parser, "strokeLineJoin",
                    AndroidResources.styleable_VectorDrawablePath_strokeLineJoin, -1);
            mStrokeLineJoin = getStrokeLineJoin(lineJoin, mStrokeLineJoin);
            mStrokeMiterlimit = TypedArrayUtils.getNamedFloat(a, parser, "strokeMiterLimit",
                    AndroidResources.styleable_VectorDrawablePath_strokeMiterLimit,
                    mStrokeMiterlimit);
            mStrokeColor = TypedArrayUtils.getNamedColor(a, parser, "strokeColor",
                    AndroidResources.styleable_VectorDrawablePath_strokeColor, mStrokeColor);
            mStrokeAlpha = TypedArrayUtils.getNamedFloat(a, parser, "strokeAlpha",
                    AndroidResources.styleable_VectorDrawablePath_strokeAlpha, mStrokeAlpha);
            mStrokeWidth = TypedArrayUtils.getNamedFloat(a, parser, "strokeWidth",
                    AndroidResources.styleable_VectorDrawablePath_strokeWidth, mStrokeWidth);
            mTrimPathEnd = TypedArrayUtils.getNamedFloat(a, parser, "trimPathEnd",
                    AndroidResources.styleable_VectorDrawablePath_trimPathEnd, mTrimPathEnd);
            mTrimPathOffset = TypedArrayUtils.getNamedFloat(a, parser, "trimPathOffset",
                    AndroidResources.styleable_VectorDrawablePath_trimPathOffset, mTrimPathOffset);
            mTrimPathStart = TypedArrayUtils.getNamedFloat(a, parser, "trimPathStart",
                    AndroidResources.styleable_VectorDrawablePath_trimPathStart, mTrimPathStart);
        }

        @Override
        public void applyTheme(Resources.Theme t) {
            if (mThemeAttrs == null) {
                return;
            }

            /*
             * TODO TINT THEME Not supported yet final TypedArray a =
             * t.resolveAttributes(mThemeAttrs, styleable_VectorDrawablePath);
             * updateStateFromTypedArray(a); a.recycle();
             */
        }

        /* Setters and Getters, used by animator from AnimatedVectorDrawable. */
        @SuppressWarnings("unused")
        public int getStrokeColor() {
            return mStrokeColor;
        }

        @SuppressWarnings("unused")
        public void setStrokeColor(int strokeColor) {
            mStrokeColor = strokeColor;
        }

        @SuppressWarnings("unused")
        public float getStrokeWidth() {
            return mStrokeWidth;
        }

        @SuppressWarnings("unused")
        public void setStrokeWidth(float strokeWidth) {
            mStrokeWidth = strokeWidth;
        }

        @SuppressWarnings("unused")
        public float getStrokeAlpha() {
            return mStrokeAlpha;
        }

        @SuppressWarnings("unused")
        public void setStrokeAlpha(float strokeAlpha) {
            mStrokeAlpha = strokeAlpha;
        }

        @SuppressWarnings("unused")
        public int getFillColor() {
            return mFillColor;
        }

        @SuppressWarnings("unused")
        public void setFillColor(int fillColor) {
            mShader = null;
            mFillColor = fillColor;
        }

        public Shader getShader() {
            return mShader;
        }

        public void setShader(Shader shader) {
            this.mShader = shader;
        }

        @SuppressWarnings("unused")
        public float getFillAlpha() {
            return mFillAlpha;
        }

        @SuppressWarnings("unused")
        public void setFillAlpha(float fillAlpha) {
            mFillAlpha = fillAlpha;
        }

        @SuppressWarnings("unused")
        public float getTrimPathStart() {
            return mTrimPathStart;
        }

        @SuppressWarnings("unused")
        public void setTrimPathStart(float trimPathStart) {
            mTrimPathStart = trimPathStart;
        }

        @SuppressWarnings("unused")
        public float getTrimPathEnd() {
            return mTrimPathEnd;
        }

        @SuppressWarnings("unused")
        public void setTrimPathEnd(float trimPathEnd) {
            mTrimPathEnd = trimPathEnd;
        }

        @SuppressWarnings("unused")
        public float getTrimPathOffset() {
            return mTrimPathOffset;
        }

        @SuppressWarnings("unused")
        public void setTrimPathOffset(float trimPathOffset) {
            mTrimPathOffset = trimPathOffset;
        }
    }
}
