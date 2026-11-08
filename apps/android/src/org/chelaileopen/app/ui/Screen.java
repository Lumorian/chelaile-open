package org.chelaileopen.app.ui;

import android.view.View;

import org.chelaileopen.app.App;
import org.chelaileopen.app.MainActivity;

/** 页面基类：延迟构建视图，进入/离开时管理轮询。 */
public abstract class Screen {
    protected final MainActivity host;
    protected final App app;
    private View view;

    protected Screen(MainActivity host) {
        this.host = host;
        this.app = App.get(host);
    }

    public abstract String title();

    public boolean showBack() {
        return false;
    }

    protected abstract View build();

    public final View getView() {
        if (view == null) view = build();
        return view;
    }

    /** 页面可见（首次或从别的页面返回）。 */
    public void onResume() {
    }

    /** 页面不可见。 */
    public void onPause() {
    }

    public boolean onBackPressed() {
        return false;
    }

    /** 系统权限结果转发到当前页面。 */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    }

    protected void toast(String msg) {
        host.toast(msg);
    }
}
