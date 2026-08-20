using System;
using System.IO;

namespace DOMRF.PowerPointPalette
{
    internal static class InstallerConstants
    {
        public const string ProgId = "DOMRF.PowerPointPalette";
        public const string AddInClassId = "C70E01E0-54E7-4B34-8E33-1DA1471BCF6E";
        public const string FriendlyName = "DOM.RF — 15 цветов";
        public const string Description = "Фирменная палитра из 15 цветов для PowerPoint";
        public const string InstalledFileName = "PowerPoint_15_Colors_Setup.exe";
        public const string AddInRegistryPath = @"Software\Microsoft\Office\PowerPoint\Addins\" + ProgId;

        public static string InstallDirectory => Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
            "DOMRF",
            "PowerPointPalette");

        public static string InstalledExecutablePath => Path.Combine(InstallDirectory, InstalledFileName);

        public static string OldPpamPath => Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "Microsoft",
            "AddIns",
            "DOMRF_15_COLORS.ppam");
    }
}
