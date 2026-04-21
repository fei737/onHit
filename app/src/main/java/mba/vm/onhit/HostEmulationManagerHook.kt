package mba.vm.onhit.hook

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object HostEmulationManagerHook : BaseHook() {
    override val name: String = "HostEmulationManagerHook"

    override fun init(classLoader: ClassLoader) {
        val className = "com.android.nfc.cardemulation.HostEmulationManager"
        try {
            val clazz = XposedHelpers.findClass(className, classLoader)

            // 🔍 暴力扫描：拦截所有可能处理底层 APDU 数据的入口方法
            clazz.declaredMethods.filter { method ->
                (method.name.contains("HostEmulationData") || method.name == "onData") &&
                        method.parameterTypes.isNotEmpty() &&
                        method.parameterTypes[0] == ByteArray::class.java
            }.forEach { targetMethod ->

                XposedBridge.hookMethod(targetMethod, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // 这就是外面读卡器发进来的最原始的 16进制指令！
                        val apdu = param.args[0] as? ByteArray ?: return
                        val hexApdu = apdu.joinToString("") { "%02X".format(it) }

                        // 用 Error 级别的红色日志打印，方便我们在 Logcat 里一眼看到
                        Log.e("NdefEmulator_Xposed", "🚨 [上帝视角] 截获底层入站 APDU: $hexApdu")

                        // 预留伏笔：以后我们可以在这里直接修改 param.args[0]，
                        // 甚至直接在这里 param.result = 某个字节数组，强行代答，切断发往上层钱包的通路！
                    }
                })
            }
        } catch (e: Exception) {
            Log.e("NdefEmulator_Xposed", "Hook HostEmulationManager 失败: ${e.message}")
        }
    }
}