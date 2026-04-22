package mba.vm.onhit

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

class NdefEmulatorService : HostApduService() {

    companion object {
        private const val TAG = "NdefEmulator_Service"
        @Volatile
        var ndefData: ByteArray? = null
    }

    private var isCcSelected = false
    private var isNdefSelected = false

    // 标准 NFC Forum Type 4 Tag CC 文件 (15 字节)
    private val CC_FILE = byteArrayOf(
        0x00, 0x0F,          // CC 文件总长度
        0x20,                // 协议版本 (2.0)

        // 🚨 突破极限：将 MLe 和 MLc 改为 04 00 (告诉读卡器我支持 1024 字节单次传输！)
        0x04, 0x00,          // MLe：最大读取长度 (1024)
        0x04, 0x00,          // MLc：最大响应长度 (1024)

        0x04, 0x06,          // T(类型) & L(长度)
        0xE1.toByte(), 0x04, // NDEF 数据文件ID
        0x0F, 0xFF.toByte(), // 允许的最大容量
        0x00,                // 读权限
        0xFF.toByte()        // 写权限
    )

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < 2) return hexToBytes("6A82")

        try {
            val hexCmd = bytesToHex(commandApdu).uppercase()

            if (hexCmd.startsWith("00A40400") && hexCmd.contains("D2760000850101")) {
                isCcSelected = false
                isNdefSelected = false
                return hexToBytes("9000")
            }

            if (hexCmd.startsWith("00A4000C02E103")) {
                isCcSelected = true
                isNdefSelected = false
                return hexToBytes("9000")
            }

            if (hexCmd.startsWith("00A4000C02E104")) {
                isCcSelected = false
                isNdefSelected = true
                return hexToBytes("9000")
            }

            if (hexCmd.startsWith("00B0")) {
                if (commandApdu.size < 4) return hexToBytes("6700")

                val offset = (commandApdu[2].toInt() and 0xFF shl 8) or (commandApdu[3].toInt() and 0xFF)
                val rawLength = if (commandApdu.size >= 5) commandApdu[4].toInt() and 0xFF else 0
                val length = if (rawLength == 0) 256 else rawLength

                if (isCcSelected) {
                    if (offset >= CC_FILE.size) return hexToBytes("6A82")
                    val readLen = minOf(length, CC_FILE.size - offset)
                    val response = ByteArray(readLen + 2)
                    System.arraycopy(CC_FILE, offset, response, 0, readLen)
                    response[readLen] = 0x90.toByte()
                    response[readLen + 1] = 0x00.toByte()
                    return response
                }

                if (isNdefSelected) {
                    val data = ndefData ?: return hexToBytes("6A82")
                    val nlen = data.size
                    val totalSize = nlen + 2
                    if (offset >= totalSize) return hexToBytes("6A82")

                    val readLen = minOf(length, totalSize - offset)
                    val response = ByteArray(readLen + 2)

                    for (i in 0 until readLen) {
                        val currentPos = offset + i
                        response[i] = if (currentPos == 0) {
                            (nlen shr 8).toByte()
                        } else if (currentPos == 1) {
                            (nlen and 0xFF).toByte()
                        } else {
                            data[currentPos - 2]
                        }
                    }
                    response[readLen] = 0x90.toByte()
                    response[readLen + 1] = 0x00.toByte()
                    return response
                }
            }
            return hexToBytes("6A82")
        } catch (e: Exception) {
            Log.e(TAG, "APDU 异常", e)
            return hexToBytes("6F00")
        }
    }

    override fun onDeactivated(reason: Int) {
        isCcSelected = false
        isNdefSelected = false
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }
    private fun hexToBytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}