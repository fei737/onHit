package mba.vm.onhit.hook

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.Tag
import android.util.Log

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kyuubiran.ezxhelper.xposed.EzXposed
import mba.vm.onhit.BuildConfig
import mba.vm.onhit.Constant.Companion.NFC_SERVICE_PACKAGE_NAME

class MainHook : IXposedHookLoadPackage {

    private var isReaderBlocked = false
    private val TAG = "NdefEmulator_Xposed"

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        EzXposed.initHandleLoadPackage(lpparam)

        if (lpparam.packageName == NFC_SERVICE_PACKAGE_NAME) {
            // 先尝试挂载外部屏蔽逻辑 (防弹衣版)
            injectNfcBlockerSafe(lpparam.classLoader)

            // 🚨 核心保障：即使上面报错，下面这行也绝对会执行！内部模拟永不掉线！
            initHook(lpparam.classLoader, NfcServiceHook, NfcDispatchManagerHook, PackageManagerHook, HostEmulationManagerHook)
        } else {
            initHook(lpparam.classLoader, PackageManagerHook)
        }
    }

    private fun injectNfcBlockerSafe(classLoader: ClassLoader) {
        // 1. 尝试注册广播接收器
        try {
            val nfcServiceClass = XposedHelpers.findClass("com.android.nfc.NfcService", classLoader)
            XposedBridge.hookAllMethods(nfcServiceClass, "onCreate", object : XC_MethodHook() {
                @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val context = param.thisObject as Context
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(c: Context?, intent: Intent?) {
                                if (intent?.action == "mba.vm.onhit.BLOCK_NFC_READER") {
                                    isReaderBlocked = intent.getBooleanExtra("block", false)
                                    Log.i(TAG, "🔥 NFC屏蔽离合器: $isReaderBlocked")
                                }
                            }
                        }
                        val filter = IntentFilter("mba.vm.onhit.BLOCK_NFC_READER")
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            context.registerReceiver(receiver, filter, "android.permission.NFC", null, Context.RECEIVER_EXPORTED)
                        } else {
                            context.registerReceiver(receiver, filter, "android.permission.NFC", null)
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "🚨 广播注册失败: ${t.message}")
                    }
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "🚨 NfcService onCreate Hook失败: ${t.message}")
        }

        // 2. 尝试屏蔽物理卡片读取
        try {
            XposedHelpers.findAndHookMethod("com.android.nfc.NfcDispatcher", classLoader, "dispatchTag", Tag::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isReaderBlocked) param.result = 1
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "🚨 NfcDispatcher dispatchTag Hook失败: ${t.message}")
        }

        // 3. 尝试屏蔽系统声音
        try {
            XposedHelpers.findAndHookMethod("com.android.nfc.NfcService", classLoader, "playSound", Int::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isReaderBlocked) param.result = null
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "🚨 NfcService playSound Hook失败: ${t.message}")
        }

        // 4. 尝试屏蔽底层轮询 (最容易崩溃的地方)
        try {
            XposedHelpers.findAndHookMethod("com.android.nfc.NfcDiscoveryParameters\$Builder", classLoader, "setEnablePolling", Boolean::class.javaPrimitiveType, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isReaderBlocked) param.args[0] = false
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "🚨 NfcDiscoveryParameters setEnablePolling Hook失败: ${t.message}")
        }
    }

    private fun initHook(classLoader: ClassLoader, vararg hooks: BaseHook) {
        hooks.forEach { hook ->
            try {
                hook.init(classLoader)
                Log.i(TAG, "✅ 模块加载成功: ${hook.name}")
            } catch (e: Exception) {
                XposedBridge.log("[ ${BuildConfig.APPLICATION_ID} ] Failed to Init ${hook.name}, ${e.message}")
            }
        }
    }
}