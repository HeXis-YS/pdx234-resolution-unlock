package xyz.cirno.pdx234.resolution_sup;

import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.Display;
import android.view.DisplayInfo;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedInit implements IXposedHookLoadPackage{
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (lpparam.packageName.equals("android")) {
            handleLoadSystemServer(lpparam);
        } else if (lpparam.packageName.equals("com.android.settings")) {
            handleLoadSettings(lpparam);
        } else if (lpparam.packageName.equals("com.android.systemui")) {
            handleLoadSystemUI(lpparam);
        }
    }

    private void handleLoadSettings(XC_LoadPackage.LoadPackageParam lpparam) {
        if (Build.VERSION.SDK_INT >= 34) {
            final var removeClass = XposedHelpers.findClass("com.sonymobile.settings.preference.RemovePreference", lpparam.classLoader);
            final var targetKeyField = XposedHelpers.findField(removeClass, "mTargetKey");
            targetKeyField.setAccessible(true);
            XposedHelpers.findAndHookConstructor(removeClass, Context.class, AttributeSet.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.hasThrowable()) return;
                    final String mTargetKey = (String) targetKeyField.get(param.thisObject);
                    if ("screen_resolution".equals(mTargetKey)) {
                        targetKeyField.set(param.thisObject, "screen_resolution_");
                    }
                }
            });
        }
    }

    private void handleLoadSystemServer(XC_LoadPackage.LoadPackageParam lpparam) {
        final var dmsBinderServiceClass = XposedHelpers.findClass("com.android.server.display.DisplayManagerService$BinderService", lpparam.classLoader);

        XposedHelpers.findAndHookMethod(dmsBinderServiceClass, "getDisplayInfo", int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final var displayId = (int) param.args[0];
                if (displayId == 0) {
                    // calling for internal display
                    var info = (DisplayInfo) param.getResult();
                    if (info == null) return;
                    final var supportedModes = info.supportedModes;
                    if (supportedModes == null || supportedModes.length == 0) return;
                    final var activeMode = info.getMode();

                    // sort active mode to the first in supportedModes to worsettingskaround buggy apps that assumes the first mode is the active mode
                    // sorting policy:
                    // 1. active mode
                    // 2. alternative refresh rates of active resolution
                    // 3. other modes
                    final var sortedModes = Arrays.copyOf(supportedModes, supportedModes.length);
                    Arrays.sort(sortedModes, Comparator.comparing(o -> {
                        if (o.getModeId() == activeMode.getModeId()) return 0;
                        if (o.getPhysicalWidth() == activeMode.getPhysicalWidth() && o.getPhysicalHeight() == activeMode.getPhysicalHeight()) {
                            return 1;
                        }
                        return 2;
                    }));
                    var newinfo = new DisplayInfo(info);
                    newinfo.supportedModes = sortedModes;
                    param.setResult(newinfo);
                }
            }
        });
    }

    private void handleLoadSystemUI(XC_LoadPackage.LoadPackageParam lpparam) {
        final var navigationBarInflaterViewClass = XposedHelpers.findClass("com.android.systemui.navigationbar.NavigationBarInflaterView", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(navigationBarInflaterViewClass, "inflateLayout", String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String layout = (String) param.args[0];
                if (layout.contains("back") && layout.contains("recent")) {
                    layout = layout.replace("back", "__TEMP__");
                    layout = layout.replace("recent", "back");
                    layout = layout.replace("__TEMP__", "recent");
                    param.args[0] = layout;
                }
            }
        });
    }
}
