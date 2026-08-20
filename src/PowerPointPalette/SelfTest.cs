using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Xml;

namespace DOMRF.PowerPointPalette
{
    internal static class SelfTest
    {
        internal sealed class FakeRibbonControl
        {
            public string Tag { get; set; }
        }

        public static int Run()
        {
            DiagnosticsLog.ResetInstallLog();
            DiagnosticsLog.Install("=== SELF TEST START ===");

            try
            {
                DiagnosticsLog.Install("Stage: palette definition");
                ValidatePalette();

                DiagnosticsLog.Install("Stage: RibbonX XML");
                ValidateRibbonXml();

                DiagnosticsLog.Install("Stage: COM class metadata");
                ValidateComClass();

                DiagnosticsLog.Install("Stage: swatch image conversion");
                ValidateSwatchConversion();

                DiagnosticsLog.Install("RESULT: SELF_TEST_SUCCESS");
                DiagnosticsLog.Install("=== SELF TEST END ===");
                return 0;
            }
            catch (Exception exception)
            {
                DiagnosticsLog.InstallException(exception, "SELF_TEST");
                DiagnosticsLog.Install("RESULT: SELF_TEST_FAILURE");
                DiagnosticsLog.Install("=== SELF TEST END ===");
                return 1;
            }
        }

        private static void ValidatePalette()
        {
            IReadOnlyList<PaletteColor> colors = PaletteDefinition.Colors;
            Assert(colors.Count == 15, "Palette must contain exactly 15 colors.");
            Assert(colors.Select(color => color.Hex).Distinct(StringComparer.OrdinalIgnoreCase).Count() == 15, "Palette colors must be unique.");
            Assert(colors.Count(color => color.Category == PaletteCategory.Main) == 3, "Main category must contain 3 colors.");
            Assert(colors.Count(color => color.Category == PaletteCategory.Additional) == 6, "Additional category must contain 6 colors.");
            Assert(colors.Count(color => color.Category == PaletteCategory.Status) == 6, "Status category must contain 6 colors.");

            int expected = 0x80 | (0xE3 << 8) | (0xFF << 16);
            Assert(PaletteColor.ToOfficeRgb("80E3FF") == expected, "Office RGB conversion failed.");
        }

        private static void ValidateRibbonXml()
        {
            string xml = RibbonFactory.Build();
            var document = new XmlDocument();
            document.LoadXml(xml);

            var namespaceManager = new XmlNamespaceManager(document.NameTable);
            namespaceManager.AddNamespace("r", "http://schemas.microsoft.com/office/2006/01/customui");

            XmlNode tab = document.SelectSingleNode("//r:tab[@id='domrfPaletteTab' and @label='ПАЛИТРА']", namespaceManager);
            Assert(tab != null, "Ribbon tab is missing.");

            XmlNodeList applyButtons = document.SelectNodes("//r:button[@onAction='ApplyColor']", namespaceManager);
            Assert(applyButtons != null && applyButtons.Count == 45, "Ribbon must contain exactly 45 color action buttons.");

            var tags = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            var modeCounts = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase)
            {
                ["fill"] = 0,
                ["text"] = 0,
                ["line"] = 0
            };

            foreach (XmlNode button in applyButtons)
            {
                string tag = button.Attributes?["tag"]?.Value;
                Assert(!string.IsNullOrWhiteSpace(tag), "Every color button must contain a tag.");
                Assert(tags.Add(tag), "Duplicate Ribbon tag: " + tag);

                string[] parts = tag.Split('|');
                Assert(parts.Length == 2, "Invalid Ribbon tag: " + tag);
                Assert(modeCounts.ContainsKey(parts[0]), "Invalid Ribbon mode: " + parts[0]);
                PaletteDefinition.FindByHex(parts[1]);
                modeCounts[parts[0]]++;
            }

            Assert(modeCounts.Values.All(count => count == 15), "Each Ribbon mode must contain 15 colors.");

            string[] callbacks = { "RibbonLoaded", "GetSwatchImage", "ApplyColor", "OpenAddInLog" };
            foreach (string callback in callbacks)
            {
                MethodInfo method = typeof(PaletteAddIn).GetMethod(callback, BindingFlags.Instance | BindingFlags.Public);
                Assert(method != null, "Public Ribbon callback is missing: " + callback);
            }
        }

        private static void ValidateComClass()
        {
            Type type = typeof(PaletteAddIn);
            Assert(type.GetConstructor(Type.EmptyTypes) != null, "COM add-in requires a public parameterless constructor.");
            Assert(type.GetCustomAttribute<ComVisibleAttribute>()?.Value == true, "PaletteAddIn must be COM-visible.");
            Assert(string.Equals(type.GetCustomAttribute<ProgIdAttribute>()?.Value, InstallerConstants.ProgId, StringComparison.Ordinal), "Unexpected ProgID.");
            Assert(string.Equals(type.GetCustomAttribute<GuidAttribute>()?.Value, InstallerConstants.AddInClassId, StringComparison.OrdinalIgnoreCase), "Unexpected CLSID.");
            Assert(typeof(IDTExtensibility2).IsAssignableFrom(type), "PaletteAddIn must implement IDTExtensibility2.");
            Assert(typeof(IRibbonExtensibility).IsAssignableFrom(type), "PaletteAddIn must implement IRibbonExtensibility.");
            Assert(typeof(IPaletteRibbonCallbacks).IsAssignableFrom(type), "PaletteAddIn must implement Ribbon callback dispatch interface.");
        }

        private static void ValidateSwatchConversion()
        {
            var addIn = new PaletteAddIn();
            object picture = addIn.GetSwatchImage(new FakeRibbonControl { Tag = "fill|80E3FF" });
            Assert(picture != null, "Swatch image conversion returned null.");
        }

        private static void Assert(bool condition, string message)
        {
            if (!condition)
            {
                throw new InvalidOperationException(message);
            }
        }
    }
}
