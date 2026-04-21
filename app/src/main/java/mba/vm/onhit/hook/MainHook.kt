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
            injectNfcBlocker(lpparam.classLoader)
            initHook(lpparam.classLoader, NfcServiceHook, NfcDispatchManagerHook, PackageManagerHook, HostEmulationManagerHook)
        } else {
            initHook(lpparam.classLoader, PackageManagerHook)
        }
    }

    private fun injectNfcBlocker(classLoader: ClassLoader) {
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
        } catch (t: Throwable) {}

        XposedHelpers.findAndHookMethod("com.android.nfc.NfcDispatcher", classLoader, "dispatchTag", Tag::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isReaderBlocked) param.result = 1
            }
        })

        XposedHelpers.findAndHookMethod("com.android.nfc.NfcService", classLoader, "playSound", Int::class.javaPrimitiveType, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isReaderBlocked) param.result = null
            }
        })

        XposedHelpers.findAndHookMethod("com.android.nfc.NfcDiscoveryParameters\$Builder", classLoader, "setEnablePolling", Boolean::class.javaPrimitiveType, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (isReaderBlocked) param.args[0] = false
            }
        })
    }

    private fun initHook(classLoader: ClassLoader, vararg hooks: BaseHook) {
        hooks.forEach { hook ->
            try {
                hook.init(classLoader)
            } catch (e: Exception) {
                XposedBridge.log("[ ${BuildConfig.APPLICATION_ID} ] Failed to Init ${hook.name}, ${e.message}")
            }
        }
    }
}