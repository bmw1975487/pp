package ru.nika.v3test;

import android.graphics.drawable.GradientDrawable;

final class ShapeFactory {
    private ShapeFactory() {}

    static GradientDrawable rounded(
        int fill,
        int stroke,
        int radius,
        int strokeWidth
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }
}
