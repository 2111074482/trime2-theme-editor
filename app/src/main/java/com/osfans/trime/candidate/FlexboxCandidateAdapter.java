/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.candidate;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.flexbox.FlexboxLayoutManager;
import com.osfans.trime.TrimeService;
import com.osfans.trime.core.CandidateItem;
import com.osfans.trime.theme.Style;
import com.osfans.trime.theme.ThemeManager;

import java.util.ArrayList;

public class FlexboxCandidateAdapter extends RecyclerView.Adapter<FlexboxCandidateAdapter.CandidateViewHolder> {

    private final ArrayList<CandidateItem> mData;
    private final Style mCandidateStyle;
    private final Style mCommentStyle;
    private boolean mIsLoading;

    public FlexboxCandidateAdapter(ArrayList<CandidateItem> data) {
        this.mData = data;
        mCandidateStyle = ThemeManager.getStyle().getStyle("candidate");
        mCommentStyle = mCandidateStyle.getStyle("comment",mCandidateStyle);
    }

    @NonNull
    @Override
    public FlexboxCandidateAdapter.CandidateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        // 1. 创建容器（相当于 XML 里的 LinearLayout）
        LinearLayout layout = new LinearLayout(context);
        // 在 onCreateViewHolder 里的 layout 设置之后添加
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        layout.setBackgroundResource(outValue.resourceId);
        layout.setClickable(true);
        layout.setGravity(Gravity.CENTER);
        layout.setOrientation(LinearLayout.VERTICAL);
        int dp = ThemeManager.dp2px(8);
        layout.setPadding(dp, dp, dp, dp);
        // 关键：必须使用 FlexboxLayoutManager.LayoutParams
        // 宽度设为 WRAP_CONTENT，高度设为 WRAP_CONTENT
        FlexboxLayoutManager.LayoutParams lp = new FlexboxLayoutManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        //lp.setFlexBasisPercent(0.2f);
        lp.setFlexGrow(1.0f);
        layout.setLayoutParams(lp);

        // 2. 创建并配置 TextView (关键：必须给子 View 设置 LayoutParams)
        LinearLayout.LayoutParams childLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView tvComment = new TextView(context);
        tvComment.setTextColor(mCommentStyle.getTextColor(0xff444444));
        tvComment.setTextSize(TypedValue.COMPLEX_UNIT_DIP,mCommentStyle.getTextSize(12));
        tvComment.setLayoutParams(childLp);

        TextView tvText = new TextView(context);
        tvText.setTextColor(mCandidateStyle.getTextColor(0xff000000));
        tvText.setTextSize(TypedValue.COMPLEX_UNIT_DIP,mCandidateStyle.getTextSize(22));
        tvText.setLayoutParams(childLp);

        layout.addView(tvComment);
        layout.addView(tvText);

        FlexboxCandidateAdapter.CandidateViewHolder holder = new FlexboxCandidateAdapter.CandidateViewHolder(layout, tvComment, tvText);

        // 在这里只设置一次监听器
        holder.itemView.setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition(); // 获取当前实时位置
            if (position != RecyclerView.NO_POSITION && mData != null) {
                CandidateItem item = mData.get(position);
                TrimeService.getInstance().selectCandidate(item.getIndex());
            }
        });

        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull FlexboxCandidateAdapter.CandidateViewHolder holder, int position) {
        final CandidateItem data = mData.get(position);
        if (TextUtils.isEmpty(data.getComment())) {
            holder.tvComment.setVisibility(View.GONE);
        } else {
            holder.tvComment.setVisibility(View.VISIBLE);
            holder.tvComment.setText(data.getComment());
        }

        holder.tvText.setText(data.getText());
        holder.itemView.setContentDescription(data.getText());
        // 2. 强制处理宽度更新
        holder.itemView.post(() -> {
            ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
            if (lp != null) {
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                holder.itemView.setLayoutParams(lp);
                // 关键：强制要求父容器重新布局
                //holder.itemView.requestLayout();
            }
        });
        // 检查是否滑到了最后三项（提前加载，体验更好）
        if (position >= getItemCount() - 10) {
            // 使用 post 避免在布局阶段刷新
            holder.itemView.post(this::loadNextPage);
        }
    }

    private void loadNextPage() {
        if (mIsLoading) return;
        mIsLoading = true;
        ArrayList<CandidateItem> cand = CandidatesManager.next();
        if (!cand.isEmpty()) {
            int startPos = mData.size();
            mData.addAll(cand);
            // 不要 notifyDataSetChanged()，只通知新增的部分，性能更好
            notifyItemRangeInserted(startPos, cand.size());
        }
        mIsLoading = false;
    }

    @Override
    public int getItemCount() {
        return mData == null ? 0 : mData.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setData(ArrayList<CandidateItem> next) {
        mData.clear();
        mData.addAll(next);
        notifyDataSetChanged();
    }

    public static class CandidateViewHolder extends RecyclerView.ViewHolder {

        public final TextView tvComment;
        public final TextView tvText;

        public CandidateViewHolder(@NonNull View itemView, TextView tvComment, TextView tvText) {
            super(itemView);
            this.tvComment = tvComment;
            this.tvText = tvText;
        }
    }
}
