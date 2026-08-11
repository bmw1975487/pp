package com.bmw1975487.aione.core;
import android.content.Context;
public final class StateStore { private static final String F="aione_state"; private StateStore(){} public static void set(Context c,String s,String d){c.getSharedPreferences(F,0).edit().putString("state",s).putString("detail",d==null?"":d).apply();} public static String state(Context c){return c.getSharedPreferences(F,0).getString("state",AppConstants.STATE_OFF);} public static String detail(Context c){return c.getSharedPreferences(F,0).getString("detail","");} }
