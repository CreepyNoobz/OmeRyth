using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

class Program
{
    private const int GWL_STYLE = -16;
    private const int WS_MINIMIZEBOX = 0x00020000;
    private const int WS_MAXIMIZEBOX = 0x00010000;
    private const int WS_THICKFRAME = 0x00040000;
    private const int WS_MAXIMIZE = 0x01000000;

    private const int SW_RESTORE = 9;
    private const int SW_SHOWNORMAL = 1;

    private const uint SWP_NOMOVE = 0x0002;
    private const uint SWP_NOSIZE = 0x0001;
    private const uint SWP_NOZORDER = 0x0004;
    private const uint SWP_FRAMECHANGED = 0x0020;

    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    private static extern bool IsZoomed(IntPtr hWnd);

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    [DllImport("user32.dll")]
    private static extern int GetWindowLong(IntPtr hWnd, int nIndex);

    [DllImport("user32.dll")]
    private static extern int SetWindowLong(IntPtr hWnd, int nIndex, int dwNewLong);

    [DllImport("user32.dll")]
    private static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);

    [DllImport("user32.dll")]
    private static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);

    [DllImport("kernel32.dll")]
    private static extern int GetCurrentThreadId();

    [DllImport("user32.dll")]
    private static extern bool EnumThreadWindows(int dwThreadId, EnumThreadDelegate lpfn, IntPtr lParam);

    private delegate bool EnumThreadDelegate(IntPtr hWnd, IntPtr lParam);

    [StructLayout(LayoutKind.Sequential)]
    private struct RECT
    {
        public int Left, Top, Right, Bottom;
        public int Width { get { return Right - Left; } }
        public int Height { get { return Bottom - Top; } }
    }

    private static void ResetRegistryMRU()
    {
        try
        {
            using (var key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Explorer\ComDlg32\CIDSizeMRU", true))
            {
                if (key != null)
                {
                    foreach (string valName in key.GetValueNames())
                    {
                        if (valName.IndexOf("NativeDialog", StringComparison.OrdinalIgnoreCase) >= 0 ||
                            valName.IndexOf("OmeRyth", StringComparison.OrdinalIgnoreCase) >= 0 ||
                            valName.IndexOf("java", StringComparison.OrdinalIgnoreCase) >= 0)
                        {
                            key.DeleteValue(valName, false);
                        }
                    }
                }
            }
        }
        catch { }
    }

    private static void StartDialogWatcher(int threadId)
    {
        Thread watcher = new Thread(() =>
        {
            for (int i = 0; i < 50; i++)
            {
                Thread.Sleep(40);
                bool found = false;
                EnumThreadWindows(threadId, (hwnd, lparam) =>
                {
                    RECT rect;
                    if (GetWindowRect(hwnd, out rect) && rect.Width > 150 && rect.Height > 150)
                    {
                        found = true;
                        int style = GetWindowLong(hwnd, GWL_STYLE);

                        // Si la fenêtre est maximisée, forcer la restauration immédiate
                        if (IsZoomed(hwnd) || (style & WS_MAXIMIZE) != 0)
                        {
                            ShowWindow(hwnd, SW_RESTORE);
                        }

                        // Ajouter boutons Réduire, Agrandir et bordure redimensionnable
                        int newStyle = style | WS_MINIMIZEBOX | WS_MAXIMIZEBOX | WS_THICKFRAME;
                        if (newStyle != style)
                        {
                            SetWindowLong(hwnd, GWL_STYLE, newStyle);
                        }

                        // Si la fenêtre dépasse ou est trop grande, la recentrer proprement
                        Screen scr = Screen.FromPoint(Cursor.Position);
                        Rectangle wa = scr.WorkingArea;
                        int curW = rect.Width;
                        int curH = rect.Height;

                        if (curW >= wa.Width || curH >= wa.Height || curW < 400 || curH < 300)
                        {
                            curW = Math.Min(960, (int)(wa.Width * 0.75));
                            curH = Math.Min(650, (int)(wa.Height * 0.75));
                            int posX = wa.Left + (wa.Width - curW) / 2;
                            int posY = wa.Top + (wa.Height - curH) / 2;
                            SetWindowPos(hwnd, IntPtr.Zero, posX, posY, curW, curH, SWP_NOZORDER | SWP_FRAMECHANGED);
                        }
                        else
                        {
                            SetWindowPos(hwnd, IntPtr.Zero, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_FRAMECHANGED);
                        }

                        return false; // Stop énumération
                    }
                    return true;
                }, IntPtr.Zero);

                if (found) break;
            }
        });
        watcher.IsBackground = true;
        watcher.Start();
    }

    [STAThread]
    static void Main(string[] args)
    {
        Console.OutputEncoding = Encoding.UTF8;
        if (args.Length == 0) return;

        string mode = args[0].ToLowerInvariant();
        if (mode == "check")
        {
            Console.WriteLine("OK");
            return;
        }

        ResetRegistryMRU();
        int currentThreadId = GetCurrentThreadId();

        if (mode == "open")
        {
            var dlg = new Microsoft.Win32.OpenFileDialog();
            if (args.Length > 1) dlg.Title = args[1];
            if (args.Length > 2) dlg.Filter = args[2];
            dlg.RestoreDirectory = true;

            StartDialogWatcher(currentThreadId);

            bool? res = dlg.ShowDialog();
            if (res == true && !string.IsNullOrEmpty(dlg.FileName))
            {
                Console.WriteLine(dlg.FileName);
            }
            else
            {
                Console.WriteLine("::CANCELLED::");
            }
            return;
        }

        if (mode == "save")
        {
            var dlg = new Microsoft.Win32.SaveFileDialog();
            if (args.Length > 1) dlg.Title = args[1];
            if (args.Length > 2) dlg.Filter = args[2];
            if (args.Length > 3) dlg.DefaultExt = args[3];
            dlg.RestoreDirectory = true;

            StartDialogWatcher(currentThreadId);

            bool? res = dlg.ShowDialog();
            if (res == true && !string.IsNullOrEmpty(dlg.FileName))
            {
                Console.WriteLine(dlg.FileName);
            }
            else
            {
                Console.WriteLine("::CANCELLED::");
            }
            return;
        }
    }
}
