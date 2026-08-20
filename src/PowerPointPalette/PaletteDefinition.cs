using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Globalization;
using System.Linq;

namespace DOMRF.PowerPointPalette
{
    internal enum PaletteCategory
    {
        Main,
        Additional,
        Status
    }

    internal sealed class PaletteColor
    {
        public PaletteColor(PaletteCategory category, string name, string hex)
        {
            if (string.IsNullOrWhiteSpace(name))
            {
                throw new ArgumentException("Color name is required.", nameof(name));
            }

            Category = category;
            Name = name;
            Hex = NormalizeHex(hex);
            OfficeRgb = ToOfficeRgb(Hex);
        }

        public PaletteCategory Category { get; }

        public string Name { get; }

        public string Hex { get; }

        public int OfficeRgb { get; }

        public string DisplayLabel => string.Format(CultureInfo.InvariantCulture, "{0}  #{1}", Name, Hex);

        public static string NormalizeHex(string value)
        {
            string normalized = (value ?? string.Empty).Trim().TrimStart('#').ToUpperInvariant();
            if (normalized.Length != 6 || !int.TryParse(normalized, NumberStyles.HexNumber, CultureInfo.InvariantCulture, out _))
            {
                throw new ArgumentException("HEX color must contain exactly six hexadecimal characters.", nameof(value));
            }

            return normalized;
        }

        public static int ToOfficeRgb(string hex)
        {
            string normalized = NormalizeHex(hex);
            int red = int.Parse(normalized.Substring(0, 2), NumberStyles.HexNumber, CultureInfo.InvariantCulture);
            int green = int.Parse(normalized.Substring(2, 2), NumberStyles.HexNumber, CultureInfo.InvariantCulture);
            int blue = int.Parse(normalized.Substring(4, 2), NumberStyles.HexNumber, CultureInfo.InvariantCulture);
            return red | (green << 8) | (blue << 16);
        }
    }

    internal static class PaletteDefinition
    {
        private static readonly ReadOnlyCollection<PaletteColor> AllColors = new List<PaletteColor>
        {
            new PaletteColor(PaletteCategory.Main, "Голубой", "80E3FF"),
            new PaletteColor(PaletteCategory.Main, "Тёмно-синий", "012F42"),
            new PaletteColor(PaletteCategory.Main, "Графит", "252628"),

            new PaletteColor(PaletteCategory.Additional, "Синий 1", "114870"),
            new PaletteColor(PaletteCategory.Additional, "Синий 2", "0F5C87"),
            new PaletteColor(PaletteCategory.Additional, "Бирюзовый", "2BCEF0"),
            new PaletteColor(PaletteCategory.Additional, "Светло-голубой 1", "9DEEFF"),
            new PaletteColor(PaletteCategory.Additional, "Светло-голубой 2", "B6F3FF"),
            new PaletteColor(PaletteCategory.Additional, "Очень светлый", "F0FCFF"),

            new PaletteColor(PaletteCategory.Status, "Светло-зелёный", "E5FCF1"),
            new PaletteColor(PaletteCategory.Status, "Светло-жёлтый", "FFFAE0"),
            new PaletteColor(PaletteCategory.Status, "Светло-красный", "FFF0F0"),
            new PaletteColor(PaletteCategory.Status, "Зелёный", "39C182"),
            new PaletteColor(PaletteCategory.Status, "Жёлтый", "EEA20F"),
            new PaletteColor(PaletteCategory.Status, "Красный", "D74B54")
        }.AsReadOnly();

        public static IReadOnlyList<PaletteColor> Colors => AllColors;

        public static PaletteColor FindByHex(string hex)
        {
            string normalized = PaletteColor.NormalizeHex(hex);
            PaletteColor result = AllColors.FirstOrDefault(color => string.Equals(color.Hex, normalized, StringComparison.OrdinalIgnoreCase));
            if (result == null)
            {
                throw new InvalidOperationException("Color is not present in the approved palette: #" + normalized);
            }

            return result;
        }

        public static IReadOnlyList<PaletteColor> ByCategory(PaletteCategory category)
        {
            return AllColors.Where(color => color.Category == category).ToList().AsReadOnly();
        }
    }
}
