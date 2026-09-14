package com.google.android.accessibility.selecttospeak;

import android.content.Intent;

/**
 * 这个类刻意使用了与 Android 系统内置“选择朗读”无障碍服务完全相同的
 * 包名和类名。实际业务逻辑仍由 QQAccessibilityService 提供。
 *
 * 这里补充服务生命周期的恢复钩子：如果系统重新绑定这个无障碍服务，
 * 会正常进入 onRebind()/onServiceConnected()，让父类重新建立运行状态。
 * 需要注意：Android 不允许无障碍服务自行静默打开系统无障碍开关，
 * 因此这里做的是“支持系统重新绑定后的恢复”，而不是强行重新授权。
 */
public class SelectToSpeakService extends com.example.u7e5f3218e9.QQAccessibilityService {

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // 返回 true，系统后续重新绑定时会回调 onRebind()。
        return true;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        // 无障碍服务的实际恢复由系统完成；父类的 onServiceConnected()
        // 会在系统重新建立无障碍连接时负责重新初始化。
    }
}
