using System.Security;
using System.Text;

namespace DOMRF.PowerPointPalette
{
    internal static class RibbonFactory
    {
        private sealed class ModeDefinition
        {
            public ModeDefinition(string key, string label, string iconHex)
            {
                Key = key;
                Label = label;
                IconHex = iconHex;
            }

            public string Key { get; }
            public string Label { get; }
            public string IconHex { get; }
        }

        private static readonly ModeDefinition[] Modes =
        {
            new ModeDefinition("fill", "ЗАЛИВКА", "80E3FF"),
            new ModeDefinition("text", "ТЕКСТ", "012F42"),
            new ModeDefinition("line", "КОНТУР", "252628")
        };

        public static string Build()
        {
            var xml = new StringBuilder(32768);
            xml.Append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            xml.Append("<customUI xmlns=\"http://schemas.microsoft.com/office/2006/01/customui\" onLoad=\"RibbonLoaded\">");
            xml.Append("<ribbon><tabs>");
            xml.Append("<tab id=\"domrfPaletteTab\" label=\"ПАЛИТРА\" insertAfterMso=\"TabHome\">");
            xml.Append("<group id=\"domrfPaletteGroup\" label=\"15 ФИРМЕННЫХ ЦВЕТОВ\">");

            foreach (ModeDefinition mode in Modes)
            {
                AppendModeMenu(xml, mode);
            }

            xml.Append("</group>");
            xml.Append("<group id=\"domrfPaletteDiagnostics\" label=\"ДИАГНОСТИКА\">");
            xml.Append("<button id=\"domrfOpenLog\" label=\"Открыть лог\" screentip=\"Открыть журнал работы палитры\" onAction=\"OpenAddInLog\" />");
            xml.Append("</group>");
            xml.Append("</tab>");
            xml.Append("</tabs></ribbon></customUI>");
            return xml.ToString();
        }

        private static void AppendModeMenu(StringBuilder xml, ModeDefinition mode)
        {
            string safeMode = Escape(mode.Key);
            string safeLabel = Escape(mode.Label);
            string topTag = Escape(mode.Key + "|" + mode.IconHex);

            xml.AppendFormat(
                "<menu id=\"domrf_{0}_menu\" label=\"{1}\" size=\"large\" tag=\"{2}\" getImage=\"GetSwatchImage\">",
                safeMode,
                safeLabel,
                topTag);

            AppendCategoryMenu(xml, mode, PaletteCategory.Main, "Основные", "main");
            AppendCategoryMenu(xml, mode, PaletteCategory.Additional, "Дополнительные", "additional");
            AppendCategoryMenu(xml, mode, PaletteCategory.Status, "Статусные", "status");

            xml.Append("</menu>");
        }

        private static void AppendCategoryMenu(
            StringBuilder xml,
            ModeDefinition mode,
            PaletteCategory category,
            string categoryLabel,
            string categoryId)
        {
            xml.AppendFormat(
                "<menu id=\"domrf_{0}_{1}\" label=\"{2}\">",
                Escape(mode.Key),
                Escape(categoryId),
                Escape(categoryLabel));

            int index = 0;
            foreach (PaletteColor color in PaletteDefinition.ByCategory(category))
            {
                string tag = mode.Key + "|" + color.Hex;
                xml.AppendFormat(
                    "<button id=\"domrf_{0}_{1}_{2}\" label=\"{3}\" tag=\"{4}\" getImage=\"GetSwatchImage\" onAction=\"ApplyColor\" screentip=\"Применить #{5}\" />",
                    Escape(mode.Key),
                    Escape(categoryId),
                    index,
                    Escape(color.DisplayLabel),
                    Escape(tag),
                    Escape(color.Hex));
                index++;
            }

            xml.Append("</menu>");
        }

        private static string Escape(string value)
        {
            return SecurityElement.Escape(value) ?? string.Empty;
        }
    }
}
