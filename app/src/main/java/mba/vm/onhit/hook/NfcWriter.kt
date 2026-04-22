package mba.vm.onhit.hook

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.util.Log
import java.io.IOException

object NfcWriter {
    private const val TAG = "NfcWriter_Debug"

    fun writeNdefBytesToTag(tag: Tag, ndefBytes: ByteArray): Boolean {
        Log.w(TAG, "========== ⬇️ 开始烧录诊断 ⬇️ ==========")

        // 1. 打印标签支持的所有物理层技术 (Tech List)
        val techList = tag.techList.joinToString(", ") { it.substringAfterLast('.') }
        Log.w(TAG, "🔍 捕捉到物理标签！支持的技术: [$techList]")
        Log.w(TAG, "📦 准备写入的 NDEF 数据大小: ${ndefBytes.size} 字节")

        try {
            val ndefMessage = NdefMessage(ndefBytes)
            val ndef = Ndef.get(tag)

            if (ndef != null) {
                Log.i(TAG, "▶️ 标签检测为已格式化的 NDEF，尝试建立连接...")
                try {
                    ndef.connect()
                    Log.i(TAG, "✅ 连接 NDEF 成功！卡片类型: ${ndef.type}")
                    Log.i(TAG, "📊 卡片最大容量: ${ndef.maxSize} 字节 | 是否可写: ${ndef.isWritable}")

                    if (!ndef.isWritable) {
                        Log.e(TAG, "❌ 致命错误: 卡片被硬件或密码锁定 (只读)，无法写入！")
                        return false
                    }

                    if (ndef.maxSize < ndefBytes.size) {
                        Log.e(TAG, "❌ 致命错误: 空间不足！你的数据需要 ${ndefBytes.size} 字节，但卡片最多只能装 ${ndef.maxSize} 字节。")
                        return false
                    }

                    ndef.writeNdefMessage(ndefMessage)
                    Log.i(TAG, "🎉 烧录成功: NDEF 数据已完美覆盖写入！")
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "❌ NDEF 写入阶段发生异常: ${e.javaClass.simpleName} - ${e.message}", e)
                    return false
                } finally {
                    try { ndef.close() } catch (e: Exception) { Log.e(TAG, "关闭 NDEF 连接时发生异常", e) }
                }
            }

            // 如果标签不是 NDEF，说明它可能是一张出厂纯白卡，尝试格式化
            Log.w(TAG, "⚠️ 标签未格式化为标准 NDEF，尝试挂载为 NdefFormatable 进行格式化...")
            val ndefFormatable = NdefFormatable.get(tag)

            if (ndefFormatable != null) {
                try {
                    ndefFormatable.connect()
                    Log.i(TAG, "✅ 连接 NdefFormatable 成功，开始强行格式化并写入数据...")
                    ndefFormatable.format(ndefMessage)
                    Log.i(TAG, "🎉 烧录成功: 出厂白卡已成功格式化并写入完毕！")
                    return true
                } catch (e: IOException) {
                    Log.e(TAG, "❌ 格式化失败 (IOException): 可能是写入时手抖卡片移开了，或者卡片质量太差通讯中断。错误信息: ${e.message}")
                    return false
                } catch (e: FormatException) {
                    Log.e(TAG, "❌ 格式化失败 (FormatException): 该卡片的物理扇区可能不支持 NDEF 规范，或者已被写死。错误信息: ${e.message}")
                    return false
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 格式化阶段发生未知异常: ${e.message}", e)
                    return false
                } finally {
                    try { ndefFormatable.close() } catch (e: Exception) {}
                }
            }

            // 走到这里，说明这张卡根本不支持 NDEF
            Log.e(TAG, "❌ 彻底失败: 该标签既不是 NDEF，也不支持格式化。这绝对是一张加密的门禁卡、公交卡或不支持的低端芯片！")
            return false

        } catch (e: Exception) {
            Log.e(TAG, "❌ 严重崩溃: NdefMessage 构造或解析彻底失败 (你的字节数据可能有误): ${e.message}", e)
            return false
        } finally {
            Log.w(TAG, "========== ⬆️ 烧录诊断结束 ⬆️ ==========")
        }
    }
}