package mba.vm.onhit

import android.app.AlertDialog
import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.view.View
import android.widget.*
import androidx.documentfile.provider.DocumentFile
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

object NdefGeneratorDialog {

    fun showDialog(context: Context, targetDir: DocumentFile?, onSuccess: () -> Unit) {
        if (targetDir == null) {
            Toast.makeText(context, "请先在主页设置存储目录！", Toast.LENGTH_SHORT).show()
            return
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        // --- 核心修复：为单选按钮生成唯一 ID ---
        val alipayId = View.generateViewId()
        val customId = View.generateViewId()

        val radioGroup = RadioGroup(context).apply { 
            orientation = LinearLayout.HORIZONTAL 
            setPadding(0, 0, 0, 20)
        }
        
        val radioAlipay = RadioButton(context).apply { 
            id = alipayId
            text = "支付宝"
            isChecked = true 
        }
        val radioCustom = RadioButton(context).apply { 
            id = customId
            text = "自定义" 
        }
        
        radioGroup.addView(radioAlipay)
        radioGroup.addView(radioCustom)

        val nameInput = EditText(context).apply { hint = "💾 保存文件名 (选填)" }
        val urlInput = EditText(context).apply { hint = "🌐 目标链接 (URL)" }
        val aarInput = EditText(context).apply { 
            hint = "📦 强制唤醒包名 (AAR)"
            visibility = View.GONE 
        }

        // --- 修复后的监听逻辑 ---
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == customId) {
                aarInput.visibility = View.VISIBLE
            } else {
                aarInput.visibility = View.GONE
                aarInput.text.clear()
            }
        }

        layout.addView(radioGroup)
        layout.addView(nameInput)
        layout.addView(urlInput)
        layout.addView(aarInput)

        AlertDialog.Builder(context)
            .setTitle("生成 .ndef 文件")
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val rawUrl = urlInput.text.toString().trim()
                if (rawUrl.isEmpty()) {
                    Toast.makeText(context, "链接不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val isAlipay = radioAlipay.isChecked
                val aarStr = aarInput.text.toString().trim()
                var fileName = nameInput.text.toString().trim()

                try {
                    val bytes = if (isAlipay) generateAlipayBytes(rawUrl) else generateCustomBytes(rawUrl, aarStr)
                    
                    if (fileName.isEmpty()) {
                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                        fileName = "${if (isAlipay) "支付宝" else "自定义"}_${timeStamp}.ndef"
                    } else if (!fileName.lowercase().endsWith(".ndef")) {
                        fileName += ".ndef"
                    }

                    val newFile = targetDir.createFile("application/octet-stream", fileName)
                    if (newFile != null) {
                        context.contentResolver.openOutputStream(newFile.uri)?.use { it.write(bytes) }
                        Toast.makeText(context, "✅ 生成成功: $fileName", Toast.LENGTH_LONG).show()
                        onSuccess() 
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "❌ 错误: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun generateAlipayBytes(rawUrl: String): ByteArray {
        val sep = if (rawUrl.contains("?")) "&" else "?"
        val appendedUrl = rawUrl + sep + "noT=abxcxvunnt3bb"
        val deepLink = "alipay://nfc/app?id=10000007&actionType=route&codeContent=" + URLEncoder.encode(appendedUrl, "UTF-8")
        val finalUriStr = "https://render.alipay.com/p/s/ulink/dc0?s=dc&scheme=" + URLEncoder.encode(deepLink, "UTF-8")
        return NdefMessage(arrayOf(
            NdefRecord.createUri(finalUriStr),
            NdefRecord.createApplicationRecord("com.eg.android.AlipayGphone")
        )).toByteArray()
    }

    private fun generateCustomBytes(rawUrl: String, aar: String): ByteArray {
        val records = mutableListOf(NdefRecord.createUri(rawUrl))
        if (aar.isNotBlank()) records.add(NdefRecord.createApplicationRecord(aar))
        return NdefMessage(records.toTypedArray()).toByteArray()
    }
}