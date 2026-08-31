package com.roomvision.demo;

import java.util.Arrays;
import java.util.List;

public enum FilterType {
    MATRIX("Matrix"),
    CRAYON("Crayon"),
    BLUE_PEN("Blue Pen"),
    ASCII("ASCII"),
    GOTHIC("Gothic");

    public final String label;
    FilterType(String label) { this.label = label; }

    public static List<FilterType> catalog() {
        return Arrays.asList(values());
    }
}
