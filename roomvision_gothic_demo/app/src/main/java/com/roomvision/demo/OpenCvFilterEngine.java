package com.roomvision.demo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class OpenCvFilterEngine {
    private static final int MAX_SIDE = 640;
    private final Random random = new Random(0x524f4f4d);

    OpenCvFilterEngine() {
        if (!OpenCVLoader.initLocal()) {
            throw new IllegalStateException("OpenCV init failed");
        }
    }

    Bitmap process(Bitmap source, FilterType type) {
        Bitmap input = scaleForRealtime(source);
        if (type == FilterType.ORIGINAL) return input;
        if (type == FilterType.PIXEL_ART) return pixelArt(input, 18);
        if (type == FilterType.ASCII_ART) return ascii(input);
        if (type == FilterType.GAME_BOY) return gameBoy(input);
        if (type == FilterType.CYBERPUNK) return tintPixel(input, 0.82f, 0.62f, 1.35f, 18, -3, 28);
        if (type == FilterType.RETRO || type == FilterType.OLDIE) return sepia(input, type == FilterType.OLDIE ? 0.78f : 0.55f);
        if (type == FilterType.GRUNGE) return grunge(input);
        if (type == FilterType.MATTE) return matte(input);
        if (type == FilterType.WARHOL) return warhol(input);
        if (type == FilterType.FROSTED) return frosted(input);
        if (type == FilterType.BUBBLES) return bubbles(input);

        Mat rgba = new Mat();
        Utils.bitmapToMat(input, rgba);
        Mat out = new Mat();
        try {
            switch (type) {
                case CARTOON: cartoon(rgba, out, false); break;
                case COMIC: cartoon(rgba, out, true); break;
                case COMIC_BW: comicBw(rgba, out); break;
                case CARTOON_EDGE: neonEdges(rgba, out, false); break;
                case SKETCH: sketch(rgba, out, false); break;
                case PENCIL: sketch(rgba, out, true); break;
                case COLOR_PENCIL: colorPencil(rgba, out); break;
                case CHARCOAL: charcoal(rgba, out); break;
                case CRAYON: crayon(rgba, out); break;
                case INK_WASH: inkWash(rgba, out); break;
                case OIL_PAINTING: oil(rgba, out); break;
                case WATERCOLOR: watercolor(rgba, out); break;
                case PAINTING: painting(rgba, out); break;
                case PASTEL: pastel(rgba, out); break;
                case MANGA: manga(rgba, out); break;
                case CROSSHATCH: crosshatch(rgba, out); break;
                case CUTOUT: cutout(rgba, out); break;
                case POSTER: poster(rgba, out, 48); break;
                case HALFTONE: halftone(rgba, out); break;
                case THERMAL: thermal(rgba, out); break;
                case NEON_EDGE: neonEdges(rgba, out, true); break;
                case NOIR: noir(rgba, out); break;
                case MONO: mono(rgba, out); break;
                case AQUA: aqua(rgba, out); break;
                case STAINED_GLASS: stained(rgba, out); break;
                case DOTS: dots(rgba, out); break;
                case EMBOSS: emboss(rgba, out); break;
                case CONTOURS: contours(rgba, out); break;
                default: rgba.copyTo(out); break;
            }
            Bitmap result = bitmapFromMat(out);
            if (input != source && !input.isRecycled()) input.recycle();
            return result;
        } finally {
            rgba.release();
            out.release();
        }
    }

    private Bitmap scaleForRealtime(Bitmap source) {
        int w = source.getWidth();
        int h = source.getHeight();
        int max = Math.max(w, h);
        if (max <= MAX_SIDE) return source.copy(Bitmap.Config.ARGB_8888, false);
        float s = MAX_SIDE / (float) max;
        return Bitmap.createScaledBitmap(source, Math.max(2, Math.round(w * s)), Math.max(2, Math.round(h * s)), true);
    }

    private Bitmap bitmapFromMat(Mat src) {
        Mat rgba = new Mat();
        try {
            if (src.channels() == 1) Imgproc.cvtColor(src, rgba, Imgproc.COLOR_GRAY2RGBA);
            else if (src.channels() == 3) Imgproc.cvtColor(src, rgba, Imgproc.COLOR_RGB2RGBA);
            else src.copyTo(rgba);
            Bitmap b = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(rgba, b);
            return b;
        } finally {
            rgba.release();
        }
    }

    private void gray(Mat rgba, Mat out) { Imgproc.cvtColor(rgba, out, Imgproc.COLOR_RGBA2GRAY); }

    private void cartoon(Mat rgba, Mat out, boolean stronger) {
        Mat rgb = new Mat(), smooth = new Mat(), g = new Mat(), edges = new Mat();
        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
            Imgproc.bilateralFilter(rgb, smooth, stronger ? 11 : 9, stronger ? 95 : 70, stronger ? 95 : 70);
            Imgproc.cvtColor(rgb, g, Imgproc.COLOR_RGB2GRAY);
            Imgproc.medianBlur(g, g, 5);
            Imgproc.adaptiveThreshold(g, edges, 255, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 9, 5);
            if (stronger) posterizeMat(smooth, smooth, 48);
            Core.bitwise_and(smooth, smooth, out, edges);
        } finally { rgb.release(); smooth.release(); g.release(); edges.release(); }
    }

    private void comicBw(Mat rgba, Mat out) {
        Mat g = new Mat(), blur = new Mat();
        try {
            gray(rgba, g);
            Imgproc.GaussianBlur(g, blur, new Size(5,5), 0);
            Imgproc.adaptiveThreshold(blur, out, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 5);
        } finally { g.release(); blur.release(); }
    }

    private void sketch(Mat rgba, Mat out, boolean hard) {
        Mat g = new Mat(), inv = new Mat(), blur = new Mat();
        try {
            gray(rgba, g);
            Core.bitwise_not(g, inv);
            Imgproc.GaussianBlur(inv, blur, new Size(hard ? 17 : 25, hard ? 17 : 25), 0);
            Core.bitwise_not(blur, blur);
            Core.divide(g, blur, out, 256.0);
            if (hard) Imgproc.threshold(out, out, 168, 255, Imgproc.THRESH_BINARY);
        } finally { g.release(); inv.release(); blur.release(); }
    }

    private void colorPencil(Mat rgba, Mat out) {
        Mat pencil = new Mat(), rgb = new Mat(), pencil3 = new Mat();
        try {
            sketch(rgba, pencil, false);
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
            Imgproc.cvtColor(pencil, pencil3, Imgproc.COLOR_GRAY2RGB);
            Core.addWeighted(rgb, 0.58, pencil3, 0.42, 10, out);
        } finally { pencil.release(); rgb.release(); pencil3.release(); }
    }

    private void charcoal(Mat rgba, Mat out) {
        Mat g = new Mat();
        try {
            gray(rgba, g);
            Imgproc.GaussianBlur(g, g, new Size(3,3), 0);
            Imgproc.adaptiveThreshold(g, out, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 15, 7);
            Core.bitwise_not(out, out);
        } finally { g.release(); }
    }

    private void crayon(Mat rgba, Mat out) {
        Mat rgb = new Mat(), edges = new Mat(), g = new Mat();
        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
            posterizeMat(rgb, rgb, 56);
            Imgproc.cvtColor(rgb, g, Imgproc.COLOR_RGB2GRAY);
            Imgproc.Canny(g, edges, 80, 160);
            Core.bitwise_not(edges, edges);
            Core.bitwise_and(rgb, rgb, out, edges);
            out.convertTo(out, -1, 1.08, 8);
        } finally { rgb.release(); edges.release(); g.release(); }
    }

    private void inkWash(Mat rgba, Mat out) {
        Mat rgb = new Mat(), smooth = new Mat(), g = new Mat(), g3 = new Mat();
        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
            Imgproc.bilateralFilter(rgb, smooth, 9, 85, 85);
            Imgproc.cvtColor(smooth, g, Imgproc.COLOR_RGB2GRAY);
            Imgproc.cvtColor(g, g3, Imgproc.COLOR_GRAY2RGB);
            Core.addWeighted(smooth, 0.28, g3, 0.72, 18, out);
        } finally { rgb.release(); smooth.release(); g.release(); g3.release(); }
    }

    private void oil(Mat rgba, Mat out) {
        Mat rgb = new Mat(), tmp = new Mat();
        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
            Imgproc.bilateralFilter(rgb, tmp, 11, 110, 110);
            Imgproc.bilateralFilter(tmp, out, 9, 85, 85);
            posterizeMat(out, out, 32);
        } finally { rgb.release(); tmp.release(); }
    }

    private void watercolor(Mat rgba, Mat out) {
        Mat rgb = new Mat(), a = new Mat();
        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
            Imgproc.bilateralFilter(rgb, a, 13, 95, 95);
            Imgproc.bilateralFilter(a, out, 9, 70, 70);
            out.convertTo(out, -1, 1.08, 8);
        } finally { rgb.release(); a.release(); }
    }

    private void painting(Mat rgba, Mat out) {
        Mat rgb = new Mat(), blur = new Mat();
        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
            Imgproc.GaussianBlur(rgb, blur, new Size(0,0), 2.2);
            Core.addWeighted(rgb, 1.55, blur, -0.55, 8, out);
            posterizeMat(out, out, 40);
        } finally { rgb.release(); blur.release(); }
    }

    private void pastel(Mat rgba, Mat out) {
        Mat rgb = new Mat();
        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
            Imgproc.bilateralFilter(rgb, out, 9, 65, 65);
            posterizeMat(out, out, 64);
            out.convertTo(out, -1, 0.85, 34);
        } finally { rgb.release(); }
    }

    private void manga(Mat rgba, Mat out) {
        Mat g = new Mat(), edges = new Mat();
        try {
            gray(rgba, g);
            Imgproc.Canny(g, edges, 55, 120);
            Core.bitwise_not(edges, edges);
            Imgproc.threshold(g, g, 170, 255, Imgproc.THRESH_BINARY);
            Core.bitwise_and(g, edges, out);
        } finally { g.release(); edges.release(); }
    }

    private void crosshatch(Mat rgba, Mat out) {
        Mat base = new Mat();
        try {
            sketch(rgba, base, false);
            base.copyTo(out);
            int step = 14;
            for (int y = 0; y < out.rows(); y += step) {
                Imgproc.line(out, new Point(0, y), new Point(out.cols(), Math.min(out.rows()-1, y + out.cols()/5.0)), new Scalar(90), 1);
            }
        } finally { base.release(); }
    }

    private void cutout(Mat rgba, Mat out) {
        Mat rgb = new Mat(), smooth = new Mat();
        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
            Imgproc.bilateralFilter(rgb, smooth, 9, 90, 90);
            posterizeMat(smooth, out, 72);
        } finally { rgb.release(); smooth.release(); }
    }

    private void poster(Mat rgba, Mat out, int step) {
        Mat rgb = new Mat();
        try { Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB); posterizeMat(rgb, out, step); }
        finally { rgb.release(); }
    }

    private void posterizeMat(Mat src, Mat dst, int step) {
        Mat tmp = src.clone();
        byte[] data = new byte[(int)(tmp.total() * tmp.channels())];
        tmp.get(0,0,data);
        int s = Math.max(8, step);
        for (int i=0;i<data.length;i++) {
            int v = data[i] & 255;
            int q = Math.min(255, (v / s) * s + s/2);
            data[i] = (byte)q;
        }
        tmp.put(0,0,data);
        tmp.copyTo(dst);
        tmp.release();
    }

    private void halftone(Mat rgba, Mat out) {
        Mat g = new Mat();
        try {
            gray(rgba, g);
            out.create(g.rows(), g.cols(), CvType.CV_8UC3);
            out.setTo(new Scalar(245,245,245));
            int step = 10;
            for (int y=step/2;y<g.rows();y+=step) for(int x=step/2;x<g.cols();x+=step) {
                double v = g.get(y,x)[0];
                int r = Math.max(1, Math.min(step/2, (int)Math.round((255-v)/255.0*(step/2))));
                Imgproc.circle(out, new Point(x,y), r, new Scalar(25,25,25), -1);
            }
        } finally { g.release(); }
    }

    private void thermal(Mat rgba, Mat out) {
        Mat g = new Mat();
        try { gray(rgba, g); Imgproc.applyColorMap(g, out, Imgproc.COLORMAP_INFERNO); }
        finally { g.release(); }
    }

    private void neonEdges(Mat rgba, Mat out, boolean neon) {
        Mat g = new Mat(), edges = new Mat();
        try {
            gray(rgba, g);
            Imgproc.GaussianBlur(g, g, new Size(3,3), 0);
            Imgproc.Canny(g, edges, 55, 130);
            if (neon) Imgproc.applyColorMap(edges, out, Imgproc.COLORMAP_TURBO);
            else Imgproc.cvtColor(edges, out, Imgproc.COLOR_GRAY2RGB);
        } finally { g.release(); edges.release(); }
    }

    private void noir(Mat rgba, Mat out) {
        Mat g = new Mat();
        try { gray(rgba, g); Core.convertScaleAbs(g, out, 1.35, -28); }
        finally { g.release(); }
    }

    private void mono(Mat rgba, Mat out) { gray(rgba, out); }

    private void aqua(Mat rgba, Mat out) {
        Mat g = new Mat();
        try { gray(rgba, g); Imgproc.applyColorMap(g, out, Imgproc.COLORMAP_OCEAN); }
        finally { g.release(); }
    }

    private void stained(Mat rgba, Mat out) {
        Mat rgb = new Mat(), edges = new Mat(), g = new Mat();
        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
            posterizeMat(rgb, rgb, 64);
            Imgproc.cvtColor(rgb, g, Imgproc.COLOR_RGB2GRAY);
            Imgproc.Canny(g, edges, 80, 160);
            Core.bitwise_not(edges, edges);
            Core.bitwise_and(rgb, rgb, out, edges);
        } finally { rgb.release(); edges.release(); g.release(); }
    }

    private void dots(Mat rgba, Mat out) { halftone(rgba, out); }

    private void emboss(Mat rgba, Mat out) {
        Mat g = new Mat(), k = new Mat(3,3,CvType.CV_32F);
        try {
            gray(rgba, g);
            k.put(0,0,new float[]{-2,-1,0,-1,1,1,0,1,2});
            Imgproc.filter2D(g, out, -1, k);
            out.convertTo(out, -1, 1.0, 96);
        } finally { g.release(); k.release(); }
    }

    private void contours(Mat rgba, Mat out) {
        Mat g = new Mat(), edges = new Mat();
        try { gray(rgba, g); Imgproc.Canny(g, edges, 60, 140); Core.bitwise_not(edges, out); }
        finally { g.release(); edges.release(); }
    }

    private Bitmap pixelArt(Bitmap in, int divisor) {
        int w = Math.max(8, in.getWidth()/divisor), h = Math.max(8, in.getHeight()/divisor);
        Bitmap small = Bitmap.createScaledBitmap(in,w,h,false);
        Bitmap out = Bitmap.createScaledBitmap(small,in.getWidth(),in.getHeight(),false);
        small.recycle(); in.recycle(); return out;
    }

    private Bitmap gameBoy(Bitmap in) {
        int[] p = pixels(in); int[] pal={0xff0f380f,0xff306230,0xff8bac0f,0xff9bbc0f};
        for(int i=0;i<p.length;i++){int c=p[i],g=(int)(.299*((c>>16)&255)+.587*((c>>8)&255)+.114*(c&255));p[i]=pal[Math.min(3,g/64)];}
        return fromPixelsAndRecycle(in,p);
    }

    private Bitmap tintPixel(Bitmap in,float rm,float gm,float bm,int ro,int go,int bo){
        int[] p=pixels(in); for(int i=0;i<p.length;i++){int c=p[i];int r=clamp((int)(((c>>16)&255)*rm)+ro),g=clamp((int)(((c>>8)&255)*gm)+go),b=clamp((int)((c&255)*bm)+bo);p[i]=0xff000000|(r<<16)|(g<<8)|b;}return fromPixelsAndRecycle(in,p);
    }

    private Bitmap sepia(Bitmap in,float strength){
        int[] p=pixels(in); for(int i=0;i<p.length;i++){int c=p[i],r=(c>>16)&255,g=(c>>8)&255,b=c&255;int sr=clamp((int)(.393*r+.769*g+.189*b)),sg=clamp((int)(.349*r+.686*g+.168*b)),sb=clamp((int)(.272*r+.534*g+.131*b));r=clamp((int)(r*(1-strength)+sr*strength));g=clamp((int)(g*(1-strength)+sg*strength));b=clamp((int)(b*(1-strength)+sb*strength));p[i]=0xff000000|(r<<16)|(g<<8)|b;}return fromPixelsAndRecycle(in,p);
    }

    private Bitmap grunge(Bitmap in){int[]p=pixels(in);for(int i=0;i<p.length;i++){int c=p[i],n=((i*1103515245+12345)>>>24)-128;int r=clamp(((c>>16)&255)+n/5-12),g=clamp(((c>>8)&255)+n/6-15),b=clamp((c&255)+n/7-17);p[i]=0xff000000|(r<<16)|(g<<8)|b;}return fromPixelsAndRecycle(in,p);}
    private Bitmap matte(Bitmap in){int[]p=pixels(in);for(int i=0;i<p.length;i++){int c=p[i],r=clamp((int)(((c>>16)&255)*.72+38)),g=clamp((int)(((c>>8)&255)*.72+36)),b=clamp((int)((c&255)*.72+34));p[i]=0xff000000|(r<<16)|(g<<8)|b;}return fromPixelsAndRecycle(in,p);}
    private Bitmap warhol(Bitmap in){int[]p=pixels(in);for(int i=0;i<p.length;i++){int c=p[i],g=(int)(.3*((c>>16)&255)+.59*((c>>8)&255)+.11*(c&255));int r=g>170?255:g>85?245:20,gg=g>170?210:g>85?30:210,b=g>170?40:g>85?180:245;p[i]=0xff000000|(r<<16)|(gg<<8)|b;}return fromPixelsAndRecycle(in,p);}
    private Bitmap frosted(Bitmap in){int[]p=pixels(in);int w=in.getWidth(),h=in.getHeight();int[]q=p.clone();for(int y=2;y<h-2;y++)for(int x=2;x<w-2;x++){int j=y*w+x;int off=((x*31+y*17)&3)-1;q[j]=p[(y+off)*w+Math.max(0,Math.min(w-1,x-off))];}return fromPixelsAndRecycle(in,q);}
    private Bitmap bubbles(Bitmap in){Bitmap out=in.copy(Bitmap.Config.ARGB_8888,true);Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(Color.argb(100,255,255,255));for(int y=18;y<out.getHeight();y+=34)for(int x=18;x<out.getWidth();x+=34)c.drawCircle(x,y,10+((x+y)%8),p);in.recycle();return out;}
    private Bitmap ascii(Bitmap in){Bitmap out=Bitmap.createBitmap(in.getWidth(),in.getHeight(),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.drawColor(Color.BLACK);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.WHITE);paint.setTextSize(11);paint.setTypeface(android.graphics.Typeface.MONOSPACE);String chars=" .:-=+*#%@";int step=10;int[]p=pixels(in);int w=in.getWidth();for(int y=step;y<in.getHeight();y+=step)for(int x=0;x<in.getWidth();x+=step){int col=p[Math.min(p.length-1,y*w+x)],g=(int)(.299*((col>>16)&255)+.587*((col>>8)&255)+.114*(col&255));char ch=chars.charAt(Math.min(chars.length()-1,g*(chars.length()-1)/255));c.drawText(String.valueOf(ch),x,y,paint);}in.recycle();return out;}
    private int[] pixels(Bitmap b){int[]p=new int[b.getWidth()*b.getHeight()];b.getPixels(p,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());return p;}
    private Bitmap fromPixelsAndRecycle(Bitmap in,int[]p){Bitmap out=Bitmap.createBitmap(in.getWidth(),in.getHeight(),Bitmap.Config.ARGB_8888);out.setPixels(p,0,in.getWidth(),0,0,in.getWidth(),in.getHeight());in.recycle();return out;}
    private int clamp(int v){return Math.max(0,Math.min(255,v));}
}
