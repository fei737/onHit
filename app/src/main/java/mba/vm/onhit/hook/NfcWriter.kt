package mba.vm.onhit.hook

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.util.Log

object NfcWriter {
     /**
     * 将你选中的文件数据 (原始 bytes) 直接烧录到实体标签中
     */
    fun writeNdefBytesToTag(tag: Tag, rawBytes: ByteArray): Boolean {
        return try {
            // 将原始字节数组还原成 NDEF 消息对象
            val message = NdefMessage(rawBytes)

            // 尝试连接标准 NDEF 标签
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    Log.e("NfcWriter", "标签是被锁定的只读卡！")
                    ndef.close()
                    return false
                }
                if (ndef.maxSize < message.toByteArray().size) {
                    Log.e("NfcWriter", "标签容量太小，装不下这个文件的数据！")
                    ndef.close()
                    return false
                }
                ndef.writeNdefMessage(message)
                ndef.close()
                return true
            }
            // 如果是一张全新的、没被格式化过的白卡 (比如新买的 NTAG215)
            else {
                val formatable = NdefFormatable.get(tag)
                if (formatable != null) {
                    formatable.connect()
                    formatable.format(message) // 强行格式化并烧录
                    formatable.close()
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e("NfcWriter", "烧录失败: ${e.message}")
            false
        }
    }
}