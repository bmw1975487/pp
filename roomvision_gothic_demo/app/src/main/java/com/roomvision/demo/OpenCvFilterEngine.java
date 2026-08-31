package com.roomvision.demo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.Random;

final class OpenCvFilterEngine {
    private static final int MAX_SIDE = 640;
    private static final int[][] BAYER4 = {
            {0, 8, 2, 10},
            {12, 4, 14, 6},
            {3, 11, 1, 9},
            {15, 7, 13, 5}
    };
    private final Random random = new Random(0x534b45544348L);

    OpenCvFilterEngine() {
        if (!OpenCVLoader.initLocal()) throw new IllegalStateException("OpenCV init failed");
    }

    Bitmap process(Bitmap source, FilterType type) {
        Bitmap input = scaleForRealtime(source);
        switch (type) {
            case ORIGINAL: return input;
            case PIXEL_ART: return pixelArt(input);
            case PIXEL_CYBERPUNK: return cyberpunk(input);
            case GAME_BOY: return gameBoy(input);
            case PIXEL_ASCII: return ascii(input);
            case BLUE_PEN: return bluePen(input);
            case PENCIL_2: return graphite(input, true);
            case PENCIL_3: return hardPencil(input);
            case CRAYON: return crayonBitmap(input);
            case CROSSHATCH: return crosshatchBitmap(input);
            case MANGA: return mangaBitmap(input);
            case PASTEL: return pastelBitmap(input);
            case SKETCHY: return sketchyBitmap(input);
            case OIL_FLOW: return oilFlowBitmap(input);
            case WATER: return waterBitmap(input);
            case WARHOL: return warhol(input);
            case STAINED_GLASS: return stainedGlass(input);
            case DOTS: return colorDots(input);
            case FROSTED: return frosted(input);
            case AMERICAN: return american(input);
            case BW: return sigmoidBw(input);
            default: break;
        }

        Mat rgba = new Mat();
        Utils.bitmapToMat(input, rgba);
        Mat out = new Mat();
        try {
            switch (type) {
                case PIXEL_COMIC: comicBook(rgba, out); break;
                case PIXEL_COMIC_BW: comicBw(rgba, out); break;
                case PIXEL_OIL: oilPainting(rgba, out, false); break;
                case PIXEL_WATERCOLOR: watercolor(rgba, out); break;
                case PIXEL_HALFTONE: halftone(rgba, out); break;
                case PIXEL_THERMAL: thermal(rgba, out); break;
                case PIXEL_INK_WASH: inkWash(rgba, out); break;
                case PIXEL_SKETCH: pencilSketch(rgba, out, false); break;
                case CARTOON_HD: cartoonHd(rgba, out); break;
                case PEN: pen(rgba, out); break;
                case PENCIL: pencilSketch(rgba, out, true); break;
                case COLOR_PENCIL: colorPencil(rgba, out); break;
                case COLOR_SKETCH: colorSketch(rgba, out); break;
                case CHARCOAL: charcoal(rgba, out); break;
                case SKETCH: fineSketch(rgba, out); break;
                case SK_OIL: oilPainting(rgba, out, true); break;
                case SK_OIL_2: oil2(rgba, out); break;
                case OILY: oily(rgba, out); break;
                case SK_COMICS: sketchComics(rgba, out); break;
                default: rgba.copyTo(out); break;
            }
            Bitmap result = bitmapFromMat(out);
            if (!input.isRecycled()) input.recycle();
            return result;
        } finally {
            rgba.release();
            out.release();
        }
    }

    private Bitmap scaleForRealtime(Bitmap source) {
        int w = source.getWidth(), h = source.getHeight();
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
        } finally { rgba.release(); }
    }

    private void gray(Mat rgba, Mat out) { Imgproc.cvtColor(rgba, out, Imgproc.COLOR_RGBA2GRAY); }

    // Pixel Lense: saturated comic with broad flat fills and strong black contours.
    private void comicBook(Mat rgba, Mat out) {
        Mat rgb = new Mat(), smooth = new Mat(), gray = new Mat(), edge = new Mat(), edge3 = new Mat();
        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB);
            Imgproc.bilateralFilter(rgb, smooth, 11, 95, 95);
            posterizeMat(smooth, smooth, 42);
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY);
            Imgproc.GaussianBlur(gray, gray, new Size(3,3), 0);
            Imgproc.Canny(gray, edge, 55, 115);
            Imgproc.dilate(edge, edge, Mat.ones(2,2,CvType.CV_8U));
            Core.bitwise_not(edge, edge);
            Imgproc.cvtColor(edge, edge3, Imgproc.COLOR_GRAY2RGB);
            Core.bitwise_and(smooth, edge3, out);
            out.convertTo(out, -1, 1.10, 7);
        } finally { rgb.release(); smooth.release(); gray.release(); edge.release(); edge3.release(); }
    }

    // Pixel Lense: monochrome comic with solid blacks, whites and coarse mid-tone screen.
    private void comicBw(Mat rgba, Mat out) {
        Mat g = new Mat(), blur = new Mat(), edges = new Mat();
        try {
            gray(rgba, g);
            Imgproc.GaussianBlur(g, blur, new Size(5,5), 0);
            Imgproc.Canny(blur, edges, 45, 105);
            out.create(g.rows(), g.cols(), CvType.CV_8UC1);
            byte[] gp = new byte[(int)g.total()]; g.get(0,0,gp);
            byte[] ep = new byte[(int)edges.total()]; edges.get(0,0,ep);
            byte[] dst = new byte[gp.length];
            int cols = g.cols();
            for (int i=0;i<gp.length;i++) {
                int y=i/cols, x=i-y*cols, v=gp[i]&255;
                if ((ep[i]&255)>0) dst[i]=0;
                else if (v < 75) dst[i]=0;
                else if (v < 170) dst[i]=(byte)(((x+y)%7<2)?70:225);
                else dst[i]=(byte)255;
            }
            out.put(0,0,dst);
        } finally { g.release(); blur.release(); edges.release(); }
    }

    private Bitmap pixelArt(Bitmap in) {
        int targetW = Math.max(32, in.getWidth()/10), targetH = Math.max(32, in.getHeight()/10);
        Bitmap small = Bitmap.createScaledBitmap(in, targetW, targetH, false);
        int[] p = pixels(small);
        for (int i=0;i<p.length;i++) {
            int c=p[i];
            int r=quant((c>>16)&255, 6), g=quant((c>>8)&255,6), b=quant(c&255,6);
            p[i]=0xff000000|(r<<16)|(g<<8)|b;
        }
        small.setPixels(p,0,targetW,0,0,targetW,targetH);
        Bitmap out=Bitmap.createScaledBitmap(small,in.getWidth(),in.getHeight(),false);
        small.recycle(); in.recycle(); return out;
    }

    private Bitmap cyberpunk(Bitmap in) {
        int[] p=pixels(in); int w=in.getWidth(),h=in.getHeight(); int[] out=p.clone();
        for(int y=1;y<h-1;y++) for(int x=1;x<w-1;x++) {
            int i=y*w+x,c=p[i],r=(c>>16)&255,g=(c>>8)&255,b=c&255;
            int lum=(r*54+g*183+b*19)>>8;
            int gx=Math.abs(luma(p[i+1])-luma(p[i-1]));
            int gy=Math.abs(luma(p[i+w])-luma(p[i-w]));
            int edge=Math.min(255,gx+gy);
            int nr=clamp((int)(lum*.42 + b*.30 + edge*1.45));
            int ng=clamp((int)(lum*.25 + g*.45 + edge*.72));
            int nb=clamp((int)(lum*.65 + r*.30 + edge*1.55));
            if(lum>155){ng=clamp(ng+50);nb=255;} else if(lum<80){nr=clamp(nr+50);nb=clamp(nb+42);}
            out[i]=0xff000000|(nr<<16)|(ng<<8)|nb;
        }
        return fromPixelsAndRecycle(in,out);
    }

    // Pixel Lense-like Game Boy: actual 4 shade DMG palette + ordered dithering + chunky pixels.
    private Bitmap gameBoy(Bitmap in) {
        int w=Math.max(64,in.getWidth()/4), h=Math.max(64,in.getHeight()/4);
        Bitmap small=Bitmap.createScaledBitmap(in,w,h,false); in.recycle();
        int[] p=pixels(small); int[] palette={0xff0f380f,0xff306230,0xff8bac0f,0xff9bbc0f};
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            int i=y*w+x,c=p[i],lum=luma(c);
            int bias=(BAYER4[y&3][x&3]-8)*6;
            int v=clamp(lum+bias);
            int idx=v<64?0:v<128?1:v<192?2:3;
            p[i]=palette[idx];
        }
        small.setPixels(p,0,w,0,0,w,h);
        Bitmap out=Bitmap.createScaledBitmap(small,w*4,h*4,false); small.recycle(); return out;
    }

    private void oilPainting(Mat rgba, Mat out, boolean sketchVersion) {
        Mat rgb=new Mat(), mean=new Mat(), blur=new Mat();
        try {
            Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);
            Imgproc.pyrMeanShiftFiltering(rgb,mean,sketchVersion?16:22,sketchVersion?35:45);
            Imgproc.medianBlur(mean,blur,sketchVersion?5:7);
            Core.addWeighted(mean,1.35,blur,-0.35,4,out);
            posterizeMat(out,out,sketchVersion?28:36);
        } finally {rgb.release();mean.release();blur.release();}
    }

    private void watercolor(Mat rgba, Mat out) {
        Mat rgb=new Mat(),a=new Mat(),b=new Mat(),g=new Mat(),edges=new Mat(),edges3=new Mat();
        try {
            Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);
            Imgproc.bilateralFilter(rgb,a,13,100,100);
            Imgproc.bilateralFilter(a,b,9,75,75);
            Imgproc.GaussianBlur(b,b,new Size(3,3),0);
            Imgproc.cvtColor(rgb,g,Imgproc.COLOR_RGB2GRAY);
            Imgproc.Canny(g,edges,35,85);
            Imgproc.GaussianBlur(edges,edges,new Size(5,5),0);
            Core.bitwise_not(edges,edges);
            Imgproc.cvtColor(edges,edges3,Imgproc.COLOR_GRAY2RGB);
            Core.addWeighted(b,0.88,edges3,0.12,17,out);
            out.convertTo(out,-1,0.94,14);
        } finally {rgb.release();a.release();b.release();g.release();edges.release();edges3.release();}
    }

    private void halftone(Mat rgba, Mat out) {
        Mat g=new Mat();
        try {
            gray(rgba,g); out.create(g.rows(),g.cols(),CvType.CV_8UC3); out.setTo(new Scalar(250,250,246));
            int step=9;
            for(int y=step/2;y<g.rows();y+=step) for(int x=step/2;x<g.cols();x+=step) {
                double v=g.get(y,x)[0]; int r=Math.max(1,Math.min(step/2,(int)Math.round((255-v)/255.0*(step/2))));
                Imgproc.circle(out,new Point(x,y),r,new Scalar(20,20,20),-1);
            }
        } finally {g.release();}
    }

    private void thermal(Mat rgba, Mat out) {
        Mat g=new Mat(); try {gray(rgba,g);Imgproc.applyColorMap(g,out,Imgproc.COLORMAP_INFERNO);} finally {g.release();}
    }

    private void inkWash(Mat rgba, Mat out) {
        Mat g=new Mat(),smooth=new Mat(),edges=new Mat(),wash=new Mat();
        try {
            gray(rgba,g); Imgproc.bilateralFilter(g,smooth,9,55,55);
            Imgproc.adaptiveThreshold(smooth,edges,255,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,17,8);
            Imgproc.GaussianBlur(smooth,wash,new Size(0,0),2.0);
            Core.addWeighted(wash,0.58,edges,0.42,34,out);
        } finally {g.release();smooth.release();edges.release();wash.release();}
    }

    private void pencilSketch(Mat rgba, Mat out, boolean dark) {
        Mat g=new Mat(),inv=new Mat(),blur=new Mat();
        try {
            gray(rgba,g); Core.bitwise_not(g,inv); Imgproc.GaussianBlur(inv,blur,new Size(dark?17:25,dark?17:25),0); Core.bitwise_not(blur,blur); Core.divide(g,blur,out,256.0);
            if(dark) Core.convertScaleAbs(out,out,1.18,-20);
        } finally {g.release();inv.release();blur.release();}
    }

    private Bitmap ascii(Bitmap in) {
        Bitmap out=Bitmap.createBitmap(in.getWidth(),in.getHeight(),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.drawColor(Color.rgb(4,8,7));
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(Color.rgb(110,255,188));paint.setTextSize(10);paint.setTypeface(Typeface.MONOSPACE);
        String chars="@%#*+=-:. ";int step=10;int[]p=pixels(in);int w=in.getWidth();
        for(int y=step;y<in.getHeight();y+=step)for(int x=0;x<in.getWidth();x+=step){int g=luma(p[Math.min(p.length-1,y*w+x)]);char ch=chars.charAt(Math.min(chars.length()-1,(255-g)*(chars.length()-1)/255));c.drawText(String.valueOf(ch),x,y,paint);}in.recycle();return out;
    }

    private void cartoonHd(Mat rgba, Mat out) {
        Mat rgb=new Mat(),a=new Mat(),b=new Mat(),g=new Mat(),edge=new Mat(),edge3=new Mat();
        try {
            Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);
            Imgproc.bilateralFilter(rgb,a,9,80,80);Imgproc.bilateralFilter(a,b,7,55,55);posterizeMat(b,b,45);
            Imgproc.cvtColor(rgb,g,Imgproc.COLOR_RGB2GRAY);Imgproc.medianBlur(g,g,5);Imgproc.adaptiveThreshold(g,edge,255,Imgproc.ADAPTIVE_THRESH_MEAN_C,Imgproc.THRESH_BINARY,9,4);Imgproc.cvtColor(edge,edge3,Imgproc.COLOR_GRAY2RGB);Core.bitwise_and(b,edge3,out);out.convertTo(out,-1,1.08,9);
        } finally {rgb.release();a.release();b.release();g.release();edge.release();edge3.release();}
    }

    // Sketch Camera Blue2: white paper + dense cobalt pen strokes/crosshatching.
    private Bitmap bluePen(Bitmap in) {
        int w=in.getWidth(),h=in.getHeight(); int[] src=pixels(in),dst=new int[src.length];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x,v=luma(src[i]);
            int gx=0,gy=0;if(x>0&&x<w-1)gx=Math.abs(luma(src[i+1])-luma(src[i-1]));if(y>0&&y<h-1)gy=Math.abs(luma(src[i+w])-luma(src[i-w]));
            int edge=Math.min(255,(gx+gy)*2);int hatch=0;
            if(v<205 && ((x+y)%7<2)) hatch=38;
            if(v<135 && ((x-y+10000)%9<2)) hatch+=48;
            if(v<75 && ((x+2*y)%5<2)) hatch+=60;
            int ink=clamp(edge+hatch+(190-v)/2);
            int r=clamp(255-ink);int g=clamp(255-ink);int b=clamp(255-ink/8);
            dst[i]=0xff000000|(r<<16)|(g<<8)|b;
        }
        return fromPixelsAndRecycle(in,dst);
    }

    private void pen(Mat rgba, Mat out) {
        Mat g=new Mat(),edge=new Mat();try{gray(rgba,g);Imgproc.GaussianBlur(g,g,new Size(3,3),0);Imgproc.Canny(g,edge,35,95);Imgproc.dilate(edge,edge,Mat.ones(2,2,CvType.CV_8U));Core.bitwise_not(edge,out);}finally{g.release();edge.release();}
    }

    private Bitmap graphite(Bitmap in, boolean soft) {
        int w=in.getWidth(),h=in.getHeight();int[]s=pixels(in),d=new int[s.length];
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){int i=y*w+x,v=luma(s[i]);int gx=Math.abs(luma(s[i+1])-luma(s[i-1])),gy=Math.abs(luma(s[i+w])-luma(s[i-w]));int line=clamp((gx+gy)*(soft?2:3));int grain=((x*17+y*29)&31)-16;int o=clamp(255-line-(255-v)/(soft?5:3)+grain);d[i]=0xff000000|(o<<16)|(o<<8)|o;}return fromPixelsAndRecycle(in,d);
    }

    private Bitmap hardPencil(Bitmap in) {
        int[]s=pixels(in),d=new int[s.length];int w=in.getWidth(),h=in.getHeight();
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){int i=y*w+x,v=luma(s[i]),e=Math.abs(luma(s[i+1])-luma(s[i-1]))+Math.abs(luma(s[i+w])-luma(s[i-w]));int o=(e>38||v<80)?20:(v<150?175:250);d[i]=0xff000000|(o<<16)|(o<<8)|o;}return fromPixelsAndRecycle(in,d);
    }

    private void colorPencil(Mat rgba, Mat out) {
        Mat rgb=new Mat(),sk=new Mat(),sk3=new Mat();try{Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);pencilSketch(rgba,sk,false);Imgproc.cvtColor(sk,sk3,Imgproc.COLOR_GRAY2RGB);Core.addWeighted(rgb,0.58,sk3,0.52,8,out);}finally{rgb.release();sk.release();sk3.release();}
    }

    private void colorSketch(Mat rgba, Mat out) {
        Mat rgb=new Mat(),g=new Mat(),edge=new Mat(),edge3=new Mat();try{Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);posterizeMat(rgb,rgb,54);Imgproc.cvtColor(rgb,g,Imgproc.COLOR_RGB2GRAY);Imgproc.Canny(g,edge,45,105);Core.bitwise_not(edge,edge);Imgproc.cvtColor(edge,edge3,Imgproc.COLOR_GRAY2RGB);Core.bitwise_and(rgb,edge3,out);out.convertTo(out,-1,1.18,8);}finally{rgb.release();g.release();edge.release();edge3.release();}
    }

    private void charcoal(Mat rgba, Mat out) {
        Mat g=new Mat(),blur=new Mat(),edge=new Mat();try{gray(rgba,g);Imgproc.GaussianBlur(g,blur,new Size(7,7),0);Imgproc.adaptiveThreshold(blur,edge,255,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,19,6);Core.bitwise_not(edge,edge);Core.addWeighted(g,0.35,edge,0.95,-35,out);Core.bitwise_not(out,out);}finally{g.release();blur.release();edge.release();}
    }

    private Bitmap crayonBitmap(Bitmap in) {
        int w=in.getWidth(),h=in.getHeight();int[]s=pixels(in),d=s.clone();
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){int i=y*w+x,c=s[i];int r=quant((c>>16)&255,5),g=quant((c>>8)&255,5),b=quant(c&255,5);int edge=Math.abs(luma(s[i+1])-luma(s[i-1]))+Math.abs(luma(s[i+w])-luma(s[i-w]));int grain=(((x*13+y*7)&15)-7)*2;r=clamp((int)(r*1.12)+grain);g=clamp((int)(g*1.12)+grain);b=clamp((int)(b*1.12)+grain);if(edge>45){r/=5;g/=5;b/=5;}d[i]=0xff000000|(r<<16)|(g<<8)|b;}return fromPixelsAndRecycle(in,d);
    }

    private Bitmap crosshatchBitmap(Bitmap in) {
        int w=in.getWidth(),h=in.getHeight();int[]s=pixels(in),d=s.clone();
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int i=y*w+x,c=s[i],v=luma(c);int r=clamp((int)(((c>>16)&255)*.72+48)),g=clamp((int)(((c>>8)&255)*.72+48)),b=clamp((int)((c&255)*.72+48));int ink=0;if(v<210&&(x+y)%8<2)ink+=32;if(v<150&&(x-y+10000)%8<2)ink+=44;if(v<90&&(2*x+y)%7<2)ink+=50;r=clamp(r-ink);g=clamp(g-ink);b=clamp(b-ink);d[i]=0xff000000|(r<<16)|(g<<8)|b;}return fromPixelsAndRecycle(in,d);
    }

    private Bitmap mangaBitmap(Bitmap in) {
        int w=in.getWidth(),h=in.getHeight();int[]s=pixels(in),d=new int[s.length];
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){int i=y*w+x,v=luma(s[i]);int e=Math.abs(luma(s[i+1])-luma(s[i-1]))+Math.abs(luma(s[i+w])-luma(s[i-w]));int o;if(e>42||v<58)o=0;else if(v<165)o=((x/3+y/3)&1)==0?75:235;else o=255;d[i]=0xff000000|(o<<16)|(o<<8)|o;}return fromPixelsAndRecycle(in,d);
    }

    private Bitmap pastelBitmap(Bitmap in) {
        int[]p=pixels(in);for(int i=0;i<p.length;i++){int c=p[i],r=(c>>16)&255,g=(c>>8)&255,b=c&255;r=clamp((int)(quant(r,7)*.68+82));g=clamp((int)(quant(g,7)*.68+82));b=clamp((int)(quant(b,7)*.68+82));p[i]=0xff000000|(r<<16)|(g<<8)|b;}return fromPixelsAndRecycle(in,p);
    }

    private void fineSketch(Mat rgba, Mat out) {
        Mat g=new Mat(),lap=new Mat();try{gray(rgba,g);Imgproc.GaussianBlur(g,g,new Size(3,3),0);Imgproc.Laplacian(g,lap,CvType.CV_8U,3,1,0);Core.bitwise_not(lap,out);}finally{g.release();lap.release();}
    }

    private Bitmap sketchyBitmap(Bitmap in) {
        int w=in.getWidth(),h=in.getHeight();int[]s=pixels(in),d=new int[s.length];
        for(int y=2;y<h-2;y++)for(int x=2;x<w-2;x++){int i=y*w+x;int e=Math.abs(luma(s[i+2])-luma(s[i-2]))+Math.abs(luma(s[i+2*w])-luma(s[i-2*w]));int jitter=((x*19+y*23)&7);int o=e>28+jitter?20:250;d[i]=0xff000000|(o<<16)|(o<<8)|o;}return fromPixelsAndRecycle(in,d);
    }

    private void oil2(Mat rgba, Mat out) {
        Mat rgb=new Mat(),mean=new Mat(),g=new Mat(),edge=new Mat(),edge3=new Mat();try{Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);Imgproc.pyrMeanShiftFiltering(rgb,mean,28,55);posterizeMat(mean,mean,30);Imgproc.cvtColor(rgb,g,Imgproc.COLOR_RGB2GRAY);Imgproc.Canny(g,edge,65,135);Core.bitwise_not(edge,edge);Imgproc.cvtColor(edge,edge3,Imgproc.COLOR_GRAY2RGB);Core.bitwise_and(mean,edge3,out);out.convertTo(out,-1,1.16,4);}finally{rgb.release();mean.release();g.release();edge.release();edge3.release();}
    }

    private void oily(Mat rgba, Mat out) {
        Mat rgb=new Mat(),med=new Mat(),blur=new Mat();try{Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB);Imgproc.medianBlur(rgb,med,9);Imgproc.GaussianBlur(med,blur,new Size(0,0),1.4);Core.addWeighted(med,1.65,blur,-0.65,4,out);posterizeMat(out,out,24);}finally{rgb.release();med.release();blur.release();}
    }

    private Bitmap oilFlowBitmap(Bitmap in) {
        int w=in.getWidth(),h=in.getHeight();int[]s=pixels(in),d=new int[s.length];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int wave=(int)(Math.sin(y*.075)*8+Math.sin((x+y)*.025)*5);int sx=Math.max(0,Math.min(w-1,x+wave));int c=s[y*w+sx];int r=quant((c>>16)&255,7),g=quant((c>>8)&255,7),b=quant(c&255,7);d[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;}return fromPixelsAndRecycle(in,d);
    }

    private Bitmap waterBitmap(Bitmap in) {
        int w=in.getWidth(),h=in.getHeight();int[]s=pixels(in),d=new int[s.length];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int sx=Math.max(0,Math.min(w-1,x+(int)(Math.sin(y*.045)*5)));int sy=Math.max(0,Math.min(h-1,y+(int)(Math.sin(x*.038)*4)));int c=s[sy*w+sx];int r=clamp((int)(((c>>16)&255)*.82+36)),g=clamp((int)(((c>>8)&255)*.92+32)),b=clamp((int)((c&255)*1.02+28));d[y*w+x]=0xff000000|(r<<16)|(g<<8)|b;}return fromPixelsAndRecycle(in,d);
    }

    // Sketch Camera comics shader character: black edges, stripe midtones, white highlights.
    private void sketchComics(Mat rgba, Mat out) {
        Mat g=new Mat(),edge=new Mat();try{gray(rgba,g);Imgproc.Canny(g,edge,70,145);out.create(g.rows(),g.cols(),CvType.CV_8UC1);byte[]gp=new byte[(int)g.total()],ep=new byte[(int)edge.total()],d=new byte[gp.length];g.get(0,0,gp);edge.get(0,0,ep);int w=g.cols();for(int i=0;i<d.length;i++){int y=i/w,x=i-y*w,v=gp[i]&255;if((ep[i]&255)>0||v<64)d[i]=0;else if(v<150)d[i]=(byte)(((x+y)%10<4)?120:220);else d[i]=(byte)255;}out.put(0,0,d);}finally{g.release();edge.release();}
    }

    private Bitmap warhol(Bitmap in) {
        int w=in.getWidth(),h=in.getHeight(),hw=Math.max(1,w/2),hh=Math.max(1,h/2);Bitmap small=Bitmap.createScaledBitmap(in,hw,hh,true);int[]s=pixels(small);Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);
        int[][] tint={{255,30,180},{30,180,255},{255,200,30},{60,255,120}};
        for(int q=0;q<4;q++){Bitmap tile=Bitmap.createBitmap(hw,hh,Bitmap.Config.ARGB_8888);int[]p=s.clone();for(int i=0;i<p.length;i++){int lum=luma(p[i]);int r=clamp((int)(tint[q][0]*(lum/255f))),g=clamp((int)(tint[q][1]*(lum/255f))),b=clamp((int)(tint[q][2]*(lum/255f)));if(lum<95){r=20;g=20;b=30;}p[i]=0xff000000|(r<<16)|(g<<8)|b;}tile.setPixels(p,0,hw,0,0,hw,hh);c.drawBitmap(tile,(q%2)*hw,(q/2)*hh,null);tile.recycle();}
        small.recycle();in.recycle();return out;
    }

    private Bitmap stainedGlass(Bitmap in) {
        int w=in.getWidth(),h=in.getHeight(),cell=14;Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);Paint p=new Paint();int[]src=pixels(in);
        for(int y=0;y<h;y+=cell)for(int x=0;x<w;x+=cell){int sx=Math.min(w-1,x+cell/2),sy=Math.min(h-1,y+cell/2),col=src[sy*w+sx];p.setColor(col);c.drawRect(x,y,Math.min(w,x+cell),Math.min(h,y+cell),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(Color.BLACK);c.drawRect(x,y,Math.min(w,x+cell),Math.min(h,y+cell),p);p.setStyle(Paint.Style.FILL);}in.recycle();return out;
    }

    private Bitmap colorDots(Bitmap in) {
        int w=in.getWidth(),h=in.getHeight(),step=10;Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);c.drawColor(Color.WHITE);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);int[]s=pixels(in);
        for(int y=step/2;y<h;y+=step)for(int x=step/2;x<w;x+=step){int col=s[y*w+x],v=luma(col);float rad=1f+(255-v)/255f*(step*.48f);int r=(col>>16)&255,g=(col>>8)&255,b=col&255;p.setColor(Color.rgb(r/2,g/2,Math.min(255,b+60)));c.drawCircle(x,y,rad,p);}in.recycle();return out;
    }

    private Bitmap frosted(Bitmap in) {
        int[]p=pixels(in);int w=in.getWidth(),h=in.getHeight();int[]q=p.clone();for(int y=3;y<h-3;y++)for(int x=3;x<w-3;x++){int j=y*w+x;int ox=((x*31+y*17)&7)-3,oy=((x*13+y*29)&7)-3;q[j]=p[(y+oy)*w+(x+ox)];}return fromPixelsAndRecycle(in,q);
    }

    private Bitmap american(Bitmap in) {
        int w=in.getWidth(),h=in.getHeight();int[]s=pixels(in),d=new int[s.length];
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){int i=y*w+x,v=luma(s[i]),e=Math.abs(luma(s[i+1])-luma(s[i-1]))+Math.abs(luma(s[i+w])-luma(s[i-w]));int col;if(e>60)col=0xff000000;else if(v<72)col=0xff781414;else if(v<125)col=0xff141478;else col=0xfff8f8f2;d[i]=col;}return fromPixelsAndRecycle(in,d);
    }

    private Bitmap sigmoidBw(Bitmap in) {
        int[]p=pixels(in);for(int i=0;i<p.length;i++){double x=luma(p[i])/255.0;double s=1.0/(1.0+Math.exp(-10.0*(x-.5)));int v=clamp((int)Math.round(s*255));p[i]=0xff000000|(v<<16)|(v<<8)|v;}return fromPixelsAndRecycle(in,p);
    }

    private void posterizeMat(Mat src, Mat dst, int step) {
        Mat tmp=src.clone();byte[]data=new byte[(int)(tmp.total()*tmp.channels())];tmp.get(0,0,data);int s=Math.max(8,step);for(int i=0;i<data.length;i++){int v=data[i]&255;data[i]=(byte)Math.min(255,(v/s)*s+s/2);}tmp.put(0,0,data);tmp.copyTo(dst);tmp.release();
    }

    private int[] pixels(Bitmap b){int[]p=new int[b.getWidth()*b.getHeight()];b.getPixels(p,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());return p;}
    private Bitmap fromPixelsAndRecycle(Bitmap in,int[]p){Bitmap out=Bitmap.createBitmap(in.getWidth(),in.getHeight(),Bitmap.Config.ARGB_8888);out.setPixels(p,0,in.getWidth(),0,0,in.getWidth(),in.getHeight());in.recycle();return out;}
    private int luma(int c){return (((c>>16)&255)*54+((c>>8)&255)*183+(c&255)*19)>>8;}
    private int clamp(int v){return Math.max(0,Math.min(255,v));}
    private int quant(int v,int levels){int n=Math.max(2,levels);int step=255/(n-1);return clamp(Math.round(v/(float)step)*step);}
}
