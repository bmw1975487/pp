using System;
using System.Runtime.InteropServices;

namespace DOMRF.PowerPointPalette
{
    public enum ExtConnectMode
    {
        AfterStartup = 0,
        Startup = 1,
        External = 2,
        CommandLine = 3,
        Solution = 4,
        UISetup = 5
    }

    public enum ExtDisconnectMode
    {
        HostShutdown = 0,
        UserClosed = 1
    }

    [ComImport]
    [Guid("B65AD801-ABAF-11D0-BB8B-00A0C90F2744")]
    [InterfaceType(ComInterfaceType.InterfaceIsDual)]
    public interface IDTExtensibility2
    {
        [DispId(1)]
        void OnConnection(
            [In, MarshalAs(UnmanagedType.IDispatch)] object application,
            [In] ExtConnectMode connectMode,
            [In, MarshalAs(UnmanagedType.IDispatch)] object addInInst,
            [In, Out, MarshalAs(UnmanagedType.SafeArray, SafeArraySubType = VarEnum.VT_VARIANT)] ref Array custom);

        [DispId(2)]
        void OnDisconnection(
            [In] ExtDisconnectMode removeMode,
            [In, Out, MarshalAs(UnmanagedType.SafeArray, SafeArraySubType = VarEnum.VT_VARIANT)] ref Array custom);

        [DispId(3)]
        void OnAddInsUpdate(
            [In, Out, MarshalAs(UnmanagedType.SafeArray, SafeArraySubType = VarEnum.VT_VARIANT)] ref Array custom);

        [DispId(4)]
        void OnStartupComplete(
            [In, Out, MarshalAs(UnmanagedType.SafeArray, SafeArraySubType = VarEnum.VT_VARIANT)] ref Array custom);

        [DispId(5)]
        void OnBeginShutdown(
            [In, Out, MarshalAs(UnmanagedType.SafeArray, SafeArraySubType = VarEnum.VT_VARIANT)] ref Array custom);
    }

    [ComImport]
    [Guid("000C0396-0000-0000-C000-000000000046")]
    [InterfaceType(ComInterfaceType.InterfaceIsDual)]
    public interface IRibbonExtensibility
    {
        [DispId(1)]
        [return: MarshalAs(UnmanagedType.BStr)]
        string GetCustomUI([MarshalAs(UnmanagedType.BStr)] string ribbonId);
    }

    [ComVisible(true)]
    [Guid("D4F71E57-6E10-4BF8-9E8C-5BBD924EEA15")]
    [InterfaceType(ComInterfaceType.InterfaceIsIDispatch)]
    public interface IPaletteRibbonCallbacks
    {
        [DispId(101)]
        void RibbonLoaded([MarshalAs(UnmanagedType.IDispatch)] object ribbonUi);

        [DispId(102)]
        [return: MarshalAs(UnmanagedType.IDispatch)]
        object GetSwatchImage([MarshalAs(UnmanagedType.IDispatch)] object control);

        [DispId(103)]
        void ApplyColor([MarshalAs(UnmanagedType.IDispatch)] object control);

        [DispId(104)]
        void OpenAddInLog([MarshalAs(UnmanagedType.IDispatch)] object control);
    }
}
