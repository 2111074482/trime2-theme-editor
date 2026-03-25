/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.keyboard;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.osfans.trime.TrimeService;
import com.osfans.trime.keyboard.adapter.WaterfallAdapter;
import com.osfans.trime.theme.KeyStyle;
import com.osfans.trime.theme.Style;
import com.osfans.trime.theme.ThemeManager;

public class ClipboardKeyboardView extends LinearLayout {

    private final TrimeService mTrime;
    private final KeyStyle mKeyStyle;
    private final Style mClipboardStyle;
    private RecyclerView mListView;
    private WaterfallAdapter mAdapter;
    private KeyView mChar;
    private KeyView phraseTitle;
    private KeyView clipboardTitle;

    public ClipboardKeyboardView(@NonNull Context context) {
        super(context);
        mClipboardStyle = ThemeManager.getStyle().getStyle("clipboard");
        mKeyStyle=mClipboardStyle.getKeyStyle("key",ThemeManager.getStyle().getKeyStyle("key"));
        mTrime=TrimeService.getInstance();
        setBackground(mClipboardStyle.getBackground(0x00000000));
        initView();
        setClipChildren(false);
        setClipToPadding(false);
    }

    private void initView() {
        LinearLayout root = new LinearLayout(getContext());
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setOrientation(VERTICAL);

        LinearLayout buttons = new LinearLayout(getContext());
        buttons.setClipChildren(false);
        buttons.setClipToPadding(false);
        buttons.setOrientation(HORIZONTAL);

        clipboardTitle = new KeyView(getContext(),mKeyStyle);
        buttons.addView(clipboardTitle,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,1));
        clipboardTitle.setText("剪贴板");

        phraseTitle = new KeyView(getContext(),mKeyStyle);
        phraseTitle.setSelected(false);
        buttons.addView(phraseTitle,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,1));
        phraseTitle.setText("短语");

        clipboardTitle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                clipboardTitle.setSelected(true);
                phraseTitle.setSelected(false);
                mAdapter.setData(false);
                clipboardTitle.setPressed(true);
            }
        });

        phraseTitle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                clipboardTitle.setSelected(false);
                phraseTitle.setSelected(true);
                mAdapter.setData(true);
                phraseTitle.setPressed(true);
            }
        });
        root.addView(buttons,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mListView=new RecyclerView(getContext());
        FrameLayout fr = new FrameLayout(getContext());
        fr.addView(mListView,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(fr,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mListView.setClipChildren(false);
        mListView.setClipToPadding(false);
        // 参数说明：2 代表列数，VERTICAL 代表垂直滚动
        StaggeredGridLayoutManager layoutManager =
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);

       // 关键设置：防止 Item 切换位置导致闪烁（可选）
        layoutManager.setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        mListView.setLayoutManager(layoutManager);
        mListView.setItemViewCacheSize(40); // 增加缓存数量
        mListView.setItemAnimator(null);
        //mListView.setInitialPrefetchItemCount(8); // 提前预取

        LinearLayout mButtonBar = new LinearLayout(getContext());
        mButtonBar.setOrientation(VERTICAL);
        mButtonBar.setClipChildren(false);
        mButtonBar.setClipToPadding(false);

        addView(root,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ThemeManager.getContentHeight(),1));
        addView(mButtonBar,new LinearLayout.LayoutParams(ThemeManager.getCandidateHeight(), ThemeManager.getContentHeight()));
        mListView.setAdapter(mAdapter=new WaterfallAdapter(mTrime.getClipboard()));
        KeyView mHide = new KeyView(getContext(),mKeyStyle);
        mHide.setText("△");
        mHide.setContentDescription("返回");
        mHide.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mTrime.showClipboardView(false);
            }
        });
        KeyView mPrev = new KeyView(getContext(),mKeyStyle);
        mPrev.setText("⇑");
        mPrev.setContentDescription("上一页");
        mPrev.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mListView.smoothScrollBy(0,-ThemeManager.getContentHeight());
            }
        });
        KeyView mNext = new KeyView(getContext(),mKeyStyle);
        mNext.setText("⇓");
        mNext.setContentDescription("下一页");
        mNext.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mListView.smoothScrollBy(0,ThemeManager.getContentHeight());
            }
        });
        mChar = new KeyView(getContext(),mKeyStyle);
        mChar.setText("撤销");
        mChar.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mTrime.onKey(KeyEvent.KEYCODE_Z,KeyEvent.META_CTRL_ON);
            }
        });
        mButtonBar.addView(mHide,new LinearLayout.LayoutParams(ThemeManager.getCandidateHeight(), ViewGroup.LayoutParams.MATCH_PARENT,1));
        mButtonBar.addView(mPrev,new LinearLayout.LayoutParams(ThemeManager.getCandidateHeight(), ViewGroup.LayoutParams.MATCH_PARENT,1));
        mButtonBar.addView(mNext,new LinearLayout.LayoutParams(ThemeManager.getCandidateHeight(), ViewGroup.LayoutParams.MATCH_PARENT,1));
        mButtonBar.addView(mChar,new LinearLayout.LayoutParams(ThemeManager.getCandidateHeight(), ViewGroup.LayoutParams.MATCH_PARENT,1));
    }

    public boolean pageDown() {
        mListView.smoothScrollBy(0,ThemeManager.getContentHeight());
        return true;
    }

    public boolean pageUp() {
        mListView.smoothScrollBy(0,-ThemeManager.getContentHeight());
        return true;
    }

    public void update() {
        if (mListView.isComputingLayout()) {
            // 如果正在布局，延迟一帧更新，防止冲突
            mListView.post(this::update);
            return;
        }
        mListView.stopScroll(); // 刷新数据前停止可能的滑动
        mAdapter.setData(false);
        mListView.scrollToPosition(0);
        clipboardTitle.setSelected(true);
        phraseTitle.setSelected(false);
        //mListView.invalidateItemDecorations();
    }

    public void show(){
        if (mListView.isComputingLayout()) {
            // 如果正在布局，延迟一帧更新，防止冲突
            mListView.post(this::show);
            return;
         }
         mAdapter.setData(false);
         mListView.scrollToPosition(0);
         clipboardTitle.setSelected(true);
         phraseTitle.setSelected(false);
    }


    public void showPhrase() {
        if (mListView.isComputingLayout()) {
            // 如果正在布局，延迟一帧更新，防止冲突
            mListView.post(this::show);
            return;
        }
        mAdapter.setData(true);
        mListView.scrollToPosition(0);
        clipboardTitle.setSelected(false);
        phraseTitle.setSelected(true);
    }
}
