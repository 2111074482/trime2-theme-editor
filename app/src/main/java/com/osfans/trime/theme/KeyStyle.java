/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.theme;

import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.VibrationEffect;
import android.text.TextUtils;
import android.view.Gravity;

import com.osfans.trime.Config;

import org.luaj.LuaValue;

import java.io.File;
import java.util.Map;

/**
 * 缓存优化的 KeyStyle
 * 针对高频访问的属性（如动画参数、颜色）进行了字段级缓存，避免重复查询 LuaTable。
 */
public class KeyStyle extends Style {
    // 默认常量
    private static final int DEFAULT_TEXT_COLOR = 0xff000000;
    private static final int DEFAULT_TEXT_SIZE = 18;
    private static final int DEFAULT_BG_COLOR = 0xffffffff;

    // --- 缓存字段 ---
    // 使用基本类型避免装箱开销，使用 -1 或特定初值标记未缓存状态
    private float mTextSize = -1;
    private int mTextColor = 0;
    private boolean mHasCachedTextColor = false;

    private int mBackgroundColor = 0;
    private boolean mHasCachedBgColor = false;

    private float mScaleX = Float.NaN; // 使用 NaN 标记未缓存
    private float mScaleY = Float.NaN;
    private float mTranslationX = Float.MIN_VALUE; // 使用极小值标记未缓存
    private float mTranslationY = Float.MIN_VALUE;
    private float mTranslationZ = Float.MIN_VALUE;

    private int mElevation = -1;
    private int mShadowColor = 0;
    private boolean mHasCachedShadowColor = false;

    // 样式引用缓存
    private KeyStyle mHintStyle, mLongClickStyle, mPressedStyle;
    private int mGravity;
    private VibrationEffect mVibrationEffect;
    private boolean mHasCachedVibrationEffect;
    private boolean mHasCachedVibrationEnabled;
    private boolean mVibrationEnabled;
    private int mSoundEffect=-1;
    private boolean mHasCachedSoundEffect;
    private boolean mHasCachedSoundEnabled;
    private boolean mSoundEnabled;
    private long mLongClickTime;
    private long mRepeatClickTime;

    public KeyStyle(LuaValue t) {
        super(t);
        getSoundEffect();
    }

    public KeyStyle(LuaValue t, Style def) {
        this(t);
        setStyle(def);
    }

    // --- 核心属性获取（带缓存逻辑） ---

    public float getTextSize() {
        if (mTextSize < 0) {
            mTextSize = getTextSize(DEFAULT_TEXT_SIZE);
        }
        return mTextSize;
    }

    public int getTextColor() {
        if (!mHasCachedTextColor) {
            mTextColor = getTextColor(DEFAULT_TEXT_COLOR);
            mHasCachedTextColor = true;
        }
        return mTextColor;
    }

    public int getBackgroundColor() {
        if (!mHasCachedBgColor) {
            mBackgroundColor = getColor("background", DEFAULT_BG_COLOR);
            mHasCachedBgColor = true;
        }
        return mBackgroundColor;
    }

    public Drawable getBackground() {
        // Drawable 涉及对象创建，由父类实现或按需获取，此处通常不适合在 Style 中长期常驻缓存
        return getBackground(DEFAULT_BG_COLOR);
    }

    // --- 动画相关属性（极高频调用） ---

    public float getScaleX() {
        if (Float.isNaN(mScaleX)) {
            mScaleX = getFloat("scale_x", 1.0f);
        }
        return mScaleX;
    }

    public float getScaleY() {
        if (Float.isNaN(mScaleY)) {
            mScaleY = getFloat("scale_y", 1.0f);
        }
        return mScaleY;
    }

    public float getTranslationX() {
        if (mTranslationX == Float.MIN_VALUE) {
            mTranslationX = getSize("translation_x", 0);
        }
        return mTranslationX;
    }

    public float getTranslationY() {
        if (mTranslationY == Float.MIN_VALUE) {
            mTranslationY = getSize("translation_y", 0);
        }
        return mTranslationY;
    }

    public float getTranslationZ() {
        if (mTranslationZ == Float.MIN_VALUE) {
            mTranslationZ = getSize("translation_z", 0);
        }
        return mTranslationZ;
    }

    // --- 其他样式属性 ---

    public int getElevation() {
        if (mElevation < 0) {
            mElevation = getSize("elevation", 0);
        }
        return mElevation;
    }

    public int getShadowColor() {
        if (!mHasCachedShadowColor) {
            mShadowColor = getColor("shadow_color", 0);
            mHasCachedShadowColor = true;
        }
        return mShadowColor;
    }

    public int getGravity() {
        if (mGravity == -1) {
            mGravity = getGravity(Gravity.CENTER);
        }
        return mGravity;
    }



    // --- 嵌套样式引用缓存 ---

    public KeyStyle getHintKeyStyle() {
        if (mHintStyle == null) mHintStyle = getKeyStyle("hint", this);
        return mHintStyle;
    }

    public KeyStyle getLongClickKeyStyle() {
        if (mLongClickStyle == null) mLongClickStyle = getKeyStyle("long_click", this);
        return mLongClickStyle;
    }

    public KeyStyle getPressedStyle() {
        if (mPressedStyle == null) mPressedStyle = getKeyStyle("pressed", this);
        return mPressedStyle;
    }

    public VibrationEffect getVibrationEffect() {
        if (!mHasCachedVibrationEffect) {
            mHasCachedVibrationEffect = true;
            LuaValue ve = get("vibration_effect");

            if (ve.istable()) {
                LuaValue vt = ve.get(1); // 时间轴
                LuaValue va = ve.get(2); // 强度轴

                // 关键：取两者长度的最小值，防止 Lua 配置不一致导致崩溃
                int len = Math.min(vt.length(), va.length());

                if (len > 0) {
                    long[] timings = new long[len];
                    int[] amplitudes = new int[len];
                    for (int i = 0; i < len; i++) {
                        timings[i] = vt.get(i + 1).optlong(0);
                        // 强制约束振幅在 0-255，防止 Lua 填错导致非法参数异常
                        int amp = va.get(i + 1).optint(0);
                        amplitudes[i] = Math.max(0, Math.min(255, amp));
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            mVibrationEffect = VibrationEffect.createWaveform(timings, amplitudes, -1);
                        } catch (Exception e) {
                            // 最后的兜底，防止意外的非法参数
                            mVibrationEffect = null;
                        }
                    }
                }
            }
        }
        return mVibrationEffect;
    }

    public boolean isVibrationEnabled(){
        if(!mHasCachedVibrationEnabled) {
            mHasCachedVibrationEnabled = true;
            mVibrationEnabled = get("vibration_enabled").toboolean();
        }
        return mVibrationEnabled;
    }

    public int getSoundEffect() {
        if (!mHasCachedSoundEffect) {
            mHasCachedSoundEffect = true;
            LuaValue ve = get("sound_effect");
            if (ve.isstring()) {
                String s = ve.tojstring();
                String path = Config.getStylePath(s);
                if(new File(path).exists()){
                    mSoundEffect = ThemeManager.loadSound(path);
                } else {
                    path = Config.getSoundPath(s);
                    if(new File(path).exists())
                        mSoundEffect = ThemeManager.loadSound(path);
                }
            }
        }
        return mSoundEffect;
    }

    public boolean isSoundEnabled(){
        if(!mHasCachedSoundEnabled) {
            mHasCachedSoundEnabled = true;
            mSoundEnabled = get("sound_enabled").toboolean();
        }
        return mSoundEnabled;
    }

    public long getLongClickTime() {
        if(mLongClickTime==0)
            mLongClickTime=get("long_click_time").optlong(1000);
        return mLongClickTime;
    }

    public long getRepeatClickTime() {
        if(mRepeatClickTime==0)
            mRepeatClickTime=get("repeat_click_time").optlong(200);
        return mRepeatClickTime;
    }
}
