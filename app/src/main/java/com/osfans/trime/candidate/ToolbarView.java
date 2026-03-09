/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.candidate;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.osfans.trime.Event;
import com.osfans.trime.Key;
import com.osfans.trime.TrimeService;
import com.osfans.trime.core.Rime;
import com.osfans.trime.core.RimeSchema;
import com.osfans.trime.keyboard.KeyView;
import com.osfans.trime.theme.KeyStyle;
import com.osfans.trime.theme.Style;
import com.osfans.trime.theme.ThemeManager;

import org.luaj.LuaTable;
import org.luaj.LuaValue;

import java.util.ArrayList;
import java.util.List;

public class ToolbarView extends LinearLayout implements View.OnClickListener {
    private final TrimeService mTrime;
    private final Style mToolbarStyle;
    private final KeyStyle mKeyStyle;
    private ArrayList<KeyView> mKeys = new ArrayList<>();
    ;

    public ToolbarView(Context context) {
        super(context);
        mTrime = TrimeService.getInstance();
        mToolbarStyle = ThemeManager.getStyle().getStyle("toolbar");
        mKeyStyle = mToolbarStyle.getKeyStyle("key", ThemeManager.getStyle().getKeyStyle("key"));
        setClipChildren(false);
        setClipToPadding(false);
        initView();
    }

    private void initView() {
        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(HORIZONTAL);
        root.setBackground(mToolbarStyle.getBackground(0xffdddddd));
        int elevation = mToolbarStyle.getSize("elevation", 2);
        root.setElevation(elevation);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int dShadowColor = mToolbarStyle.getColor("shadow_color", 0);
            if (dShadowColor != 0) {
                root.setOutlineAmbientShadowColor(dShadowColor);
                root.setOutlineSpotShadowColor(dShadowColor);
            }
        }
        // 设置 CandidateView 自身的高度，防止输入法界面闪烁
        int height = ThemeManager.getCandidateHeight() - elevation;
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(0, 0, 0, elevation);
        addView(root, lp);

        HorizontalScrollView mListView = new HorizontalScrollView(getContext());
        mListView.setHorizontalScrollBarEnabled(false); // 禁止水平滚动条
        mListView.setVerticalScrollBarEnabled(false);
        LinearLayout itemsLayout = new LinearLayout(getContext());
        itemsLayout.setGravity(Gravity.CENTER);
        mListView.addView(itemsLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        KeyView mHide = new KeyView(getContext(), mToolbarStyle.getKeyStyle("key", ThemeManager.getStyle().getKeyStyle("key")));
        mHide.setText("▽");
        mHide.setContentDescription("收起键盘");
        mHide.setOnClickListener(this);

        root.addView(mListView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height, 1));
        root.addView(mHide, new LayoutParams(height, height));
        try {
            if(mToolbarStyle.get("schema_switches").optboolean(false)&&!Rime.getCurrentRimeSchema().equals(".default")) {
                RimeSchema currentRimeSchema = new RimeSchema(Rime.getCurrentRimeSchema());
                List<RimeSchema.Switch> switches = currentRimeSchema.getSwitches();
                for (RimeSchema.Switch aSwitch : switches) {
                    if (aSwitch.getStates().isEmpty())
                        continue;
                    KeyView key = new KeyView(getContext(), mKeyStyle) {
                        @Override
                        public void invalidateKey() {
                            super.invalidateKey();
                            setText(aSwitch.getState());
                        }
                    };
                    key.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            aSwitch.toggleOption();
                        }
                    });
                    key.setText(aSwitch.getState());
                    itemsLayout.addView(key, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    key.setMinimumWidth(height);
                    mKeys.add(key);
                    Rime.setRimeOption(aSwitch.getName(), aSwitch.getReset() != 0);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        LuaValue keys = mToolbarStyle.get("keys").opttable(new LuaTable());
        int len = keys.length();
        for (int i = 0; i < len; i++) {
            LuaValue s = keys.get(i + 1);
            if (s.istable()) {
                KeyView key = new KeyView(getContext(), s.get("click").isnil() ? new Key(new Event(s)) : new Key(s), mKeyStyle);
                itemsLayout.addView(key, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                key.setMinimumWidth(height);
                mKeys.add(key);
            } else if (s.isstring()) {
                KeyView key = new KeyView(getContext(), new Key(s.tojstring()), mKeyStyle);
                itemsLayout.addView(key, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                key.setMinimumWidth(height);
                mKeys.add(key);
            }
        }
    }

    @Override
    public void onClick(View v) {
        mTrime.requestHideSelf(0);
    }

    public void invalidateAllKeys() {
        for (KeyView key : mKeys) {
            key.invalidateKey();
        }
    }

    public void setSchema(String id) {
        removeAllViews();
        initView();
    }
}
