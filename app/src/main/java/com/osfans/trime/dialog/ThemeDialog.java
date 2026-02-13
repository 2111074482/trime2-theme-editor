/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.androlua.EditDialog;
import com.osfans.trime.Config;
import com.osfans.trime.TrimeService;
import com.osfans.trime.core.Rime;
import com.osfans.trime.theme.ThemeManager;

import java.io.File;

public class ThemeDialog {
    private AlertDialog mDig;
    private boolean mNeedUpdateRimeOption;
    private IBinder mToken;

    public ThemeDialog(Context context) {
        //if (TrimeService.getInstance() == null) {
        //    Toast.makeText(context, "请先启用输入法", Toast.LENGTH_SHORT).show();
        //    return;
        //}
        AlertDialog.Builder builder =
                new AlertDialog.Builder(context, ThemeManager.getDialogTheme())
                        .setTitle("选择主题")
                        //.setCancelable(true)
                        .setPositiveButton(
                                "样式",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface di, int id) {
                                        di.dismiss();
                                        new StyleDialog(context).show(mToken);
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel, null);
        if (Rime.getCurrentRimeSchema().equals(".default")) {
            builder.setMessage("请先正确配置"); //提示安裝碼表
        } else {
            String id = Config.getTheme();
            String[] ss = Config.getThemes();
            int idx = 0;
            for (int i = 0; i < ss.length; i++) {
                if(ss[i].equals(id))
                    idx=i;
            }
            builder.setSingleChoiceItems(
                    ss,
                    idx,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface di, int id) {
                            di.dismiss();
                            Config.setTheme(ss[id]);
                            mNeedUpdateRimeOption = true;
                            TrimeService trime = TrimeService.getInstance();
                            if(trime!=null){
                                trime.setTheme(ss[id]);
                            }
                        }
                    });
        }
        mDig = builder.create();
    }


    public void show() {
        if(mDig==null)
            return;
        mDig.show();
    }

    public void show(IBinder token) {
        if(mDig==null)
            return;
        mToken=token;
        if(mToken==null){
            show();
            return;
        }
        Window win = mDig.getWindow();
        WindowManager.LayoutParams attr = win.getAttributes();
        attr.type=WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
        win.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        attr.token=token;
        win.setAttributes(attr);
        mDig.show();
    }
}
