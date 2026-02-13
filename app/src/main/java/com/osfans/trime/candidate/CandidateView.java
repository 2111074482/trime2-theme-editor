/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.candidate;

import static android.widget.LinearLayout.HORIZONTAL;
import static com.osfans.trime.theme.ThemeManager.dp2px;

import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.osfans.trime.TrimeService;
import com.osfans.trime.core.Rime;
import com.osfans.trime.keyboard.KeyView;
import com.osfans.trime.theme.Style;
import com.osfans.trime.theme.ThemeManager;

import java.util.ArrayList;

public class CandidateView extends LinearLayout implements View.OnClickListener{

    private final TrimeService mTrime;
    private final Style mCandidateStyle;
    private RecyclerView mListView;
    private KeyView mHide;
    private CandidateAdapter mAdapter;
    private ToolbarView mToolbarView;
    private LinearLayout root;

    public CandidateView(@NonNull Context context) {
        super(context);
        mTrime=TrimeService.getInstance();
        mCandidateStyle=ThemeManager.getStyle().getStyle("candidate");
        setClipChildren(false);
        setClipToPadding(false);
        initView();
    }

    private void initView() {
        root = new LinearLayout(getContext());
        root.setOrientation(HORIZONTAL);
        root.setBackground(mCandidateStyle.getBackground(0xffdddddd));
        int elevation = mCandidateStyle.getSize("elevation", 2);
        root.setElevation(elevation);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int dShadowColor = mCandidateStyle.getColor("shadow_color", 0);
            if (dShadowColor != 0) {
                root.setOutlineAmbientShadowColor(dShadowColor);
                root.setOutlineSpotShadowColor(dShadowColor);
            }
        }
        // 设置 CandidateView 自身的高度，防止输入法界面闪烁
        int height = mCandidateStyle.getHeight(48)-elevation;
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(0,0,0,elevation);
        addView(root,lp);
        mListView=new RecyclerView(getContext());
        mListView.setClipChildren(false);
        mListView.setClipToPadding(false);
        // 性能优化：横向候选栏通常变动频繁，关闭动画更流畅
        mListView.setItemAnimator(null);
        // 2. 设置布局管理器 (核心步骤：指定为 HORIZONTAL)
        LinearLayoutManager layoutManager = new LinearLayoutManager(
                getContext(),
                LinearLayoutManager.HORIZONTAL, // 横向布局
                false
        );
        mListView.setLayoutManager(layoutManager);
        // 必须设为 false，否则 RecyclerView 会跳过尺寸计算
        mListView.setHasFixedSize(false);
        mHide=new KeyView(getContext(),mCandidateStyle.getKeyStyle("key",ThemeManager.getStyle().getKeyStyle("key")));
        mHide.setText("▽");
        mHide.setContentDescription("更多候选");
        mHide.setOnClickListener(this);
        root.addView(mListView,new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,height,1));
        root.addView(mHide,new LayoutParams(height,height));
        mListView.setAdapter(mAdapter=new CandidateAdapter(new ArrayList<>()));
    }


    @Override
    public void onClick(View v) {
        if(Rime.isComposing())
            mTrime.showExtractedCandidatesView(true);
        else
            mTrime.requestHideSelf(0);
    }


    public void update() {
        if (mListView.isComputingLayout()) {
            mListView.post(this::update);
            return;
        }
        CandidatesManager.reset();
        mAdapter.setData(CandidatesManager.next());
        mListView.scrollToPosition(0);
        //mListView.invalidateItemDecorations();
    }

    public void show(){
        if (mListView.isComputingLayout()) {
            mListView.post(this::update);
            return;
        }
        CandidatesManager.reset();
        CandidatesManager.resetFilter();
        mAdapter.setData(CandidatesManager.next());
        // 第一种方式：直接调用 RecyclerView 的方法
        mListView.scrollToPosition(0);
        //mListView.invalidateItemDecorations();
        mListView.requestLayout();
    }

    public void showToolbarView(boolean b) {
        if(mToolbarView==null) {
            mToolbarView = new ToolbarView(getContext());
            addView(mToolbarView,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        mToolbarView.setVisibility(b ? View.VISIBLE : View.GONE);
        root.setVisibility(!b ? View.VISIBLE : View.GONE);
    }

    public void invalidateAllKeys() {
        if(mToolbarView!=null) {
            mToolbarView.invalidateAllKeys();
        }
    }
}
