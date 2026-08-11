package com.bmw1975487.aione.diag;
import android.content.Context; import android.util.Log; import java.io.*;
public final class AppLog { private AppLog(){} public static void i(Context c,String e,String d){Log.i("AI-ACCESS",e+" "+d); try(FileWriter w=new FileWriter(new File(c.getFilesDir(),"aione.log"),true)){w.write(e+" "+d+"\n");}catch(Throwable x){}} public static void e(Context c,String e,String d,Throwable t){Log.e("AI-ACCESS",e+" "+d,t);} }
