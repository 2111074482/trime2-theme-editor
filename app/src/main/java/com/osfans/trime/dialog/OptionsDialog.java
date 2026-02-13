/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.IBinder;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.osfans.trime.theme.ThemeManager;
import com.osfans.trime.util.Function;
import com.osfans.trime.TrimeService;
import com.osfans.trime.core.Rime;
import com.osfans.trime.core.SchemaItem;

public class OptionsDialog {
    private AlertDialog mDig;
    private boolean mNeedUpdateRimeOption;
    private IBinder mToken;

    public OptionsDialog(Context context) {
        if (TrimeService.getInstance() == null) {
            Toast.makeText(context, "请先启用输入法", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder =
                new AlertDialog.Builder(context, ThemeManager.getDialogTheme())
                        .setTitle("选择方案")
                        //.setCancelable(true)
                        .setPositiveButton(
                                "设置",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface di, int id) {
                                        Function.showPrefDialog(context); //全局設置
                                        di.dismiss();
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel, null);
        if (Rime.getCurrentRimeSchema().equals(".default")) {
            builder.setMessage("没有方案，请先添加启用方案"); //提示安裝碼表
        } else {
            String id = Rime.getCurrentRimeSchema();
            SchemaItem[] as = Rime.getRimeSchemaList();
            if(as==null){
                Toast.makeText(context,"请先启用输入法",Toast.LENGTH_SHORT).show();
                return;
            }
            String[] ss = new String[as.length];
            int idx = 0;
            for (int i = 0; i < as.length; i++) {
                ss[i]=as[i].getName();
                if(as[i].getId().equals(id))
                    idx=i;
            }
            builder.setNeutralButton(
                    "管理方案",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface di, int id) {
                            new SchemaDialog(context).show(mToken); //部署方案
                            di.dismiss();
                        }
                    });
            builder.setSingleChoiceItems(
                    ss,
                    idx,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface di, int id) {
                            di.dismiss();
                            Rime.selectRimeSchema(as[id].getId()); //切換方案
                            mNeedUpdateRimeOption = true;
                            Function.saveString(context,"select_schema_id",as[id].getId());
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
