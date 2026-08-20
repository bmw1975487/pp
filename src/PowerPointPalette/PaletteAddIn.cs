using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace DOMRF.PowerPointPalette
{
    [ComVisible(true)]
    [Guid(InstallerConstants.AddInClassId)]
    [ProgId(InstallerConstants.ProgId)]
    [ClassInterface(ClassInterfaceType.None)]
    [ComDefaultInterface(typeof(IPaletteRibbonCallbacks))]
    public sealed class PaletteAddIn : IDTExtensibility2, IRibbonExtensibility, IPaletteRibbonCallbacks
    {
        private object _application;
        private object _ribbonUi;

        public PaletteAddIn()
        {
            DiagnosticsLog.AddIn("PaletteAddIn constructor. Assembly=" + typeof(PaletteAddIn).Assembly.Location);
        }

        public void OnConnection(object application, ExtConnectMode connectMode, object addInInst, ref Array custom)
        {
            try
            {
                _application = application;
                DiagnosticsLog.AddIn("OnConnection. Mode=" + connectMode);

                try
                {
                    dynamic app = application;
                    DiagnosticsLog.AddIn("PowerPoint version=" + Convert.ToString(app.Version));
                }
                catch (Exception exception)
                {
                    DiagnosticsLog.AddIn("Unable to read PowerPoint version: " + exception.Message);
                }
            }
            catch (Exception exception)
            {
                DiagnosticsLog.AddInException(exception, "OnConnection");
                throw;
            }
        }

        public void OnDisconnection(ExtDisconnectMode removeMode, ref Array custom)
        {
            DiagnosticsLog.AddIn("OnDisconnection. Mode=" + removeMode);
            _ribbonUi = null;
            _application = null;
        }

        public void OnAddInsUpdate(ref Array custom)
        {
            DiagnosticsLog.AddIn("OnAddInsUpdate");
        }

        public void OnStartupComplete(ref Array custom)
        {
            DiagnosticsLog.AddIn("OnStartupComplete");
        }

        public void OnBeginShutdown(ref Array custom)
        {
            DiagnosticsLog.AddIn("OnBeginShutdown");
        }

        public string GetCustomUI(string ribbonId)
        {
            try
            {
                DiagnosticsLog.AddIn("GetCustomUI. RibbonId=" + ribbonId);
                string xml = RibbonFactory.Build();
                DiagnosticsLog.AddIn("GetCustomUI returned XML length=" + xml.Length);
                return xml;
            }
            catch (Exception exception)
            {
                DiagnosticsLog.AddInException(exception, "GetCustomUI");
                throw;
            }
        }

        public void RibbonLoaded(object ribbonUi)
        {
            _ribbonUi = ribbonUi;
            DiagnosticsLog.AddIn("RibbonLoaded: SUCCESS");
        }

        public object GetSwatchImage(object control)
        {
            try
            {
                string tag = GetControlTag(control);
                string[] parts = tag.Split('|');
                if (parts.Length != 2)
                {
                    throw new InvalidOperationException("Invalid Ribbon control tag: " + tag);
                }

                PaletteColor color = PaletteDefinition.FindByHex(parts[1]);
                return SwatchImageFactory.Get(color.Hex);
            }
            catch (Exception exception)
            {
                DiagnosticsLog.AddInException(exception, "GetSwatchImage");
                return null;
            }
        }

        public void ApplyColor(object control)
        {
            try
            {
                string tag = GetControlTag(control);
                string[] parts = tag.Split('|');
                if (parts.Length != 2)
                {
                    throw new InvalidOperationException("Invalid Ribbon control tag: " + tag);
                }

                string mode = parts[0];
                PaletteColor color = PaletteDefinition.FindByHex(parts[1]);
                int appliedCount = ColorApplicationService.Apply(_application, mode, color);
                DiagnosticsLog.AddIn("ApplyColor SUCCESS. Mode=" + mode + ", Hex=#" + color.Hex + ", Count=" + appliedCount);
            }
            catch (Exception exception)
            {
                DiagnosticsLog.AddInException(exception, "ApplyColor");
                MessageBox.Show(
                    exception.Message,
                    "Палитра PowerPoint",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Warning);
            }
        }

        public void OpenAddInLog(object control)
        {
            try
            {
                DiagnosticsLog.AddIn("OpenAddInLog");
                if (!System.IO.File.Exists(DiagnosticsLog.AddInLogPath))
                {
                    DiagnosticsLog.AddIn("Log file created on request.");
                }

                Process.Start(new ProcessStartInfo
                {
                    FileName = DiagnosticsLog.AddInLogPath,
                    UseShellExecute = true
                });
            }
            catch (Exception exception)
            {
                DiagnosticsLog.AddInException(exception, "OpenAddInLog");
                MessageBox.Show(exception.Message, "Палитра PowerPoint", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private static string GetControlTag(object control)
        {
            if (control == null)
            {
                throw new ArgumentNullException(nameof(control));
            }

            dynamic ribbonControl = control;
            string tag = Convert.ToString(ribbonControl.Tag);
            if (string.IsNullOrWhiteSpace(tag))
            {
                throw new InvalidOperationException("Ribbon control does not contain a color tag.");
            }

            return tag;
        }
    }
}
