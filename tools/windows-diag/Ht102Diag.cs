// HT-102 diagnostika - pomocne funkce (jen cteni, nic nemeni)
using System;
using System.IO;
using System.Text;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

[ComImport]
[Guid("5B0D3235-4DBA-4D44-865E-8F1D0E4FD04D")]
[InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface IMemoryBufferByteAccess
{
    void GetBuffer(out IntPtr buffer, out uint capacity);
}

public static class Ht102Diag
{
    // Primy pristup k pameti snimku (IMemoryBufferReference z BitmapBuffer.CreateReference)
    public static byte[] ReadMemoryBuffer(object reference)
    {
        IMemoryBufferByteAccess acc = (IMemoryBufferByteAccess)reference;
        IntPtr p;
        uint cap;
        acc.GetBuffer(out p, out cap);
        byte[] d = new byte[(int)cap];
        Marshal.Copy(p, d, 0, (int)cap);
        return d;
    }

    [DllImport("cfgmgr32.dll", CharSet = CharSet.Unicode)]
    static extern int CM_Get_Device_Interface_List_SizeW(out int len, ref Guid g, string devId, int flags);

    [DllImport("cfgmgr32.dll", CharSet = CharSet.Unicode)]
    static extern int CM_Get_Device_Interface_ListW(ref Guid g, string devId, char[] buf, int len, int flags);

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    static extern IntPtr CreateFileW(string name, uint access, uint share, IntPtr sa, uint disp, uint flags, IntPtr tmpl);

    [DllImport("kernel32.dll", SetLastError = true)]
    static extern bool DeviceIoControl(IntPtr h, uint code, byte[] inb, int inlen, byte[] outb, int outlen, out int ret, IntPtr ov);

    [DllImport("kernel32.dll")]
    static extern bool CloseHandle(IntPtr h);

    // Cesta k rozhrani USB hubu (GUID_DEVINTERFACE_USB_HUB) podle instance ID hubu
    public static string HubPath(string hubInstanceId)
    {
        Guid g = new Guid("f18a0e88-c30c-11d0-8815-00a0c906bed8");
        int len;
        int r = CM_Get_Device_Interface_List_SizeW(out len, ref g, hubInstanceId, 0);
        if (r != 0 || len <= 1) return null;
        char[] buf = new char[len];
        r = CM_Get_Device_Interface_ListW(ref g, hubInstanceId, buf, len, 0);
        if (r != 0) return null;
        string s = new string(buf);
        int z = s.IndexOf('\0');
        return z > 0 ? s.Substring(0, z) : null;
    }

    // GET_DESCRIPTOR pres hub (IOCTL_USB_GET_DESCRIPTOR_FROM_NODE_CONNECTION) - stejne jako USBView
    public static byte[] GetDescriptor(string hubPath, int port, int type, int index, int langId, int length)
    {
        IntPtr h = CreateFileW(hubPath, 0x40000000, 2, IntPtr.Zero, 3, 0, IntPtr.Zero);
        if (h == new IntPtr(-1)) throw new Exception("CreateFile hub selhal, chyba " + Marshal.GetLastWin32Error());
        try
        {
            byte[] b = new byte[12 + length];
            BitConverter.GetBytes((uint)port).CopyTo(b, 0);
            b[4] = 0x80;
            b[5] = 6;
            BitConverter.GetBytes((ushort)((type << 8) | index)).CopyTo(b, 6);
            BitConverter.GetBytes((ushort)langId).CopyTo(b, 8);
            BitConverter.GetBytes((ushort)length).CopyTo(b, 10);
            int ret;
            if (!DeviceIoControl(h, 0x220410, b, b.Length, b, b.Length, out ret, IntPtr.Zero))
                throw new Exception("IOCTL selhal, chyba " + Marshal.GetLastWin32Error());
            byte[] d = new byte[Math.Max(0, ret - 12)];
            Array.Copy(b, 12, d, 0, d.Length);
            return d;
        }
        finally
        {
            CloseHandle(h);
        }
    }

    public static string Hex(byte[] d, int offset, int count)
    {
        StringBuilder sb = new StringBuilder();
        int end = Math.Min(d.Length, offset + count);
        for (int i = offset; i < end; i++) sb.Append(d[i].ToString("X2")).Append(' ');
        return sb.ToString().TrimEnd();
    }

    // Rozpis konfiguracniho deskriptoru po jednotlivych deskriptorech (jeden na radek)
    public static string SplitDescriptors(byte[] cfg)
    {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < cfg.Length)
        {
            int len = cfg[i];
            if (len < 2) { sb.AppendLine("  (neplatna delka na " + i + ")"); break; }
            sb.AppendLine(string.Format("  @{0,4}: {1}", i, Hex(cfg, i, len)));
            i += len;
        }
        return sb.ToString();
    }

    // Statistika snimku + PNG s jasovou slozkou
    public static string AnalyzeFrame(byte[] d, int w, int h, string fmt, string pngPath)
    {
        StringBuilder sb = new StringBuilder();
        int n = w * h;
        byte[] y = new byte[n];
        if (fmt == "Yuy2" && d.Length >= n * 2) { for (int i = 0; i < n; i++) y[i] = d[i * 2]; }
        else if ((fmt == "Bgra8" || fmt == "Rgba8") && d.Length >= n * 4) { for (int i = 0; i < n; i++) y[i] = (byte)((d[i * 4] * 29 + d[i * 4 + 1] * 150 + d[i * 4 + 2] * 77) >> 8); }
        else if ((fmt == "Nv12" || fmt == "Gray8") && d.Length >= n) { Array.Copy(d, y, n); }
        else if (fmt == "Gray16" && d.Length >= n * 2) { for (int i = 0; i < n; i++) y[i] = d[i * 2 + 1]; }
        else return "   neznamy format " + fmt + " (" + d.Length + " B)";

        double[] rm = new double[h];
        double[] rs = new double[h];
        double gm = 0;
        for (int r = 0; r < h; r++)
        {
            double s = 0, s2 = 0;
            for (int c = 0; c < w; c++) { double v = y[r * w + c]; s += v; s2 += v * v; }
            rm[r] = s / w;
            rs[r] = Math.Sqrt(Math.Max(0, s2 / w - rm[r] * rm[r]));
            gm += rm[r];
        }
        gm /= h;
        sb.AppendLine(string.Format("   jas prumer {0:F1}", gm));
        sb.Append("   radky prumer/odchylka - prvni: ");
        for (int r = 0; r < Math.Min(4, h); r++) sb.AppendFormat("{0}:{1:F0}/{2:F0} ", r, rm[r], rs[r]);
        sb.AppendLine();
        sb.Append("   posledni: ");
        for (int r = Math.Max(0, h - 12); r < h; r++) sb.AppendFormat("{0}:{1:F0}/{2:F0} ", r, rm[r], rs[r]);
        sb.AppendLine();
        int an = 0;
        for (int r = 1; r < h - 1 && an < 20; r++)
        {
            double nb = (rm[r - 1] + rm[r + 1]) / 2;
            double nbs = Math.Max(4, (rs[r - 1] + rs[r + 1]) / 2);
            if (Math.Abs(rm[r] - nb) > 25 || rs[r] > 3 * nbs)
            {
                sb.AppendLine(string.Format("   neobvykly radek {0}: {1:F0}/{2:F0}", r, rm[r], rs[r]));
                an++;
            }
        }
        if (fmt == "Yuy2")
        {
            int cnt = n / 2;
            double us = 0, vs = 0, u2 = 0, v2 = 0;
            for (int i = 0; i < cnt; i++) { double u = d[i * 4 + 1], v = d[i * 4 + 3]; us += u; vs += v; u2 += u * u; v2 += v * v; }
            double um = us / cnt, vm = vs / cnt;
            sb.AppendLine(string.Format("   U prumer {0:F1} odch {1:F1}; V prumer {2:F1} odch {3:F1}",
                um, Math.Sqrt(Math.Max(0, u2 / cnt - um * um)), vm, Math.Sqrt(Math.Max(0, v2 / cnt - vm * vm))));
        }
        int bpr = d.Length / h;
        int[] rowsToDump = new int[] { 0, 1, h - 2, h - 1 };
        foreach (int r in rowsToDump)
        {
            if (r < 0 || r >= h) continue;
            sb.AppendLine("   hex radek " + r + ": " + Hex(d, r * bpr, Math.Min(48, bpr)));
        }
        if (!string.IsNullOrEmpty(pngPath))
        {
            using (Bitmap bmp = new Bitmap(w, h, PixelFormat.Format24bppRgb))
            {
                BitmapData bd = bmp.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.WriteOnly, PixelFormat.Format24bppRgb);
                byte[] row = new byte[bd.Stride];
                for (int r = 0; r < h; r++)
                {
                    for (int c = 0; c < w; c++) { byte v = y[r * w + c]; row[c * 3] = v; row[c * 3 + 1] = v; row[c * 3 + 2] = v; }
                    Marshal.Copy(row, 0, new IntPtr(bd.Scan0.ToInt64() + (long)r * bd.Stride), bd.Stride);
                }
                bmp.UnlockBits(bd);
                bmp.Save(pngPath, ImageFormat.Png);
            }
        }
        return sb.ToString();
    }

    static byte Clamp(double x) { return (byte)(x < 0 ? 0 : (x > 255 ? 255 : x)); }

    static void PutRgb(byte[] row, int c, int y, int u, int v)
    {
        double yy = 1.164 * (y - 16);
        row[c * 3 + 2] = Clamp(yy + 1.596 * v);
        row[c * 3 + 1] = Clamp(yy - 0.392 * u - 0.813 * v);
        row[c * 3] = Clamp(yy + 2.017 * u);
    }

    // Barevny nahled YUY2 snimku jako PNG
    public static void Yuy2ToRgbPng(byte[] d, int w, int h, string path)
    {
        using (Bitmap bmp = new Bitmap(w, h, PixelFormat.Format24bppRgb))
        {
            BitmapData bd = bmp.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.WriteOnly, PixelFormat.Format24bppRgb);
            byte[] row = new byte[bd.Stride];
            for (int r = 0; r < h; r++)
            {
                for (int c = 0; c + 1 < w; c += 2)
                {
                    int i = (r * w + c) * 2;
                    if (i + 3 >= d.Length) break;
                    int u = d[i + 1] - 128, v = d[i + 3] - 128;
                    PutRgb(row, c, d[i], u, v);
                    PutRgb(row, c + 1, d[i + 2], u, v);
                }
                Marshal.Copy(row, 0, new IntPtr(bd.Scan0.ToInt64() + (long)r * bd.Stride), bd.Stride);
            }
            bmp.UnlockBits(bd);
            bmp.Save(path, ImageFormat.Png);
        }
    }

    // Porovnani dvou po sobe jdoucich snimku (ktere radky se meni)
    public static string FrameDiff(byte[] a, byte[] b, int h)
    {
        if (a == null || b == null || a.Length != b.Length || h <= 0) return "   (nelze porovnat)";
        int bpr = a.Length / h;
        double tot = 0, maxRow = 0;
        int changedRows = 0, maxIdx = -1, firstChanged = -1, lastChanged = -1;
        for (int r = 0; r < h; r++)
        {
            double s = 0;
            for (int i = r * bpr; i < (r + 1) * bpr; i++) s += Math.Abs(a[i] - b[i]);
            double rd = s / bpr;
            tot += rd;
            if (rd > 0.5) { changedRows++; if (firstChanged < 0) firstChanged = r; lastChanged = r; }
            if (rd > maxRow) { maxRow = rd; maxIdx = r; }
        }
        return string.Format("   rozdil proti predchozimu: prumer {0:F2}, zmenenych radku {1}/{2} (od {3} do {4}), nejvetsi zmena na radku {5} ({6:F1})",
            tot / h, changedRows, h, firstChanged, lastChanged, maxIdx, maxRow);
    }

    // Struktura MJPEG dat: pocet snimku, segmenty hlavicky, data za koncem snimku
    public static string JpegInfo(byte[] d)
    {
        StringBuilder sb = new StringBuilder();
        int frames = 0;
        for (int i = 0; i + 1 < d.Length; i++) if (d[i] == 0xFF && d[i + 1] == 0xD8) frames++;
        sb.AppendLine("   JPEG snimku: " + frames + ", celkem " + d.Length + " B");
        int p = 0;
        while (p + 1 < d.Length && !(d[p] == 0xFF && d[p + 1] == 0xD8)) p++;
        if (p > 0) sb.AppendLine("   pred prvnim SOI je " + p + " B: " + Hex(d, 0, Math.Min(32, p)));
        p += 2;
        int segs = 0;
        while (p + 3 < d.Length && segs < 40)
        {
            if (d[p] != 0xFF) { sb.AppendLine("   (necekany bajt na " + p + ")"); break; }
            byte m = d[p + 1];
            if (m == 0xD9) break;
            int len = (d[p + 2] << 8) | d[p + 3];
            string line = string.Format("   segment FF{0:X2} delka {1}", m, len);
            if ((m >= 0xE0 && m <= 0xEF) || m == 0xFE) line += " data: " + Hex(d, p + 4, Math.Min(40, len - 2));
            sb.AppendLine(line);
            if (m == 0xDA)
            {
                int q = p + 2 + len;
                while (q + 1 < d.Length && !(d[q] == 0xFF && d[q + 1] == 0xD9)) q++;
                int eoi = q + 2;
                int next = eoi;
                while (next + 1 < d.Length && !(d[next] == 0xFF && d[next + 1] == 0xD8)) next++;
                if (next + 1 >= d.Length) next = d.Length;
                sb.AppendLine(string.Format("   obrazova data {0} B, konec snimku na {1}, za koncem {2} B navic", eoi - p, eoi, next - eoi));
                if (next - eoi > 0) sb.AppendLine("   navic (zacatek): " + Hex(d, eoi, Math.Min(64, next - eoi)));
                break;
            }
            p += 2 + len;
            segs++;
        }
        return sb.ToString();
    }
}
