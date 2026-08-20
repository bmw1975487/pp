using System;
using System.Collections.Generic;
using System.Drawing;
using System.Globalization;
using System.Windows.Forms;

namespace DOMRF.PowerPointPalette
{
    internal static class SwatchImageFactory
    {
        private sealed class PictureDispHost : AxHost
        {
            private PictureDispHost() : base(null)
            {
            }

            public static object Convert(Image image)
            {
                return GetIPictureDispFromPicture(image);
            }
        }

        private sealed class CachedImage
        {
            public CachedImage(Bitmap bitmap, object pictureDisp)
            {
                Bitmap = bitmap;
                PictureDisp = pictureDisp;
            }

            public Bitmap Bitmap { get; }
            public object PictureDisp { get; }
        }

        private static readonly object Sync = new object();
        private static readonly Dictionary<string, CachedImage> Cache = new Dictionary<string, CachedImage>(StringComparer.OrdinalIgnoreCase);

        public static object Get(string hex)
        {
            string normalized = PaletteColor.NormalizeHex(hex);
            lock (Sync)
            {
                if (Cache.TryGetValue(normalized, out CachedImage cached))
                {
                    return cached.PictureDisp;
                }

                Bitmap bitmap = CreateBitmap(normalized);
                object pictureDisp = PictureDispHost.Convert(bitmap);
                Cache.Add(normalized, new CachedImage(bitmap, pictureDisp));
                return pictureDisp;
            }
        }

        private static Bitmap CreateBitmap(string hex)
        {
            int red = int.Parse(hex.Substring(0, 2), NumberStyles.HexNumber, CultureInfo.InvariantCulture);
            int green = int.Parse(hex.Substring(2, 2), NumberStyles.HexNumber, CultureInfo.InvariantCulture);
            int blue = int.Parse(hex.Substring(4, 2), NumberStyles.HexNumber, CultureInfo.InvariantCulture);

            var bitmap = new Bitmap(24, 24, System.Drawing.Imaging.PixelFormat.Format32bppArgb);
            using (Graphics graphics = Graphics.FromImage(bitmap))
            using (var fillBrush = new SolidBrush(Color.FromArgb(255, red, green, blue)))
            using (var borderPen = new Pen(Color.FromArgb(110, 110, 110), 1f))
            {
                graphics.Clear(Color.Transparent);
                graphics.FillRectangle(fillBrush, 2, 2, 20, 20);
                graphics.DrawRectangle(borderPen, 1, 1, 21, 21);
            }

            bitmap.MakeTransparent(Color.Transparent);
            return bitmap;
        }
    }
}
