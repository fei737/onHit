package mba.vm.onhit

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

class NdefEmulatorService : HostApduService() {

    companion object {
        private const val TAG = "NdefEmulator"
        var ndefData: ByteArray? = null
    }

    // 状态机
    private var isCcSelected = false
    private var isNdefSelected = false

    private val CC_FILE = byteArrayOf(
        0x00, 0x0F, // CC 文件总长度
        0x20,       // 协议版本 (2.0)

        // 👇 核心提速修改！将最大读取和响应长度从 0x7F (127) 提升到 0xFF (255)
        0x00, 0xFF.toByte(), // MLe：最大读取长度
        0x00, 0xFF.toByte(), // MLc：最大响应长度

        0x04, 0x06, // T(类型) & L(长度)
        0xE1.toByte(), 0x04, // NDEF 数据文件ID
        0x0F, 0xFF.toByte(), // 允许的 NDEF 文件最大容量
        0x00,       // 读权限
        0xFF.toByte() // 写权限
    )

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return hexToBytes("6A82")

        // 强制大写，防止大小写差异
        val hexCmd = bytesToHex(commandApdu).uppercase()
        Log.d(TAG, "===> 收到指令: $hexCmd")

        // 1. 采用 contains 抓取核心关键词，无视对方的 Le(00) 小尾巴
        if (hexCmd.contains("D2760000850101")) {
            isCcSelected = false
            isNdefSelected = false
            Log.d(TAG, "<=== 回复: 9000 (AID 命中！)")
            return hexToBytes("9000")
        }

        // 2. 选择 CC 配置文件
        if (hexCmd.contains("E103")) {
            isCcSelected = true
            isNdefSelected = false
            Log.d(TAG, "<=== 回复: 9000 (CC文件 命中！)")
            return hexToBytes("9000")
        }

        // 3. 选择 核心 NDEF 文件
        if (hexCmd.contains("E104")) {
            isCcSelected = false
            isNdefSelected = true
            Log.d(TAG, "<=== 回复: 9000 (NDEF文件 命中！)")
            return hexToBytes("9000")
        }

        // 4. 读取数据 (00B0开头)
        if (hexCmd.startsWith("00B0")) {
            val offset = (commandApdu[2].toInt() and 0xFF shl 8) or (commandApdu[3].toInt() and 0xFF)
            val length = commandApdu[4].toInt() and 0xFF

            Log.d(TAG, "===> 对方请求读取内容: 偏移量=$offset, 长度=$length")

            if (isCcSelected) {
                val response = CC_FILE.sliceArray(offset until minOf(offset + length, CC_FILE.size))
                Log.d(TAG, "<=== 发送 CC 数据 (${response.size} 字节)")
                return response + hexToBytes("9000")
            } else if (isNdefSelected) {
                val data = ndefData
                if (data != null) {
                    val dataLength = byteArrayOf((data.size shr 8).toByte(), data.size.toByte())
                    val ndefFile = dataLength + data

                    if (offset < ndefFile.size) {
                        val response = ndefFile.sliceArray(offset until minOf(offset + length, ndefFile.size))
                        Log.d(TAG, "<=== 🚀 发送 核心文件数据 (${response.size} 字节) 🚀")
                        return response + hexToBytes("9000")
                    }
                } else {
                    Log.e(TAG, "❌ 警告：内存中无数据！请检查界面是否正确传值。")
                }
            }
        }

        Log.d(TAG, "<=== 回复: 6A82 (听不懂这句方言)")
        return hexToBytes("6A82")
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "连接断开: $reason")
        isCcSelected = false
        isNdefSelected = false
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }
    private fun hexToBytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}