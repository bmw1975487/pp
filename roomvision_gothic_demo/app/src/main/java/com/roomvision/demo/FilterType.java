package com.roomvision.demo;

import java.util.Arrays;
import java.util.List;

public enum FilterType {
    ORIGINAL("Оригинал", false),
    CARTOON("Cartoon", false),
    COMIC("Comic", false),
    COMIC_BW("Comic B/W", false),
    CARTOON_EDGE("Cartoon Edge", false),
    SKETCH("Sketch", false),
    PENCIL("Pencil", false),
    COLOR_PENCIL("Color Pencil", false),
    CHARCOAL("Charcoal", false),
    CRAYON("Crayon", false),
    INK_WASH("Ink Wash", false),
    OIL_PAINTING("Oil Painting", false),
    WATERCOLOR("Watercolor", false),
    PAINTING("Painting", false),
    PASTEL("Pastel", false),
    MANGA("Manga", false),
    CROSSHATCH("Crosshatch", false),
    CUTOUT("Cutout", false),
    POSTER("Poster", false),
    HALFTONE("Halftone", false),
    PIXEL_ART("Pixel Art", false),
    GAME_BOY("Game Boy", false),
    THERMAL("Thermal", false),
    ASCII_ART("ASCII", false),
    NEON_EDGE("Neon Edge", false),
    CYBERPUNK("Cyberpunk", false),
    RETRO("Retro", false),
    NOIR("Noir", false),
    MONO("Mono", false),
    AQUA("Aqua", false),
    GRUNGE("Grunge", false),
    MATTE("Matte", false),
    OLDIE("Oldie", false),
    WARHOL("Warhol", false),
    STAINED_GLASS("Stained Glass", false),
    FROSTED("Frosted", false),
    DOTS("Dots", false),
    BUBBLES("Bubbles", false),
    EMBOSS("Emboss", false),
    CONTOURS("Contours", false),
    NEURAL_VAN_GOGH("Van Gogh AI", true),
    NEURAL_KANDINSKY("Kandinsky AI", true),
    NEURAL_CYBERPUNK("Cyberpunk AI", true);

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
