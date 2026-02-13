/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.IBinder;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.osfans.trime.PrefLauncher;
import com.osfans.trime.TrimeService;
import com.osfans.trime.core.DataManager;
import com.osfans.trime.core.Rime;
import com.osfans.trime.core.SchemaItem;
import com.osfans.trime.theme.ThemeManager;

import java.util.ArrayList;

public class SchemaDialog {
    private AlertDialog mDig;
    private IBinder mToken;

    public SchemaDialog(Context context) {
        DataManager.sync();
        if (TrimeService.getInstance() == null) {
            Toast.makeText(context, "请先启用输入法", Toast.LENGTH_SHORT).show();
            return;
        }
        SchemaItem[] as = Rime.getAvailableRimeSchemaList();
        SchemaItem[] bs = Rime.getSelectedRimeSchemaList();

        String[] ss = new String[as.length];
        boolean[] sb = new boolean[as.length];
        ArrayList<String> rs = new ArrayList<>();
        for (int i = 0; i < as.length; i++) {
            ss[i] = as[i].getName();
            if (checkSchema(bs, as[i])) {
                sb[i] = true;
                rs.add(as[i].getId());
            }
        }
        mDig=new AlertDialog.Builder(context, ThemeManager.getDialogTheme())
                .setTitle("管理方案")
                .setMultiChoiceItems(ss, sb, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        if (isChecked) {
                            rs.add(as[which].getId());
                        } else {
                            rs.remove(as[which].getId());
                        }
                    }
                })
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String[] rr = new String[rs.size()];
                        rs.toArray(rr);
                        Rime.selectRimeSchemas(rr);
                        new DeployDialog(mDig.getContext()).show(mToken);
                    }
                })
                .setNegativeButton("取消", null)
                .create();
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

    private boolean checkSchema(SchemaItem[] bs, SchemaItem a) {
        for (SchemaItem b : bs) {
            if(b.getId().equals(a.getId()))
                return true;
        }
        return false;
    }
}
