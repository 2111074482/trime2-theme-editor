/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.candidate;

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

import com.osfans.trime.TrimeService;
import com.osfans.trime.core.CandidateItem;
import com.osfans.trime.theme.Style;
import com.osfans.trime.theme.ThemeManager;

import java.util.ArrayList;

public class CandidateAdapter extends RecyclerView.Adapter<CandidateAdapter.CandidateViewHolder> {

    private final ArrayList<CandidateItem> mData;
    private final Style mCandidateStyle;
    private final Style mCommentStyle;
    private boolean mIsLoading;

    public CandidateAdapter(ArrayList<CandidateItem> data) {
        this.mData = data;
        mCandidateStyle=ThemeManager.getStyle().getStyle("candidate");
        mCommentStyle=mCandidateStyle.getStyle("comment",mCandidateStyle);
    }

    @NonNull
    @Override
    public CandidateAdapter.CandidateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context context = parent.getContext();
        // 1. 创建容器（相当于 XML 里的 LinearLayout）
        LinearLayout layout = new LinearLayout(context);
        layout.setGravity(Gravity.CENTER);
        // 在 onCreateViewHolder 里的 layout 设置之后添加
        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        layout.setBackgroundResource(outValue.resourceId);
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

        TextView tvComment = new TextView(context);
        tvComment.setPadding(0,0,0,0);
        tvComment.setLineSpacing(0, 0);
        tvComment.setGravity(Gravity.CENTER);
        tvComment.setTextColor(mCommentStyle.getTextColor(0xff444444));
        tvComment.setTextSize(TypedValue.COMPLEX_UNIT_DIP,mCommentStyle.getTextSize(12));
        tvComment.setIncludeFontPadding(false);

        TextView tvText = new TextView(context);
        tvText.setPadding(0,0,0,0);
        tvText.setLineSpacing(0, 0.1f);
        tvText.setGravity(Gravity.CENTER);
        tvText.setTextColor(mCandidateStyle.getTextColor(0xff000000));
        tvText.setTextSize(TypedValue.COMPLEX_UNIT_DIP,mCandidateStyle.getTextSize(22));
        tvText.setMaxLines(1); // 防止意外换行导致宽度测量错误
        tvText.setIncludeFontPadding(false); // 去除由于字体规范导致的额外内边距

       // 1. 先加注释（上方）
        LinearLayout.LayoutParams commentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                0,tvComment.getTextSize());
        commentLp.gravity = Gravity.CENTER;
// 2. 后加正文（下方）
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                0,tvText.getTextSize());
        textLp.gravity = Gravity.CENTER;

        layout.addView(tvComment, commentLp);
        layout.addView(tvText, textLp);

        CandidateViewHolder holder = new CandidateViewHolder(layout, tvComment, tvText);

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
    public void onBindViewHolder(@NonNull CandidateAdapter.CandidateViewHolder holder, int position) {
        final CandidateItem data = mData.get(position);
        // 处理注释为空的情况，隐藏 View 节省空间
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
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                holder.itemView.setLayoutParams(lp);
                // 关键：强制要求父容器重新布局
                //holder.itemView.requestLayout();
            }
        });
        // 检查是否滑到了最后三项（提前加载，体验更好）
        if (position >= getItemCount() - 3) {
            // 使用 post 避免在布局阶段刷新
            holder.itemView.post(this::loadNextPage);
        }
    }

    private void loadNextPage() {
        if (mIsLoading) return;
        mIsLoading = true;
        ArrayList<CandidateItem> cand = CandidatesManager.next();
        if (cand != null && !cand.isEmpty()) {
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
