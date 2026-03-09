/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.osfans.trime.core.Rime;
import com.osfans.trime.keyboard.AbsKeyboardView;
import com.osfans.trime.keyboard.FlexboxKeyboardView;
import com.osfans.trime.keyboard.KeyboardView;
import com.osfans.trime.keyboard.ModifierState;
import com.osfans.trime.keyboard.RowKeyboardView;
import com.osfans.trime.keyboard.SymbolsKeyboardView;
import com.osfans.trime.theme.ThemeManager;

import androidx.annotation.NonNull;

import org.luaj.Globals;
import org.luaj.LuaValue;
import org.luaj.lib.ResourceFinder;
import org.luaj.lib.jse.JsePlatform;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class InputView extends FrameLayout implements ResourceFinder {

    private View mKeyboardView;
    private Globals globals;

    public InputView(@NonNull Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        setKeyboard(Rime.getRimeStatus().getSchemaId());
    }

    private void setKeyboardView(KeyboardView keyboardView) {
        if (keyboardView.equals(mKeyboardView))
            return;
        View oldView = mKeyboardView;
        mKeyboardView = keyboardView;
        ViewParent parent = keyboardView.getParent();
        if (parent != null && parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(keyboardView);
        }
        Rime.setRimeOption("ascii_mode", keyboardView.isAsciiMode());
        setShifted(false);
        addView(keyboardView, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (oldView != null) {
            // 旧键盘淡出
            oldView.animate()
                    .alpha(0f)
                    .setDuration(100)
                    .withLayer() // 动画期间使用硬件层加速
                    .withEndAction(() -> {
                        removeView(oldView);
                        oldView.setAlpha(1.0f); // 恢复状态以备下次复用
                    })
                    .start();
        }
    }

    private void setKeyboardView(View keyboardView) {
        View oldView = mKeyboardView;
        mKeyboardView = keyboardView;
        ViewParent parent = keyboardView.getParent();
        if (parent != null && parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(keyboardView);
        }
        addView(keyboardView, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (oldView != null) {
            // 旧键盘淡出
            oldView.animate()
                    .alpha(0f)
                    .setDuration(100)
                    .withLayer() // 动画期间使用硬件层加速
                    .withEndAction(() -> {
                        removeView(oldView);
                        oldView.setAlpha(1.0f); // 恢复状态以备下次复用
                    })
                    .start();
        }
    }

    private String mCurrentSchemaId;

    // 2. 视图缓存：SchemaId -> KeyboardView 实例
    private final Map<String, KeyboardView> mViewCache = new HashMap<>();

    public void setKeyboard(String id) {
        Log.w("TAG", "setKeyboard: " + id);
        if (id == null || id.equals(mCurrentSchemaId)) return;
        mCurrentSchemaId = id;
        if (id.isEmpty()) {
            String k = Config.getKeyboard(".default");
            if (TextUtils.isEmpty(k)) {
                id = ThemeManager.getKeyboard(".default");
                if (TextUtils.isEmpty(id))
                    id = "qwerty36";
            } else {
                id = k;
            }
        }

        // 获取或创建 KeyboardView
        KeyboardView targetView = mViewCache.get(id);
        if (targetView == null) {
            if (!new File(findFile(id + ".lua")).exists()) {
                if (id.equals(".default") || id.equals("default")) {
                    id = Config.getKeyboard(".default");
                    if(TextUtils.isEmpty(id)) {
                        id = Rime.getRimeStatus().getSchemaId();
                        id = ThemeManager.getKeyboard(id);
                    }
                } else {
                    id = ThemeManager.getKeyboard(id);
                }
                if (id.isEmpty()) {
                    id = "qwerty36";
                }
            }
            Log.w("TAG", "setKeyboard:4 " + id);
            // 初始化 Lua 环境
            Globals globals = JsePlatform.standardGlobals();
            globals.finder = this;
            LuaValue func = globals.loadfilex(id + ".lua");
            try {
                if (func.isfunction()) {
                    LuaValue ret = func.call();
                    if (ret.isuserdata(View.class)) {
                        setKeyboardView(ret.touserdata(View.class));
                        return;
                    }
                } else {
                    ThemeManager.sendMsg("setKeyboard " + func.tojstring());
                }
            } catch (Exception e) {
                ThemeManager.sendMsg("setKeyboard " + e);
            }
            //Log.w("TAG", "setKeyboard: "+globals.checktable().dump().tojstring() );
            // 创建并存入缓存
            if (globals.get("rows").istable()) {
                targetView = new RowKeyboardView(getContext(), globals);
            } else if (globals.get("flex_box").istable()) {
                targetView = new FlexboxKeyboardView(getContext(), globals);
            } else if (globals.get("keys").istable()) {
                targetView = new AbsKeyboardView(getContext(), globals);
            } else if (globals.get("key_maps").istable()) {
                TrimeService.getInstance().showCustomView(new SymbolsKeyboardView(getContext(), globals));
                return;
            } else {
                func = globals.loadfilex("themes/default/keyboards/qwerty36.lua");
                if (func.isfunction())
                    func.call();
                targetView = new RowKeyboardView(getContext(), globals);
            }
            mViewCache.put(id, targetView);
            if (targetView.isLock()) {
                mViewCache.put(".default", targetView);
            }
        }
        setKeyboardView(targetView);
    }

    @Override
    public InputStream findResource(String name) {
        if (TextUtils.isEmpty(name))
            return null;
        try {
            if (new File(name).exists())
                return new FileInputStream(name);
        } catch (Exception e) {
            if (BuildConfig.DEBUG)
                e.printStackTrace();
        }
        try {
            return new FileInputStream(new File(Config.getKeyboardDir(), name));
        } catch (Exception e) {
            if (BuildConfig.DEBUG)
                e.printStackTrace();
        }
        if (!name.endsWith(".lua"))
            return null;
        try {
            return getContext().getAssets().open("themes/default/keyboards/" + name);
        } catch (Exception e) {
            if (BuildConfig.DEBUG)
                e.printStackTrace();
        }
        try {
            //name = name.replace(".lua", "");
            //if (!name.startsWith("themes/default/") && !TextUtils.isEmpty(name)) {
            //    //Toast.makeText(getContext(), "未找到 " + name, Toast.LENGTH_SHORT).show();
            //    return null;
            //}
            return getContext().getAssets().open(name);
        } catch (Exception e) {
            if (BuildConfig.DEBUG)
                e.printStackTrace();
        }
        return null;
    }

    @Override
    public String findFile(String filename) {
        if (TextUtils.isEmpty(filename))
            return null;
        if (filename.startsWith("/"))
            return filename;
        return new File(Config.getKeyboardDir(), filename).getAbsolutePath();
    }

    public void invalidateComposingKeys() {
        if (mKeyboardView instanceof KeyboardView)
            ((KeyboardView) mKeyboardView).invalidateComposingKeys();
    }

    public void invalidateAllKeys() {
        //if (mKeyboardView != null)
        //    mKeyboardView.invalidateAllKeys();
        for (KeyboardView value : mViewCache.values()) {
            value.invalidateAllKeys();
        }
    }

    public boolean isShifted() {
        return ModifierState.isShifted();
    }

    public void setShifted(boolean shifted) {
        if (mKeyboardView instanceof KeyboardView)
            ((KeyboardView) mKeyboardView).setShifted(shifted);
    }

    public void setAsciiMode(boolean asciiMode) {
        if (mKeyboardView instanceof KeyboardView)
            ((KeyboardView) mKeyboardView).setAsciiMode(asciiMode);
    }
}
