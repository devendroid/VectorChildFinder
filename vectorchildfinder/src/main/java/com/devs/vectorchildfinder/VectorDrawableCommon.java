package com.devs.vectorchildfinder;

/**
 * Created by ${Deven} on 1/31/18.
 */

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.core.graphics.drawable.DrawableCompat;

/**
 * Internal common delegation shared by VectorDrawableCompat and AnimatedVectorDrawableCompat
 */
abstract class VectorDrawableCommon extends Drawable {
    /**
     * Obtains styled attributes from the theme, if available, or unstyled
     * resources if the theme is null.
     *
     * @hide
     */
    protected static TypedArray obtainAttributes(
            Resources res, Resources.Theme theme, AttributeSet set, int[] attrs) {
        if (theme == null) {
            return res.obtainAttributes(set, attrs);
        }
        return theme.obtainStyledAttributes(set, attrs, 0, 0);
    }

    // Drawable delegation for Lollipop and above.
    Drawable mDelegateDrawable;

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.setColorFilter(colorFilter);
        }
    }

    @Override
    public ColorFilter getColorFilter() {
        if (mDelegateDrawable != null) {
            return DrawableCompat.getColorFilter(mDelegateDrawable);
        }
        return null;
    }

    @Override
    protected boolean onLevelChange(int level) {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.setLevel(level);
        }
        return super.onLevelChange(level);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.setBounds(bounds);
            return;
        }
        super.onBoundsChange(bounds);
    }

    @Override
    public void setHotspot(float x, float y) {
        // API >= 21 only.
        if (mDelegateDrawable != null) {
            DrawableCompat.setHotspot(mDelegateDrawable, x, y);
        }
    }

    @Override
    public void setHotspotBounds(int left, int top, int right, int bottom) {
        if (mDelegateDrawable != null) {
            DrawableCompat.setHotspotBounds(mDelegateDrawable, left, top, right, bottom);
        }
    }

    @Override
    public void setFilterBitmap(boolean filter) {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.setFilterBitmap(filter);
        }
    }

    @Override
    public void jumpToCurrentState() {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.jumpToCurrentState();
        }
    }

    @Override
    public void applyTheme(Resources.Theme t) {
        // API >= 21 only.
        if (mDelegateDrawable != null) {
            DrawableCompat.applyTheme(mDelegateDrawable, t);
        }
    }

    @Override
    public void clearColorFilter() {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.clearColorFilter();
            return;
        }
        super.clearColorFilter();
    }

    @Override
    public Drawable getCurrent() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getCurrent();
        }
        return super.getCurrent();
    }

    @Override
    public int getMinimumWidth() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getMinimumWidth();
        }
        return super.getMinimumWidth();
    }

    @Override
    public int getMinimumHeight() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getMinimumHeight();
        }
        return super.getMinimumHeight();
    }

    @Override
    public boolean getPadding(Rect padding) {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getPadding(padding);
        }
        return super.getPadding(padding);
    }

    @Override
    public int[] getState() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getState();
        }
        return super.getState();
    }


    @Override
    public Region getTransparentRegion() {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.getTransparentRegion();
        }
        return super.getTransparentRegion();
    }

    @Override
    public void setChangingConfigurations(int configs) {
        if (mDelegateDrawable != null) {
            mDelegateDrawable.setChangingConfigurations(configs);
            return;
        }
        super.setChangingConfigurations(configs);
    }

    @Override
    public boolean setState(int[] stateSet) {
        if (mDelegateDrawable != null) {
            return mDelegateDrawable.setState(stateSet);
        }
        return super.setState(stateSet);
    }
}
