/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.theme;

import static com.osfans.trime.theme.ThemeManager.dp2px;

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;

import com.androlua.LuaBitmapDrawable;
import com.osfans.trime.Config;
import com.osfans.trime.util.Function;

import org.luaj.LuaTable;
import org.luaj.LuaValue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Style {

    private final LuaValue mTable;
    private final HashMap<String, Style> mStyleCache = new HashMap<>();
    private final HashMap<String, KeyStyle> mKeyStyleCache = new HashMap<>();

    public Style(LuaValue t) {
        mTable = t.isnil() ? new LuaTable() : t;
    }

    public Style(LuaValue t, Style def) {
        this(t);
        setStyle(def);
    }

    public void setMeta(LuaValue t) {
        LuaTable mt = new LuaTable();
        mt.set("__index", t);
        mTable.setmetatable(mt);
    }

    public void setStyle(Style t) {
        LuaTable mt = new LuaTable();
        mt.set("__index", t.getTable());
        mTable.setmetatable(mt);
    }

    protected LuaValue getTable() {
        return mTable;
    }

    public int getColor(String key) {
        return mTable.get(key).toint();
    }

    public int getColor(String key, int def) {
        return mTable.get(key).optint(def);
    }

    public Drawable getDrawable(String key) {
        LuaValue v = mTable.get(key);
        // 预先计算好通用的圆角
        float radiusPx = dp2px((float) mTable.get("corner_radius").optdouble(0f));
        if (v.isint()) {
            return createGradientDrawable(v.toint(),radiusPx,dp2px((float) mTable.get("stroke_width").optdouble(0)), mTable.get("stroke_color").optint(0));
        } else if (v.isstring()) {
            File f=new File(Config.getStylePath(v.tojstring()));
            if(f.exists())
                return new LuaBitmapDrawable(f.getAbsolutePath());
            f=new File(Config.getImagePath(v.tojstring()));
            if(f.exists())
                return new LuaBitmapDrawable(f.getAbsolutePath());
        }
        return null;
    }

    public Drawable getDrawable(String key, int def) {
        LuaValue v = mTable.get(key);
        // 预先计算好通用的圆角
        float radiusPx = dp2px((float) mTable.get("corner_radius").optdouble(0f));
        if (v.isint()) {
            return createGradientDrawable(v.toint(),radiusPx,dp2px((float) mTable.get("stroke_width").optdouble(0)), mTable.get("stroke_color").optint(0));
        } else if (v.isstring()) {
            File f=new File(Config.getStylePath(v.tojstring()));
            if(f.exists())
                return new LuaBitmapDrawable(f.getAbsolutePath());
            f=new File(Config.getImagePath(v.tojstring()));
            if(f.exists())
                return new LuaBitmapDrawable(f.getAbsolutePath());
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(v.optint(def));
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    // 提取公共方法
    private Drawable createGradientDrawable(int color, float radius, int strokeWidth, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radius);

        // 添加边框逻辑
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }

        return drawable;
    }

    public Style getStyle(String key) {
        Style style = mStyleCache.get(key);
        if (style == null) {
            style = new Style(mTable.get(key));
            mStyleCache.put(key, style);
        }
        return style;
    }
    public Style getStyle(int key) {
        Style style = mStyleCache.get(Integer.toString(key));
        if (style == null) {
            style = new Style(mTable.get(key+1));
            mStyleCache.put(Integer.toString(key), style);
        }
        return style;
    }
    public Style getStyle(String key, Style def) {
        if(TextUtils.isEmpty(key))
            return def;
        Style style = mStyleCache.get(key);
        if (style == null) {
            LuaValue v = mTable.get(key);
            if (!v.istable())
                return def;
            style = new Style(v, def);
            mStyleCache.put(key, style);
        }
        return style;
    }

    public KeyStyle getKeyStyle(String key){
        KeyStyle style = mKeyStyleCache.get(key);
        if (style == null) {
            LuaValue v = mTable.get(key);
            style = new KeyStyle(v);
            mKeyStyleCache.put(key, style);
        }
        return style;
    }

    public KeyStyle getKeyStyle(){
        String key="key";
        KeyStyle style = mKeyStyleCache.get(key);
        if (style == null) {
            LuaValue v = mTable.get(key);
            style = new KeyStyle(v);
            mKeyStyleCache.put(key, style);
        }
        return style;
    }

    public KeyStyle getKeyStyle(String key, KeyStyle def) {
        if(TextUtils.isEmpty(key))
            return def;
        KeyStyle style = mKeyStyleCache.get(key);
        if (style == null) {
            LuaValue v = mTable.get(key);
            if (!v.istable())
                return def;
            style = new KeyStyle(v, def);
            mKeyStyleCache.put(key, style);
        }
        return style;
    }


    public float getTextSize(int def) {
        return mTable.get("text_size").optint(def);
    }

    public int getTextColor(int def) {
        return mTable.get("text_color").optint(def);
    }

    public int getSize(String key, int def) {
        return dp2px(mTable.get(key).optint(def));
    }

    public Drawable getBackground(int def) {
        return getDrawable("background", def);
    }

    public int getBackgroundColor(int def) {
        return getColor("background", def);
    }

    public float getFloat(String key, float def) {
        return (float) mTable.get(key).optdouble(def);
    }

    public int getHeight(int def) {
        return getSize("height", def);
    }

    public boolean hasKey(String key) {
        return !mTable.rawget(key).isnil();
    }

    public int getGravity(int def) {
        return parse(getString("gravity", ""), def);
    }
    public String getString(String key) {
        return mTable.get(key).optjstring("");
    }
    public String getString(String key, String def) {
        return mTable.get(key).optjstring(def);
    }

    // 建立字符串到常量值的映射表
    private static final Map<String, Integer> GRAVITY_MAP = new HashMap<>();

    static {
        GRAVITY_MAP.put("top", Gravity.TOP);
        GRAVITY_MAP.put("bottom", Gravity.BOTTOM);
        GRAVITY_MAP.put("left", Gravity.LEFT);
        GRAVITY_MAP.put("right", Gravity.RIGHT);
        GRAVITY_MAP.put("center", Gravity.CENTER);
        GRAVITY_MAP.put("center_vertical", Gravity.CENTER_VERTICAL);
        GRAVITY_MAP.put("center_horizontal", Gravity.CENTER_HORIZONTAL);
        GRAVITY_MAP.put("start", Gravity.START);
        GRAVITY_MAP.put("end", Gravity.END);
        // 可以根据需要继续添加 fill, clip_vertical 等
    }

    public static int parse(String gravityStr, int defaultGravity) {
        if (TextUtils.isEmpty(gravityStr)) return defaultGravity;

        int result = 0;
        // 支持类似 "center|bottom" 的写法
        String[] parts = gravityStr.toLowerCase().split("\\|");

        for (String part : parts) {
            String key = part.trim();
            if (GRAVITY_MAP.containsKey(key)) {
                result |= GRAVITY_MAP.get(key); // 按位或运算
            }
        }

        return result == 0 ? defaultGravity : result;
    }

    public LuaValue get(String key) {
        return mTable.get(key);
    }

    public Typeface getFont(String key) {
        LuaValue n = get(key);
        if (n.isnil()) {
            n=ThemeManager.getStyle().getStyle("keyboard").get(key);
            if(n.isnil())
                return Typeface.DEFAULT;
        }
        if (n.istable()) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    ArrayList<FontFamily> lt = new ArrayList<>();
                    for (int i = 1; i <= n.length(); i++) {
                        try {
                            File f = new File(Config.getFontPath(n.get(i).optjstring("")));
                            if(f.exists())
                                lt.add(new FontFamily.Builder(new Font.Builder(f).build()).build());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (lt.isEmpty())
                        return Typeface.DEFAULT;
                    Typeface.CustomFallbackBuilder tf = new Typeface.CustomFallbackBuilder(lt.get(0));
                    for (int i = 1; i < lt.size(); i++) {
                        tf.addCustomFallback(lt.get(i));
                    }
                    return tf.build();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        String name = n.optjstring("");
        if (name != null) {
            try {
                File f = new File(Config.getFontPath(name));
                if (f.exists()) return Typeface.createFromFile(f);
            } catch (Exception e) {
                  e.printStackTrace();
            }
        }
        return Typeface.DEFAULT;
    }

    public int getInt(String s) {
        LuaValue i = get(s);
        if(i.isint())
            return i.toint();
        return 0;
    }
    public int getInt(String s,int def) {
        LuaValue i = get(s);
        if(i.isint())
            return i.toint();
        return def;
    }


    public boolean getBoolean(String s) {
        return get(s).optboolean(false);
    }
    public boolean getBoolean(String s,boolean def) {
        return get(s).optboolean(def);
    }

    public int getLength() {
        return mTable.length();
    }
}
