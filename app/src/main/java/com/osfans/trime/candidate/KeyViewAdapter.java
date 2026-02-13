/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.candidate;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.osfans.trime.Key;
import com.osfans.trime.TrimeService;
import com.osfans.trime.core.CandidateItem;
import com.osfans.trime.keyboard.KeyView;
import com.osfans.trime.theme.KeyStyle;
import com.osfans.trime.theme.Style;
import com.osfans.trime.theme.ThemeManager;

import java.util.ArrayList;

public class KeyViewAdapter extends RecyclerView.Adapter<KeyViewAdapter.KeyViewHolder> {

    private final ArrayList<String> mData;
    private final Style mCandidateStyle;
    private final KeyStyle mKeyStyle;
    private boolean mIsLoading;

    public KeyViewAdapter(ArrayList<String> data) {
        this.mData = data;
        mCandidateStyle= ThemeManager.getStyle().getStyle("candidate");
        mKeyStyle=mCandidateStyle.getKeyStyle("key",ThemeManager.getStyle().getKeyStyle("key"));
    }

    @NonNull
    @Override
    public KeyViewAdapter.KeyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        // 1. 创建容器（相当于 XML 里的 LinearLayout）
        LinearLayout layout = new LinearLayout(context);
        layout.setGravity(Gravity.CENTER);
        layout.setClickable(true);
        layout.setOrientation(LinearLayout.VERTICAL);
        // 内边距：左右 8dp, 上下 2dp
        int px8 = ThemeManager.dp2px(8);
        int px1 = ThemeManager.dp2px(1);
        layout.setPadding(px8, px1, px8, px1);

        // 设置容器 LayoutParams
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT); // 横向列表高度建议 MATCH_PARENT
        layout.setLayoutParams(lp);
        KeyView tvText = new KeyView(context,mKeyStyle);

// 2. 后加正文（下方）
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        textLp.gravity = Gravity.CENTER;

        layout.addView(tvText, textLp);

        KeyViewHolder holder = new KeyViewHolder(layout, tvText);

        // 在这里只设置一次监听器
        holder.itemView.setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition(); // 获取当前实时位置
            if (position != RecyclerView.NO_POSITION && mData != null) {
                String item = mData.get(position);
                TrimeService.getInstance().onEvent(new Key(item).getEvent());
            }
        });

        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull KeyViewAdapter.KeyViewHolder holder, int position) {
        final String data = mData.get(position);
        holder.tvText.setKey(data);
        // 2. 强制处理宽度更新
        holder.itemView.post(() -> {
            ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
            if (lp != null) {
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                holder.itemView.setLayoutParams(lp);
                // 关键：强制要求父容器重新布局
                //holder.itemView.requestLayout();
            }
        });
    }


    @Override
    public int getItemCount() {
        return mData == null ? 0 : mData.size();
    }

    public void setData(ArrayList<String> next) {
        mData.clear();
        mData.addAll(next);
        notifyDataSetChanged();
    }

    public static class KeyViewHolder extends RecyclerView.ViewHolder {

        public final KeyView tvText;

        public KeyViewHolder(@NonNull View itemView, KeyView tvText) {
            super(itemView);
            this.tvText = tvText;
        }
    }
}

