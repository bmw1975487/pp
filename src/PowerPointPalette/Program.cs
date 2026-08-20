using System;
using System.Linq;
using System.Windows.Forms;

namespace DOMRF.PowerPointPalette
{
    internal static class Program
    {
        [STAThread]
        private static int Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            string[] normalizedArgs = args ?? Array.Empty<string>();
            if (normalizedArgs.Any(arg => string.Equals(arg, "--self-test", StringComparison.OrdinalIgnoreCase)))
            {
                return SelfTest.Run();
            }

            return InstallerHost.Install();
        }
    }
}
