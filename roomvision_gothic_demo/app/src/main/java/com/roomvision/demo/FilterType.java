package com.roomvision.demo;

import java.util.Arrays;
import java.util.List;

public enum FilterType {
    ORIGINAL("Original", false),

    // Pixel Lense family
    PIXEL_COMIC("Comic Book", false),
    PIXEL_COMIC_BW("Comic B&W", false),
    PIXEL_ART("Pixel", false),
    PIXEL_CYBERPUNK("Cyberpunk", false),
    GAME_BOY("Game Boy", false),
    PIXEL_OIL("Oil Painting", false),
    PIXEL_WATERCOLOR("Watercolor", false),
    PIXEL_HALFTONE("Halftone", false),
    PIXEL_THERMAL("Thermal", false),
    PIXEL_INK_WASH("Ink Wash", false),
    PIXEL_SKETCH("Pencil Sketch", false),
    PIXEL_ASCII("ASCII Art", false),
    VAN_GOGH("Van Gogh", true),
    KANDINSKY("Kandinsky", true),

    // Cartoon Camera HD family
    CARTOON_HD("Cartoon HD", false),

    // Sketch Camera family
    BLUE_PEN("Blue Pen", false),
    PEN("Pen", false),
    PENCIL("Pencil", false),
    PENCIL_2("Pencil 2", false),
    PENCIL_3("Pencil 3", false),
    COLOR_PENCIL("Color Pencil", false),
    COLOR_SKETCH("Color Sketch", false),
    CHARCOAL("Charcoal", false),
    CRAYON("Crayon", false),
    CROSSHATCH("Crosshatch", false),
    MANGA("Manga", false),
    PASTEL("Pastel", false),
    SKETCH("Sketch", false),
    SKETCHY("Sketchy", false),
    SK_OIL("Oil", false),
    SK_OIL_2("Oil 2", false),
    OILY("Oily", false),
    OIL_FLOW("Oil Flow", false),
    WATER("Water", false),
    SK_COMICS("Comics", false),
    WARHOL("Warhol", false),
    STAINED_GLASS("Stained Glass", false),
    DOTS("Dots", false),
    FROSTED("Frosted", false),
    AMERICAN("American", false),
    BW("B&W", false);

    public final String label;
    public final boolean neural;

    FilterType(String label, boolean neural) {
        this.label = label;
        this.neural = neural;
    }

    public static List<FilterType> catalog() {
        return Arrays.asList(values());
    }
}
