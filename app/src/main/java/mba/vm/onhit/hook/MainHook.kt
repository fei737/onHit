package mba.vm.onhit.hook

// 👇 新增的 Android 系统组件导入
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.Tag
import android.util.Log

// 👇 原有的 Xposed 导入及我们新增的 XposedHelpers/XC_MethodHook
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.kyuubiran.ezxhelper.xposed.EzXposed

import mba.vm.onhit.BuildConfig
import mba.vm.onhit.Constant.Companion.NFC_SERVICE_PACKAGE_NAME

class MainHook : IXposedHookLoadPackage {

    // 我们的全局屏蔽开关，仅在 com.android.nfc 进程中生效
    private var isReaderBlocked = false

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 原作者的初始化
        EzXposed.initHandleLoadPackage(lpparam)

        when (lpparam.packageName) {
            NFC_SERVICE_PACKAGE_NAME -> {
                // 1. 优先注入我们手写的“屏蔽器”大招
                injectNfcBlocker(lpparam.classLoader)

                // 2. 继续执行原作者的其他 Hook (互不干扰)
                initHook(lpparam.classLoader, NfcServiceHook, NfcDispatchManagerHook, PackageManagerHook)
            }
            else -> initHook(lpparam.classLoader, PackageManagerHook)
        }
    }

    // ====================================================
    // 🛡️ 新增：NFC 底层物理屏蔽器
    // ====================================================
    private fun injectNfcBlocker(classLoader: ClassLoader) {
        try {
            // 1. 窃听广播：接收 App 发来的屏蔽指令
            XposedHelpers.findAndHookMethod(
                "com.android.nfc.NfcService",
                classLoader,
                "onCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = param.thisObject as Context
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(c: Context?, intent: Intent?) {
                                if (intent?.action == "mba.vm.onhit.BLOCK_NFC_READER") {
                                    isReaderBlocked = intent.getBooleanExtra("block", false)
                                    Log.d("NdefEmulator_Xposed", "🔥 NFC屏蔽状态更新: $isReaderBlocked")
                                }
                            }
                        }
                        val filter = IntentFilter("mba.vm.onhit.BLOCK_NFC_READER")
                        try {
                            // 适配 Android 13+ 的广播注册要求
                            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                        } catch (e: Exception) {
                            context.registerReceiver(receiver, filter)
                        }
                    }
                }
            )

            // 2. 核心拦截：直接切断卡片数据的物理分发
            XposedHelpers.findAndHookMethod(
                "com.android.nfc.NfcDispatcher",
                classLoader,
                "dispatchTag",
                Tag::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isReaderBlocked) {
                            Log.d("NdefEmulator_Xposed", "🛡️ 拦截成功！已强制丢弃物理标签数据")
                            param.result = 1 // 1 代表 DISPATCH_SUCCESS，直接欺骗系统完成流程
                        }
                    }
                }
            )

            // 3. 静音：拦截烦人的系统提示音
            XposedHelpers.findAndHookMethod(
                "com.android.nfc.NfcService",
                classLoader,
                "playSound",
                Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isReaderBlocked) {
                            param.result = null
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("NdefEmulator_Xposed", "屏蔽逻辑注入失败: ${e.message}")
        }
    }

    // ====================================================
    // 原作者的通用加载模块 (保持原样不动)
    // ====================================================
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