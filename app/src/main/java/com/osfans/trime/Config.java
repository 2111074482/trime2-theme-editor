/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.util.Log;
import android.util.TypedValue;

import com.androlua.LuaApplication;
import com.osfans.trime.util.Function;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Map;

public class Config {

    private static String mGroup = null;
    private static String mTheme = null;
    private static String mStyle = null;


    public static boolean isSpeakKeyLabel() {
        return false;
    }

    public static boolean isKeyboardFloat() {
        return false;
    }

    public static String getDataDir() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "rime");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    public static String getGroup() {
        if (mGroup == null)
            mGroup = Function.loadString(LuaApplication.getInstance(), "select_schema_group", "default");
        return mGroup;
    }

    public static String getUserDataDir() {
        File dir = new File(getDataDir(), "schemas/" + getGroup());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    public static String getScriptsDir() {
        File dir = new File(getDataDir(), "scripts");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    public static String getScriptsDir(String name) {
        return new File(getScriptsDir(), name).getAbsolutePath();
    }

    public static String[] getGroups() {
        File dir = new File(getDataDir(), "schemas");
        String[] list = dir.list();
        if(list==null)
            list=new String[0];
        return list;
    }

    public static void setGroup(String s) {
        mGroup = s;
        Function.saveString(LuaApplication.getInstance(), "select_schema_group", s);
    }

    public static String getTheme() {
        if (mTheme == null)
            mTheme = Function.loadString(LuaApplication.getInstance(), "theme", "default");
        return mTheme;
    }

    public static void setTheme(String s) {
        mTheme = s;
        Function.saveString(LuaApplication.getInstance(), "theme", s);
    }

    public static String getThemeDir() {
        File dir = new File(getDataDir(), "themes");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    public static String getThemeDir(String s) {
        File dir = new File(getThemeDir(), s);
        return dir.getAbsolutePath();
    }

    public static String getKeyboardDir() {
        File dir = new File(getThemeDir(getTheme()), "keyboards");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    public static String[] getStyles() {
        File dir = new File(getStyleDir());
        String[] list = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return new File(dir,name+"/main.lua").exists();
            }
        });
        if(list==null)
            list=new String[0];
        return list;
    }

    public static void setStyle(String s) {
        mStyle = s;
        Function.saveString(LuaApplication.getInstance(), getTheme() + "_style", s);
    }

    public static String getStyle() {
        if (mStyle == null)
            mStyle = Function.loadString(LuaApplication.getInstance(), getTheme() + "_style", "light");
        return mStyle;
    }

    public static String getStyleDir() {
        File dir = new File(getThemeDir(getTheme()), "styles");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    public static String getStyleDir(String s) {
        File dir = new File(getStyleDir(), s);
        return dir.getAbsolutePath();
    }

    public static String getStylePath(String d,String s) {
        File dir = new File(getStyleDir(d), s);
        return dir.getAbsolutePath();
    }

    public static String getStylePath(String s) {
        File dir = new File(getStyleDir(getStyle()), s);
        return dir.getAbsolutePath();
    }

    public static int getDialogTheme() {
        return android.R.style.Theme_DeviceDefault_Dialog;
    }

    public static String[] getThemes() {
        File dir = new File(getThemeDir());
        String[] list = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return new File(dir,name+"/main.lua").exists();
            }
        });
        if(list==null)
            list=new String[0];
        return list;
    }

    public static String getImagePath(String s) {
        return new File(getThemeDir(getTheme()),"images/"+s).getAbsolutePath();
    }

    public static String getSoundPath(String s) {
        return new File(getThemeDir(getTheme()),"sounds/"+s).getAbsolutePath();
    }

}
