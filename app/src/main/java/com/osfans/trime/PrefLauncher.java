/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayListAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.androlua.LuaUtil;
import com.osfans.trime.core.DataManager;
import com.osfans.trime.dialog.DeployDialog;
import com.osfans.trime.dialog.KeyboardDialog;
import com.osfans.trime.dialog.OptionsDialog;
import com.osfans.trime.dialog.SchemaDialog;
import com.osfans.trime.dialog.SchemaGroupDialog;
import com.osfans.trime.dialog.StyleDialog;
import com.osfans.trime.dialog.ThemeDialog;

public class PrefLauncher extends Activity implements AdapterView.OnItemClickListener {

    private static IBinder mToken;

    public static IBinder getToken() {
        return mToken;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setTheme(android.R.style.Theme_DeviceDefault_DayNight);
        } else {
            setTheme(android.R.style.Theme_DeviceDefault);
        }
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        DataManager.sync();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        ArrayListAdapter<String> adapter = new ArrayListAdapter<>(this,new String[]{
                "切换输入法",
                "方案组",
                "管理方案",
                "输入方案",
                "键盘主题",
                "颜色样式",
                "默认键盘"
        });
        ListView mListView = new ListView(this);
        // 解决内容被导航栏遮挡的关键：应用 WindowInsets
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
        {
            mListView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                    // 将系统栏占用的空间转化为 ListView 的 Padding
                    v.setPadding(
                            insets.getSystemWindowInsetLeft(),
                            insets.getSystemWindowInsetTop(),
                            insets.getSystemWindowInsetRight(),
                            insets.getSystemWindowInsetBottom()
                    );
                    return insets.consumeSystemWindowInsets();
                }
            });
        }
        mListView.setAdapter(adapter);
        mListView.addFooterView(new EditText(this));
        setContentView(mListView);
        mListView.setOnItemClickListener(this);
     }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0,1,0,"部署").setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (TrimeService.getInstance() == null) {
            Toast.makeText(this, "请先启用输入法", Toast.LENGTH_SHORT).show();
            return super.onOptionsItemSelected(item);
        }
        if(item.getItemId()==1){
            new DeployDialog(this).show();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        LuaUtil.checkStorage(this);
   }

    @Override
    protected void onStart() {
        super.onStart();
        mToken=getWindow().getDecorView().getWindowToken();
    }

    @Override
    protected void onStop() {
        mToken=null;
        super.onStop();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        switch (position){
            case 0:
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();
                break;
            case 1:
                new SchemaGroupDialog(PrefLauncher.this).show();
                break;
            case 2:
                new SchemaDialog(this).show();
                break;
            case 3:
                new OptionsDialog(this).show();
                break;
            case 4:
                new ThemeDialog(this).show();
                break;
            case 5:
                new StyleDialog(this).show();
                break;
            case 6:
                new KeyboardDialog(this).show();
                break;
        }
    }
}
