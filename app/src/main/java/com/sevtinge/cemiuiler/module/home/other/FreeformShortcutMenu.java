package com.sevtinge.cemiuiler.module.home.other;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.sevtinge.cemiuiler.R;
import com.sevtinge.cemiuiler.module.base.BaseHook;
import com.sevtinge.cemiuiler.module.securitycenter.SecurityCenterDexKit;
import com.sevtinge.cemiuiler.utils.Helpers;
import com.sevtinge.cemiuiler.utils.LogUtils;
import com.sevtinge.cemiuiler.utils.Settings;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.luckypray.dexkit.descriptor.member.DexMethodDescriptor;

import static de.robv.android.xposed.XposedHelpers.callMethod;

public class FreeformShortcutMenu extends BaseHook {

    Class<?> mActivity;
    Class<?> mViewDarkModeHelper;
    Class<?> mSystemShortcutMenu;
    Class<?> mSystemShortcutMenuItem;
    Class<?> mAppShortcutMenu;
    Class<?> mShortcutMenuItem;
    Class<?> mAppDetailsShortcutMenuItem;
    Class<?> mActivityUtilsCompat;
    Class<?> mRecentsAndFSGestureUtils;

    Context mContext;

    XC_MethodHook.Unhook mShortCutMenuItemHook;

    @Override
    public void init() {

        mActivity = Activity.class;
        mViewDarkModeHelper = findClassIfExists("com.miui.home.launcher.util.ViewDarkModeHelper");
        mSystemShortcutMenu = findClassIfExists("com.miui.home.launcher.shortcuts.SystemShortcutMenu");
        mSystemShortcutMenuItem = findClassIfExists("com.miui.home.launcher.shortcuts.SystemShortcutMenuItem");
        mAppShortcutMenu = findClassIfExists("com.miui.home.launcher.shortcuts.AppShortcutMenu");
        mShortcutMenuItem = findClassIfExists("com.miui.home.launcher.shortcuts.ShortcutMenuItem");
        mAppDetailsShortcutMenuItem = findClassIfExists("com.miui.home.launcher.shortcuts.SystemShortcutMenuItem$AppDetailsShortcutMenuItem");
        mActivityUtilsCompat = findClassIfExists("com.miui.launcher.utils.ActivityUtilsCompat");
        mRecentsAndFSGestureUtils = findClassIfExists("com.miui.home.launcher.RecentsAndFSGestureUtils");

        try {

            hookAllMethods(mViewDarkModeHelper, "onConfigurationChanged", new MethodHook() {
                @Override
                protected void after(MethodHookParam param) throws Throwable {
                    XposedHelpers.callStaticMethod(mSystemShortcutMenuItem, "createAllSystemShortcutMenuItems");
                }
            });

            hookAllMethods(mShortcutMenuItem, "getShortTitle", new MethodHook() {
                @Override
                protected void after(MethodHookParam param) throws Throwable {
                    if (param.getResult().equals("应用信息")) {
                        param.setResult("信息");
                    }
                }
            });

            hookAllMethods(mActivity, "onCreate", new MethodHook() {
                @Override
                protected void after(MethodHookParam param) throws Throwable {
                    mContext = (Context) param.thisObject;
                }
            });

            findAndHookMethod(mAppDetailsShortcutMenuItem, "getOnClickListener", new MethodHook() {
                @Override
                protected void before(MethodHookParam param) throws Throwable {
                    Resources modRes = Helpers.getModuleRes(mContext);
                    Object obj = param.thisObject;
                    CharSequence mShortTitle = (CharSequence) callMethod(obj, "getShortTitle");

                    if (mShortTitle.equals(modRes.getString(R.string.share_center))) {
                        XposedHelpers.callStaticMethod(mRecentsAndFSGestureUtils, "startWorld", mContext);
                    } else if (mShortTitle.equals(modRes.getString(R.string.floating_window))) {
                        param.setResult(getFreeformOnClickListener(obj));
                    }
                }
            });

            hookAllMethods(mSystemShortcutMenu, "getMaxShortcutItemCount", new MethodHook() {
                @Override
                protected void after(MethodHookParam param) throws Throwable {
                    param.setResult(5);
                }
            });

            hookAllMethods(mAppShortcutMenu, "getMaxShortcutItemCount", new MethodHook() {
                @Override
                protected void after(MethodHookParam param) throws Throwable {
                    param.setResult(5);
                }
            });

            hookAllMethods(mSystemShortcutMenuItem, "createAllSystemShortcutMenuItems", new MethodHook() {
                @SuppressLint("DiscouragedApi")
                @Override
                protected void after(MethodHookParam param) throws Throwable {

                    Resources modRes = Helpers.getModuleRes(mContext);

                    List mAllSystemShortcutMenuItems = (List) XposedHelpers.getStaticObjectField(mSystemShortcutMenuItem, "sAllSystemShortcutMenuItems");

                    Object mSmallWindowInstance = XposedHelpers.newInstance(mAppDetailsShortcutMenuItem);
                    callMethod(mSmallWindowInstance, "setShortTitle", modRes.getString(R.string.floating_window));
                    callMethod(mSmallWindowInstance, "setIconDrawable", ContextCompat.getDrawable(mContext, mContext.getResources().getIdentifier("ic_task_small_window", "drawable", mContext.getPackageName())));

                    ArrayList sAllSystemShortcutMenuItems = new ArrayList();
                    sAllSystemShortcutMenuItems.add(mSmallWindowInstance);
                    sAllSystemShortcutMenuItems.addAll(mAllSystemShortcutMenuItems);
                    XposedHelpers.setStaticObjectField(mSystemShortcutMenuItem, "sAllSystemShortcutMenuItems", sAllSystemShortcutMenuItems);
                }
            });

        } catch (Throwable th) {
            LogUtils.log("FreeformShortcutMenu" + th);
        }
    }

    private View.OnClickListener getFreeformOnClickListener(Object obj) {
        View.OnClickListener onClickListener = view -> {
            Intent intent = new Intent();
            Context mContext = view.getContext();
            ComponentName mComponentName = (ComponentName) callMethod(obj, "getComponentName", new Object[0]);
            intent.setAction("android.intent.action.MAIN");
            intent.addCategory("android.intent.category.DEFAULT");
            intent.setComponent(mComponentName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Object makeFreeformActivityOptions = XposedHelpers.callStaticMethod(mActivityUtilsCompat, "makeFreeformActivityOptions", new Object[]{mContext, mComponentName.getPackageName()});

            if (makeFreeformActivityOptions != null) {
                mContext.startActivity(intent, (Bundle) callMethod(makeFreeformActivityOptions, "toBundle", new Object[0]));
            }
        };
        return onClickListener;
    }
}
