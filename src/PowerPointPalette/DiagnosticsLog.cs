using System;
using System.IO;
using System.Text;

namespace DOMRF.PowerPointPalette
{
    internal static class DiagnosticsLog
    {
        private static readonly object Sync = new object();
        private static readonly Encoding Utf8 = new UTF8Encoding(encoderShouldEmitUTF8Identifier: true);

        public static string ProductDirectory => Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "DOMRF",
            "PowerPointPalette");

        public static string AddInLogPath => Path.Combine(ProductDirectory, "addin.log");

        public static string InstallLogPath
        {
            get
            {
                string desktop = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
                if (string.IsNullOrWhiteSpace(desktop) || !Directory.Exists(desktop))
                {
                    desktop = AppDomain.CurrentDomain.BaseDirectory;
                }

                return Path.Combine(desktop, "PowerPoint_15_Colors_INSTALL_LOG.txt");
            }
        }

        public static void ResetInstallLog()
        {
            EnsureParentDirectory(InstallLogPath);
            lock (Sync)
            {
                File.WriteAllText(InstallLogPath, string.Empty, Utf8);
            }
        }

        public static void ResetAddInLog()
        {
            EnsureParentDirectory(AddInLogPath);
            lock (Sync)
            {
                File.WriteAllText(AddInLogPath, string.Empty, Utf8);
            }
        }

        public static void Install(string message)
        {
            Write(InstallLogPath, message);
        }

        public static void AddIn(string message)
        {
            Write(AddInLogPath, message);
        }

        public static void InstallException(Exception exception, string stage)
        {
            Install("ERROR STAGE: " + stage);
            Install("ERROR TYPE: " + exception.GetType().FullName);
            Install("ERROR MESSAGE: " + exception.Message);
            Install("ERROR FULL: " + exception);
        }

        public static void AddInException(Exception exception, string stage)
        {
            AddIn("ERROR STAGE: " + stage);
            AddIn("ERROR TYPE: " + exception.GetType().FullName);
            AddIn("ERROR MESSAGE: " + exception.Message);
            AddIn("ERROR FULL: " + exception);
        }

        public static bool AddInLogContains(string text)
        {
            try
            {
                if (!File.Exists(AddInLogPath))
                {
                    return false;
                }

                string content;
                lock (Sync)
                {
                    content = File.ReadAllText(AddInLogPath, Utf8);
                }

                return content.IndexOf(text, StringComparison.OrdinalIgnoreCase) >= 0;
            }
            catch
            {
                return false;
            }
        }

        private static void Write(string path, string message)
        {
            try
            {
                EnsureParentDirectory(path);
                string line = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss.fff") + "  " + message + Environment.NewLine;
                lock (Sync)
                {
                    File.AppendAllText(path, line, Utf8);
                }
            }
            catch
            {
            }
        }

        private static void EnsureParentDirectory(string path)
        {
            string directory = Path.GetDirectoryName(path);
            if (!string.IsNullOrWhiteSpace(directory))
            {
                Directory.CreateDirectory(directory);
            }
        }
    }
}
