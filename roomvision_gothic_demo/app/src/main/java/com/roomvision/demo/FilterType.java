package com.roomvision.demo;

import java.util.Arrays;
import java.util.List;

public enum FilterType {
    GOTHIC("Gothic / Dracula"),
    MATRIX("Matrix"),
    CRAYON("Crayon"),
    BLUE_PEN("Blue Pen"),
    ASCII("ASCII");

    public final String label;
    FilterType(String label) { this.label = label; }

    public static List<FilterType> catalog() {
        return Arrays.asList(values());
    }
}
