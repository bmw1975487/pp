package com.roomvision.demo;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.graphics.Shader;

import androidx.annotation.RequiresApi;

/** Android 13+ GPU Gothic shader. */
@RequiresApi(33)
final class AgslGothicRenderer {
    private static final String AGSL =
            "uniform shader camera;\n" +
            "uniform float2 resolution;\n" +
            "uniform float time;\n" +
            "float lum(half3 c){ return dot(float3(c), float3(0.2126,0.7152,0.0722)); }\n" +
            "float hash21(float2 p){ return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453); }\n" +
            "half4 main(float2 p){\n" +
            "  half4 src = camera.eval(p);\n" +
            "  float l = lum(src.rgb);\n" +
            "  float lx = lum(camera.eval(p+float2(1.6,0.0)).rgb);\n" +
            "  float ly = lum(camera.eval(p+float2(0.0,1.6)).rgb);\n" +
            "  float edge = clamp((abs(l-lx)+abs(l-ly))*5.2,0.0,1.0);\n" +
            "  float2 uv = p / resolution;\n" +
            "  float row = floor(p.y/26.0);\n" +
            "  float bx = p.x + mod(row,2.0)*29.0;\n" +
            "  float mortarX = 1.0-smoothstep(0.03,0.095,abs(fract(bx/58.0)-0.5));\n" +
            "  float mortarY = 1.0-smoothstep(0.035,0.11,abs(fract(p.y/26.0)-0.5));\n" +
            "  float mortar = max(mortarX,mortarY);\n" +
            "  float n = hash21(floor(float2(bx/58.0,p.y/26.0)));\n" +
            "  float stone = clamp(l*0.50 + n*0.16 + edge*0.50,0.0,1.0);\n" +
            "  float3 cold = float3(0.055,0.075,0.070) + stone*float3(0.31,0.34,0.31);\n" +
            "  cold *= 1.0 - mortar*0.50;\n" +
            "  float crackNoise = hash21(floor(p/9.0));\n" +
            "  float crack = step(0.965,crackNoise) * smoothstep(0.22,0.72,edge+0.15);\n" +
            "  cold *= 1.0-crack*0.72;\n" +
            "  float flicker = 0.82 + 0.18*sin(time*7.0) + 0.08*sin(time*17.0);\n" +
            "  float d1 = length((uv-float2(0.13,0.56))*float2(1.0,1.45));\n" +
            "  float d2 = length((uv-float2(0.87,0.50))*float2(1.0,1.45));\n" +
            "  float torch = (exp(-d1*8.5)+exp(-d2*8.5))*flicker;\n" +
            "  cold += torch*float3(0.60,0.19,0.035);\n" +
            "  float fogWave = sin(uv.x*11.0 + time*0.55) + sin(uv.x*5.0-time*0.38);\n" +
            "  float fog = smoothstep(0.42,1.0,uv.y) * (0.10 + 0.045*fogWave);\n" +
            "  cold = mix(cold,float3(0.23,0.27,0.25),clamp(fog,0.0,0.22));\n" +
            "  float vig = smoothstep(0.90,0.28,length((uv-0.5)*float2(0.92,1.08)));\n" +
            "  cold *= 0.38 + 0.78*vig;\n" +
            "  cold += edge*float3(0.055,0.075,0.062);\n" +
            "  return half4(half3(clamp(cold,0.0,1.0)),1.0);\n" +
            "}\n";

    Bitmap render(Bitmap input, long timeMs) {
        int w = input.getWidth();
        int h = input.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        RuntimeShader shader = new RuntimeShader(AGSL);
        BitmapShader camera = new BitmapShader(input, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        shader.setInputShader("camera", camera);
        shader.setFloatUniform("resolution", (float)w, (float)h);
        shader.setFloatUniform("time", timeMs / 1000f);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(shader);
        new Canvas(out).drawRect(0, 0, w, h, p);
        return out;
    }
}
