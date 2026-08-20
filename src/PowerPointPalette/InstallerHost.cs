using Microsoft.Win32;
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace DOMRF.PowerPointPalette
{
    internal sealed class OfficeInfo
    {
        public string PowerPointPath { get; set; }
        public string Platform { get; set; }
        public string Version { get; set; }
        public bool Is64Bit => string.Equals(Platform, "x64", StringComparison.OrdinalIgnoreCase);
    }

    internal sealed class ProcessResult
    {
        public int ExitCode { get; set; }
        public string StandardOutput { get; set; }
        public string StandardError { get; set; }
    }

    internal static class InstallerHost
    {
        private const int MsoTrue = -1;

        public static int Install()
        {
            DiagnosticsLog.ResetInstallLog();
            DiagnosticsLog.Install("=== POWERPOINT 15 COLORS INSTALL START ===");
            DiagnosticsLog.Install("Build: 1.0.0 COM_ADDIN_SINGLE_EXE");
            DiagnosticsLog.Install("Source executable: " + GetCurrentExecutablePath());
            DiagnosticsLog.Install("OS: " + Environment.OSVersion);
            DiagnosticsLog.Install("Process bitness: " + (Environment.Is64BitProcess ? "64-bit" : "32-bit"));
            DiagnosticsLog.Install("User: " + Environment.UserDomainName + "\\" + Environment.UserName);

            try
            {
                if (!Environment.OSVersion.Platform.Equals(PlatformID.Win32NT))
                {
                    throw new PlatformNotSupportedException("Установщик работает только в Windows.");
                }

                OfficeInfo office = DetectOffice();
                DiagnosticsLog.Install("PowerPoint path: " + office.PowerPointPath);
                DiagnosticsLog.Install("Office platform: " + office.Platform);
                DiagnosticsLog.Install("Office version: " + office.Version);

                WaitUntilPowerPointIsClosed();
                CleanupPreviousInstallations(office);
                string installedPath = CopyInstallerToPermanentLocation();
                string regAsmPath = GetRegAsmPath(office);

                RegisterComAssembly(regAsmPath, installedPath);
                RegisterPowerPointAddIn();
                VerifyRegistry(installedPath, office);
                VerifyInPowerPoint();

                DiagnosticsLog.Install("Launching PowerPoint with a new blank presentation.");
                Process.Start(new ProcessStartInfo
                {
                    FileName = office.PowerPointPath,
                    Arguments = "/B",
                    UseShellExecute = true
                });

                DiagnosticsLog.Install("RESULT: SUCCESS");
                DiagnosticsLog.Install("=== POWERPOINT 15 COLORS INSTALL END ===");

                MessageBox.Show(
                    "Установка завершена.\n\nВ PowerPoint появилась вкладка «ПАЛИТРА» с 15 фирменными цветами.",
                    "PowerPoint — 15 цветов",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Information);
                return 0;
            }
            catch (OperationCanceledException)
            {
                DiagnosticsLog.Install("RESULT: CANCELLED BY USER");
                DiagnosticsLog.Install("=== POWERPOINT 15 COLORS INSTALL END ===");
                return 2;
            }
            catch (Exception exception)
            {
                DiagnosticsLog.InstallException(exception, "INSTALL");
                DiagnosticsLog.Install("RESULT: FAILURE");
                DiagnosticsLog.Install("=== POWERPOINT 15 COLORS INSTALL END ===");

                MessageBox.Show(
                    "Установка не выполнена.\n\nЛог:\n" + DiagnosticsLog.InstallLogPath,
                    "PowerPoint — 15 цветов",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
                OpenLog(DiagnosticsLog.InstallLogPath);
                return 1;
            }
        }

        private static OfficeInfo DetectOffice()
        {
            string platform = null;
            string version = null;
            string powerPointPath = null;

            foreach (RegistryView view in GetRegistryViews())
            {
                using (RegistryKey localMachine = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, view))
                {
                    using (RegistryKey clickToRun = localMachine.OpenSubKey(@"SOFTWARE\Microsoft\Office\ClickToRun\Configuration"))
                    {
                        if (clickToRun != null)
                        {
                            platform = platform ?? Convert.ToString(clickToRun.GetValue("Platform"));
                            version = version ?? Convert.ToString(clickToRun.GetValue("VersionToReport"));
                        }
                    }

                    using (RegistryKey appPath = localMachine.OpenSubKey(@"SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\POWERPNT.EXE"))
                    {
                        string candidate = Convert.ToString(appPath?.GetValue(null));
                        if (!string.IsNullOrWhiteSpace(candidate) && File.Exists(candidate))
                        {
                            powerPointPath = candidate;
                        }
                    }
                }
            }

            if (string.IsNullOrWhiteSpace(powerPointPath))
            {
                string[] candidates =
                {
                    Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Microsoft Office", "Root", "Office16", "POWERPNT.EXE"),
                    Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Microsoft Office", "Root", "Office16", "POWERPNT.EXE")
                };

                foreach (string candidate in candidates)
                {
                    if (!string.IsNullOrWhiteSpace(candidate) && File.Exists(candidate))
                    {
                        powerPointPath = candidate;
                        break;
                    }
                }
            }

            if (string.IsNullOrWhiteSpace(powerPointPath) || !File.Exists(powerPointPath))
            {
                throw new FileNotFoundException("Microsoft PowerPoint не найден на этом компьютере.");
            }

            if (string.IsNullOrWhiteSpace(platform))
            {
                string x86Root = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);
                platform = !string.IsNullOrWhiteSpace(x86Root) && powerPointPath.StartsWith(x86Root, StringComparison.OrdinalIgnoreCase)
                    ? "x86"
                    : "x64";
            }

            return new OfficeInfo
            {
                PowerPointPath = powerPointPath,
                Platform = platform,
                Version = string.IsNullOrWhiteSpace(version) ? "unknown" : version
            };
        }

        private static IEnumerable<RegistryView> GetRegistryViews()
        {
            if (Environment.Is64BitOperatingSystem)
            {
                yield return RegistryView.Registry64;
                yield return RegistryView.Registry32;
            }
            else
            {
                yield return RegistryView.Registry32;
            }
        }

        private static void WaitUntilPowerPointIsClosed()
        {
            while (Process.GetProcessesByName("POWERPNT").Length > 0)
            {
                DiagnosticsLog.Install("PowerPoint process detected. Waiting for user to close it.");
                DialogResult answer = MessageBox.Show(
                    "PowerPoint сейчас открыт.\n\nСохраните презентации, полностью закройте PowerPoint и нажмите «Повторить».",
                    "PowerPoint — 15 цветов",
                    MessageBoxButtons.RetryCancel,
                    MessageBoxIcon.Warning);

                if (answer == DialogResult.Cancel)
                {
                    throw new OperationCanceledException();
                }

                Thread.Sleep(700);
            }

            DiagnosticsLog.Install("PowerPoint is closed.");
        }

        private static void CleanupPreviousInstallations(OfficeInfo office)
        {
            DiagnosticsLog.Install("Cleaning previous project registrations.");

            string installedPath = InstallerConstants.InstalledExecutablePath;
            string regAsmPath = GetRegAsmPath(office);
            if (File.Exists(installedPath))
            {
                ProcessResult unregisterResult = RunProcess(regAsmPath, Quote(installedPath) + " /nologo /unregister", false);
                DiagnosticsLog.Install("Previous RegAsm unregister exit code: " + unregisterResult.ExitCode);
                DiagnosticsLog.Install("Previous RegAsm unregister stdout: " + unregisterResult.StandardOutput);
                DiagnosticsLog.Install("Previous RegAsm unregister stderr: " + unregisterResult.StandardError);
            }

            DeleteCurrentUserSubKey(InstallerConstants.AddInRegistryPath);
            DeleteCurrentUserSubKey(@"Software\Microsoft\Office\PowerPoint\Addins\DOMRF.PowerPointPalette.Native");
            DeleteCurrentUserSubKey(@"Software\Microsoft\Office\PowerPoint\Addins\PowerPointPalette.Connect");
            DeleteCurrentUserSubKey(@"Software\Microsoft\Office\PowerPoint\Addins\DOMRF_15_COLORS");

            foreach (RegistryView view in GetRegistryViews())
            {
                DeleteClassesRootSubKey(view, @"CLSID\{" + InstallerConstants.AddInClassId + "}");
                DeleteClassesRootSubKey(view, InstallerConstants.ProgId);
            }

            DeleteFileIfPresent(InstallerConstants.OldPpamPath);
            DeleteFileIfPresent(Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "Microsoft",
                "PowerPoint",
                "Startup",
                "DOMRF_15_COLORS.ppam"));
        }

        private static string CopyInstallerToPermanentLocation()
        {
            string sourcePath = GetCurrentExecutablePath();
            string destinationPath = InstallerConstants.InstalledExecutablePath;
            Directory.CreateDirectory(InstallerConstants.InstallDirectory);

            if (!string.Equals(sourcePath, destinationPath, StringComparison.OrdinalIgnoreCase))
            {
                if (File.Exists(destinationPath))
                {
                    File.SetAttributes(destinationPath, FileAttributes.Normal);
                }

                File.Copy(sourcePath, destinationPath, true);
            }

            TryRemoveZoneIdentifier(destinationPath);
            DiagnosticsLog.Install("Installed executable: " + destinationPath);
            DiagnosticsLog.Install("Installed SHA256: " + ComputeSha256(destinationPath));
            return destinationPath;
        }

        private static string GetRegAsmPath(OfficeInfo office)
        {
            string frameworkFolder = office.Is64Bit ? "Framework64" : "Framework";
            string path = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.Windows),
                "Microsoft.NET",
                frameworkFolder,
                "v4.0.30319",
                "RegAsm.exe");

            if (!File.Exists(path))
            {
                throw new FileNotFoundException("Не найден системный RegAsm.exe для " + office.Platform + ".", path);
            }

            DiagnosticsLog.Install("RegAsm path: " + path);
            return path;
        }

        private static void RegisterComAssembly(string regAsmPath, string installedPath)
        {
            DiagnosticsLog.Install("Registering managed COM assembly.");
            ProcessResult result = RunProcess(regAsmPath, Quote(installedPath) + " /nologo /codebase", true);
            DiagnosticsLog.Install("RegAsm exit code: " + result.ExitCode);
            DiagnosticsLog.Install("RegAsm stdout: " + result.StandardOutput);
            DiagnosticsLog.Install("RegAsm stderr: " + result.StandardError);

            if (result.ExitCode != 0)
            {
                throw new InvalidOperationException("RegAsm завершился с кодом " + result.ExitCode + ".");
            }
        }

        private static void RegisterPowerPointAddIn()
        {
            DiagnosticsLog.Install("Creating PowerPoint Addins registry key.");
            using (RegistryKey key = Registry.CurrentUser.CreateSubKey(InstallerConstants.AddInRegistryPath, true))
            {
                if (key == null)
                {
                    throw new InvalidOperationException("Не удалось создать ключ регистрации надстройки PowerPoint.");
                }

                key.SetValue("FriendlyName", InstallerConstants.FriendlyName, RegistryValueKind.String);
                key.SetValue("Description", InstallerConstants.Description, RegistryValueKind.String);
                key.SetValue("LoadBehavior", 3, RegistryValueKind.DWord);
                key.SetValue("CommandLineSafe", 0, RegistryValueKind.DWord);
            }
        }

        private static void VerifyRegistry(string installedPath, OfficeInfo office)
        {
            DiagnosticsLog.Install("Verifying registry registration.");

            using (RegistryKey addInKey = Registry.CurrentUser.OpenSubKey(InstallerConstants.AddInRegistryPath))
            {
                if (addInKey == null)
                {
                    throw new InvalidOperationException("PowerPoint Addins registry key is missing after installation.");
                }

                int loadBehavior = Convert.ToInt32(addInKey.GetValue("LoadBehavior", 0));
                DiagnosticsLog.Install("LoadBehavior=" + loadBehavior);
                if (loadBehavior != 3)
                {
                    throw new InvalidOperationException("LoadBehavior must equal 3, actual value: " + loadBehavior);
                }
            }

            RegistryView view = office.Is64Bit ? RegistryView.Registry64 : RegistryView.Registry32;
            using (RegistryKey classesRoot = RegistryKey.OpenBaseKey(RegistryHive.ClassesRoot, view))
            using (RegistryKey classKey = classesRoot.OpenSubKey(@"CLSID\{" + InstallerConstants.AddInClassId + @"}\InprocServer32"))
            {
                if (classKey == null)
                {
                    throw new InvalidOperationException("COM CLSID registration is missing.");
                }

                string codeBase = Convert.ToString(classKey.GetValue("CodeBase"));
                string assemblyName = Convert.ToString(classKey.GetValue("Assembly"));
                DiagnosticsLog.Install("COM Assembly=" + assemblyName);
                DiagnosticsLog.Install("COM CodeBase=" + codeBase);

                if (string.IsNullOrWhiteSpace(codeBase) || codeBase.IndexOf(Path.GetFileName(installedPath), StringComparison.OrdinalIgnoreCase) < 0)
                {
                    throw new InvalidOperationException("COM CodeBase does not point to the installed EXE.");
                }
            }
        }

        private static void VerifyInPowerPoint()
        {
            DiagnosticsLog.Install("Starting isolated PowerPoint verification.");
            DiagnosticsLog.ResetAddInLog();

            object applicationObject = null;
            object comAddInsObject = null;
            object comAddInObject = null;
            object presentationObject = null;

            try
            {
                Type powerPointType = Type.GetTypeFromProgID("PowerPoint.Application", throwOnError: false);
                if (powerPointType == null)
                {
                    throw new InvalidOperationException("PowerPoint.Application COM ProgID is not registered.");
                }

                applicationObject = Activator.CreateInstance(powerPointType);
                dynamic application = applicationObject;
                application.Visible = MsoTrue;

                comAddInsObject = application.COMAddIns;
                dynamic comAddIns = comAddInsObject;
                comAddIns.Update();

                comAddInObject = comAddIns.Item(InstallerConstants.ProgId);
                dynamic comAddIn = comAddInObject;
                bool before = Convert.ToBoolean(comAddIn.Connect);
                DiagnosticsLog.Install("COMAddIn.Connect before=" + before);

                comAddIn.Connect = true;
                Thread.Sleep(1500);

                bool after = Convert.ToBoolean(comAddIn.Connect);
                DiagnosticsLog.Install("COMAddIn.Connect after=" + after);
                if (!after)
                {
                    throw new InvalidOperationException("PowerPoint обнаружил COM-надстройку, но не подключил её.");
                }

                presentationObject = application.Presentations.Add(MsoTrue);
                WaitForAddInSignal("OnConnection", TimeSpan.FromSeconds(15));
                WaitForAddInSignal("GetCustomUI", TimeSpan.FromSeconds(15));
                WaitForAddInSignal("RibbonLoaded: SUCCESS", TimeSpan.FromSeconds(20));

                DiagnosticsLog.Install("PowerPoint verification succeeded: COM connected and RibbonLoaded callback received.");

                try
                {
                    dynamic presentation = presentationObject;
                    presentation.Close();
                }
                catch (Exception exception)
                {
                    DiagnosticsLog.Install("Verification presentation close warning: " + exception.Message);
                }

                presentationObject = null;
                application.Quit();
            }
            finally
            {
                SafeReleaseComObject(presentationObject);
                SafeReleaseComObject(comAddInObject);
                SafeReleaseComObject(comAddInsObject);
                SafeReleaseComObject(applicationObject);
                GC.Collect();
                GC.WaitForPendingFinalizers();
            }
        }

        private static void WaitForAddInSignal(string text, TimeSpan timeout)
        {
            Stopwatch stopwatch = Stopwatch.StartNew();
            while (stopwatch.Elapsed < timeout)
            {
                if (DiagnosticsLog.AddInLogContains(text))
                {
                    DiagnosticsLog.Install("Add-in signal received: " + text);
                    return;
                }

                Thread.Sleep(250);
            }

            string addInLog = File.Exists(DiagnosticsLog.AddInLogPath)
                ? File.ReadAllText(DiagnosticsLog.AddInLogPath, Encoding.UTF8)
                : "<add-in log missing>";
            DiagnosticsLog.Install("Add-in log at timeout:\n" + addInLog);
            throw new TimeoutException("Не получен сигнал надстройки: " + text);
        }

        private static ProcessResult RunProcess(string fileName, string arguments, bool throwOnStartFailure)
        {
            DiagnosticsLog.Install("RUN: " + fileName + " " + arguments);
            try
            {
                using (var process = new Process())
                {
                    process.StartInfo = new ProcessStartInfo
                    {
                        FileName = fileName,
                        Arguments = arguments,
                        UseShellExecute = false,
                        CreateNoWindow = true,
                        RedirectStandardOutput = true,
                        RedirectStandardError = true,
                        StandardOutputEncoding = Encoding.UTF8,
                        StandardErrorEncoding = Encoding.UTF8
                    };

                    process.Start();
                    string stdout = process.StandardOutput.ReadToEnd();
                    string stderr = process.StandardError.ReadToEnd();
                    process.WaitForExit();

                    return new ProcessResult
                    {
                        ExitCode = process.ExitCode,
                        StandardOutput = stdout.Trim(),
                        StandardError = stderr.Trim()
                    };
                }
            }
            catch when (!throwOnStartFailure)
            {
                return new ProcessResult
                {
                    ExitCode = -1,
                    StandardOutput = string.Empty,
                    StandardError = "Unable to start process."
                };
            }
        }

        private static void DeleteCurrentUserSubKey(string path)
        {
            try
            {
                Registry.CurrentUser.DeleteSubKeyTree(path, false);
                DiagnosticsLog.Install("Deleted HKCU\\" + path);
            }
            catch (Exception exception)
            {
                DiagnosticsLog.Install("Registry cleanup warning for HKCU\\" + path + ": " + exception.Message);
            }
        }

        private static void DeleteClassesRootSubKey(RegistryView view, string path)
        {
            try
            {
                using (RegistryKey classesRoot = RegistryKey.OpenBaseKey(RegistryHive.ClassesRoot, view))
                {
                    classesRoot.DeleteSubKeyTree(path, false);
                }

                DiagnosticsLog.Install("Deleted HKCR(" + view + ")\\" + path);
            }
            catch (Exception exception)
            {
                DiagnosticsLog.Install("Registry cleanup warning for HKCR(" + view + ")\\" + path + ": " + exception.Message);
            }
        }

        private static void DeleteFileIfPresent(string path)
        {
            try
            {
                if (File.Exists(path))
                {
                    File.SetAttributes(path, FileAttributes.Normal);
                    File.Delete(path);
                    DiagnosticsLog.Install("Deleted file: " + path);
                }
            }
            catch (Exception exception)
            {
                DiagnosticsLog.Install("File cleanup warning for " + path + ": " + exception.Message);
            }
        }

        private static void TryRemoveZoneIdentifier(string path)
        {
            try
            {
                string zonePath = path + ":Zone.Identifier";
                if (File.Exists(zonePath))
                {
                    File.Delete(zonePath);
                    DiagnosticsLog.Install("Removed Zone.Identifier from installed executable.");
                }
            }
            catch (Exception exception)
            {
                DiagnosticsLog.Install("Zone.Identifier cleanup warning: " + exception.Message);
            }
        }

        private static string ComputeSha256(string path)
        {
            using (SHA256 sha256 = SHA256.Create())
            using (FileStream stream = File.OpenRead(path))
            {
                byte[] hash = sha256.ComputeHash(stream);
                var builder = new StringBuilder(hash.Length * 2);
                foreach (byte value in hash)
                {
                    builder.Append(value.ToString("x2"));
                }

                return builder.ToString();
            }
        }

        private static void SafeReleaseComObject(object value)
        {
            if (value == null)
            {
                return;
            }

            try
            {
                if (Marshal.IsComObject(value))
                {
                    Marshal.FinalReleaseComObject(value);
                }
            }
            catch
            {
            }
        }

        private static string GetCurrentExecutablePath()
        {
            return Process.GetCurrentProcess().MainModule?.FileName
                ?? typeof(InstallerHost).Assembly.Location;
        }

        private static string Quote(string value)
        {
            return "\"" + value.Replace("\"", "\\\"") + "\"";
        }

        private static void OpenLog(string path)
        {
            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = path,
                    UseShellExecute = true
                });
            }
            catch
            {
            }
        }
    }
}
