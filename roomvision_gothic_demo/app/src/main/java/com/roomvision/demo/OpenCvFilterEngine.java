package com.roomvision.demo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;

/** Lightweight live-art engine. No OpenCV/TFLite/native runtime. */
final class OpenCvFilterEngine {
    private static final int MAX_SIDE = 640;

    Bitmap process(Bitmap source, FilterType type) {
        Bitmap input = scaleForRealtime(source);
        switch (type) {
            case CRAYON: return crayon(input);
            case BLUE_PEN: return bluePen(input);
            case ASCII: return ascii(input);
            case GOTHIC: return gothic(input);
            default: return input;
        }
    }

    private Bitmap scaleForRealtime(Bitmap source) {
        int w = source.getWidth(), h = source.getHeight();
        int max = Math.max(w, h);
        if (max <= MAX_SIDE) return source.copy(Bitmap.Config.ARGB_8888, false);
        float s = MAX_SIDE / (float) max;
        return Bitmap.createScaledBitmap(source, Math.max(2, Math.round(w*s)), Math.max(2, Math.round(h*s)), true);
    }

    /** Wax crayon: broad saturated blocks, heavy irregular dark contour and stable paper grain. */
    private Bitmap crayon(Bitmap in) {
        int w=in.getWidth(), h=in.getHeight();
        int[] p=pixels(in), lum=new int[p.length], out=new int[p.length];
        for(int i=0;i<p.length;i++) lum[i]=luma(p[i]);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            int i=y*w+x,c=p[i];
            int r=quant((c>>16)&255,48), g=quant((c>>8)&255,48), b=quant(c&255,48);
            int max=Math.max(r,Math.max(g,b)), min=Math.min(r,Math.min(g,b));
            if(max-min>15){r=clamp((int)((r-128)*1.30+140));g=clamp((int)((g-128)*1.30+140));b=clamp((int)((b-128)*1.30+140));}
            int grain=hashNoise(x,y)-128;
            r=clamp(r+grain/8+7);g=clamp(g+grain/10+6);b=clamp(b+grain/9+5);
            if(x>0&&x<w-1&&y>0&&y<h-1){int edge=Math.abs(lum[i+1]-lum[i-1])+Math.abs(lum[i+w]-lum[i-w]);if(edge>52){float k=Math.max(.18f,.72f-edge/420f);r=(int)(r*k);g=(int)(g*k);b=(int)(b*k);}}
            out[i]=0xff000000|(r<<16)|(g<<8)|b;
        }
        Bitmap result=fromPixelsAndRecycle(in,out);
        Canvas c=new Canvas(result); Paint wax=new Paint(Paint.ANTI_ALIAS_FLAG); wax.setStrokeWidth(1.15f);
        for(int y=5;y<h;y+=8) for(int x=4;x<w;x+=11){int v=lum[Math.min(lum.length-1,y*w+x)]; if(v<205){int a=18+(205-v)/3; wax.setColor(Color.argb(Math.min(85,a),35,30,55)); c.drawLine(x-5,y+2,x+7,y-2,wax);}}
        return result;
    }

    /** Blue Pen: white paper + blue ballpoint contours and hand-drawn cross-hatching in shadows. */
    private Bitmap bluePen(Bitmap in) {
        int w=in.getWidth(),h=in.getHeight(); int[] src=pixels(in),lum=new int[src.length],dst=new int[src.length];
        for(int i=0;i<src.length;i++) lum[i]=luma(src[i]);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            int i=y*w+x, edge=0;
            if(x>0&&x<w-1&&y>0&&y<h-1){int gx=Math.abs(lum[i+1]-lum[i-1]),gy=Math.abs(lum[i+w]-lum[i-w]);edge=Math.min(255,(gx+gy)*3);}
            int shade=255-lum[i];
            int paper=247+(hashNoise(x,y)%7)-3;
            int ink=Math.min(235, edge + Math.max(0,shade-70)/3);
            int r=clamp(paper-ink*78/255), g=clamp(paper-ink*48/255), b=clamp(paper-ink*4/255);
            if(edge>95){r=18;g=61;b=176;}
            dst[i]=0xff000000|(r<<16)|(g<<8)|b;
        }
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); out.setPixels(dst,0,w,0,0,w,h); in.recycle();
        Canvas canvas=new Canvas(out); Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG); pen.setStrokeWidth(Math.max(.8f,w/800f)); pen.setColor(Color.rgb(24,72,190));
        int cell=Math.max(7,w/82);
        for(int y=cell;y<h-cell;y+=cell) for(int x=cell;x<w-cell;x+=cell){int v=lum[y*w+x]; if(v<165){int a=42+(165-v);pen.setAlpha(Math.min(150,a));canvas.drawLine(x-cell*.45f,y+cell*.42f,x+cell*.48f,y-cell*.40f,pen);} if(v<100){pen.setAlpha(80);canvas.drawLine(x-cell*.45f,y-cell*.38f,x+cell*.48f,y+cell*.42f,pen);}}
        return out;
    }

    /** Full-frame terminal/ASCII world. */
    private Bitmap ascii(Bitmap in) {
        int w=in.getWidth(),h=in.getHeight(); Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(out);canvas.drawColor(Color.rgb(3,8,5));
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setTypeface(Typeface.MONOSPACE);paint.setTextSize(Math.max(9,w/58f));paint.setColor(Color.rgb(104,255,154));
        String chars="@%#*+=-:. ";int sx=Math.max(8,w/64),sy=Math.max(10,(int)(sx*1.35f));int[] p=pixels(in);
        for(int y=sy;y<h;y+=sy)for(int x=0;x<w;x+=sx){int v=luma(p[Math.min(p.length-1,y*w+x)]);int idx=Math.min(chars.length()-1,(255-v)*(chars.length()-1)/255);paint.setAlpha(Math.min(255,105+v/2));canvas.drawText(String.valueOf(chars.charAt(idx)),x,y,paint);}in.recycle();return out;
    }

    /** Gothic world: cold carved stone, mortar, cracks, vignette, drifting mist and torch glow. */
    private Bitmap gothic(Bitmap in) {
        int w=in.getWidth(),h=in.getHeight(); int[] src=pixels(in),lum=new int[src.length],dst=new int[src.length];
        for(int i=0;i<src.length;i++) lum[i]=luma(src[i]);
        int bw=Math.max(64,w/6), bh=Math.max(48,h/9), mortar=Math.max(2,w/260);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            int i=y*w+x,c=src[i],v=lum[i]; int n=(hashNoise(x/3,y/3)-128)/7;
            int r=clamp((int)(v*.47)+n-12),g=clamp((int)(v*.52)+n-7),b=clamp((int)(v*.55)+n-3);
            int row=y/bh; int xx=x+(row%2)*(bw/2); boolean seam=(xx%bw<mortar)||(y%bh<mortar);
            if(seam){r=(int)(r*.34);g=(int)(g*.36);b=(int)(b*.37);} else if((xx%bw)<mortar*3||(y%bh)<mortar*3){r=clamp(r+13);g=clamp(g+12);b=clamp(b+10);}
            if(x>0&&x<w-1&&y>0&&y<h-1){int e=Math.abs(lum[i+1]-lum[i-1])+Math.abs(lum[i+w]-lum[i-w]);if(e>75){r=clamp(r-e/11);g=clamp(g-e/13);b=clamp(b-e/15);}}
            float nx=(x-w*.5f)/(w*.5f), ny=(y-h*.5f)/(h*.5f);float vig=Math.min(.48f,(nx*nx+ny*ny)*.30f);r=(int)(r*(1-vig));g=(int)(g*(1-vig));b=(int)(b*(1-vig));
            dst[i]=0xff000000|(r<<16)|(g<<8)|b;
        }
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);out.setPixels(dst,0,w,0,0,w,h);in.recycle();Canvas canvas=new Canvas(out);
        Paint crack=new Paint(Paint.ANTI_ALIAS_FLAG);crack.setColor(Color.argb(145,12,13,14));crack.setStrokeWidth(Math.max(1,w/420f));
        for(int k=0;k<18;k++){int x=(k*97+w/7)%w,y=(k*149+h/9)%h;for(int s=0;s<5;s++){int nx=clampCoord(x+((hashNoise(k,s)%49)-24),w),ny=clampCoord(y+18+(hashNoise(s,k)%55),h);canvas.drawLine(x,y,nx,ny,crack);x=nx;y=ny;}}
        long t=SystemClock.uptimeMillis()/40;Paint fog=new Paint(Paint.ANTI_ALIAS_FLAG);fog.setColor(Color.argb(18,185,198,205));for(int j=0;j<7;j++){float cx=((j*137+t*(j+2))%(w+240))-120,cy=h*(.60f+j*.055f);canvas.drawOval(cx-150,cy-34,cx+150,cy+34,fog);}
        Paint torch=new Paint(Paint.ANTI_ALIAS_FLAG);for(int side=0;side<2;side++){float cx=side==0?w*.12f:w*.88f,cy=h*.58f;for(int rad=100;rad>18;rad-=18){torch.setColor(Color.argb(Math.max(5,32-rad/4),255,132,48));canvas.drawCircle(cx,cy,rad,torch);}}
        return out;
    }

    private int[] pixels(Bitmap b){int[]p=new int[b.getWidth()*b.getHeight()];b.getPixels(p,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());return p;}
    private Bitmap fromPixelsAndRecycle(Bitmap in,int[]p){Bitmap out=Bitmap.createBitmap(in.getWidth(),in.getHeight(),Bitmap.Config.ARGB_8888);out.setPixels(p,0,in.getWidth(),0,0,in.getWidth(),in.getHeight());in.recycle();return out;}
    private int luma(int c){return (((c>>16)&255)*54+((c>>8)&255)*183+(c&255)*19)>>8;}
    private int quant(int v,int step){return clamp((v/step)*step+step/2);}
    private int hashNoise(int x,int y){int n=x*374761393+y*668265263;n=(n^(n>>>13))*1274126177;return(n^(n>>>16))&255;}
    private int clampCoord(int v,int max){return Math.max(0,Math.min(max-1,v));}
    private int clamp(int v){return Math.max(0,Math.min(255,v));}
}
