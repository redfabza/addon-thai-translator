package com.wdev1.addonthai

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowInsets
import android.widget.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val PICK = 10
    private val SAVE = 11
    private var sourceUri: Uri? = null
    @Volatile private var translatedBytes: ByteArray? = null
    @Volatile private var skipped = 0
    private var sourceName = "addon.zip"
    private lateinit var status: TextView
    private lateinit var keyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var chooseButton: Button
    private lateinit var translateButton: Button
    private lateinit var saveButton: Button

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFFF4F6FB.toInt())
        }
        val title = TextView(this).apply {
            text = "WDEV1 Add-on Thai Translator"
            textSize = 23f; setTextColor(0xFF18243A.toInt()); gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "แปลแอดออนมายคราฟเป็นภาษาไทยบนมือถือ"
            textSize = 14f; gravity = Gravity.CENTER; setPadding(0, 8, 0, 22)
        }
        keyInput = EditText(this).apply { hint = "ใส่ OpenAI API Key"; inputType = 129 }
        modelInput = EditText(this).apply { hint = "โมเดล (ค่าเริ่มต้น gpt-5.4-mini)"; setText("gpt-5.4-mini") }
        chooseButton = Button(this).apply { text = "1. เลือกไฟล์แอดออน" }
        translateButton = Button(this).apply { text = "2. เริ่มแปลเป็นภาษาไทย"; isEnabled = false }
        saveButton = Button(this).apply { text = "3. บันทึกไฟล์แอดออน"; isEnabled = false }
        status = TextView(this).apply {
            text = "เลือกไฟล์ .mcaddon, .mcpack หรือ .zip เพื่อเริ่มต้น"
            textSize = 14f; setTextColor(0xFF34445C.toInt()); setPadding(4, 18, 4, 18)
        }
        val note = TextView(this).apply {
            text = "หมายเหตุ: รุ่นทดลองนี้แปลไฟล์ .lang ภายใน ZIP โดยคงคีย์และตัวแปรเดิมไว้ ต้องใช้อินเทอร์เน็ตและ API Key ของคุณเอง"
            textSize = 12f; setTextColor(0xFF667085.toInt()); setPadding(4, 12, 4, 0)
        }
        root.addView(title); root.addView(subtitle); root.addView(keyInput)
        root.addView(modelInput); root.addView(chooseButton); root.addView(translateButton)
        root.addView(saveButton); root.addView(status); root.addView(note)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFFF4F6FB.toInt())
            addView(root)
        }
        // Android 15 (targetSdk 35) บังคับ edge-to-edge: เว้นขอบให้พ้นแถบสถานะ/แถบนำทาง
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                top = bars.top; bottom = bars.bottom
            } else {
                top = insets.systemWindowInsetTop
                bottom = insets.systemWindowInsetBottom
            }
            v.setPadding(0, top, 0, bottom)
            insets
        }
        setContentView(scroll)

        chooseButton.setOnClickListener {
            // ไม่จำกัด MIME เพราะไฟล์ .mcaddon/.mcpack ถูกรายงาน MIME ไม่เหมือนกันในแต่ละเครื่อง
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            }
            startActivityForResult(i, PICK)
        }
        translateButton.setOnClickListener { translate() }
        saveButton.setOnClickListener {
            // ใช้ octet-stream เพื่อไม่ให้ระบบต่อ .zip ท้ายชื่อไฟล์ .mcaddon/.mcpack
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, outputName(sourceName))
            }
            startActivityForResult(i, SAVE)
        }
    }

    private fun outputName(name: String): String {
        val ext = name.substringAfterLast('.', "zip")
        return "TH_${name.substringBeforeLast('.', name)}.$ext"
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        if (requestCode == PICK) {
            sourceUri = uri
            sourceName = queryName(uri) ?: "addon.zip"
            status.text = "เลือกแล้ว: $sourceName"
            translateButton.isEnabled = true
            translatedBytes = null; saveButton.isEnabled = false
        } else if (requestCode == SAVE) {
            val bytes = translatedBytes ?: return
            try {
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                status.text = "บันทึกไฟล์แล้ว สามารถเปิดไฟล์เพื่อนำเข้า Minecraft ได้"
            } catch (e: Exception) {
                status.text = "บันทึกไฟล์ไม่สำเร็จ: ${e.message}"
            }
        }
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return null
    }

    private fun translate() {
        val key = keyInput.text.toString().trim()
        if (key.isBlank()) { status.text = "กรุณาใส่ OpenAI API Key ก่อน"; return }
        val uri = sourceUri ?: return
        val model = modelInput.text.toString().trim().ifBlank { "gpt-5.4-mini" }
        translateButton.isEnabled = false
        saveButton.isEnabled = false
        skipped = 0
        status.text = "กำลังอ่านและตรวจสอบไฟล์..."
        thread {
            try {
                val input = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IOException("เปิดไฟล์ที่เลือกไม่ได้")
                val output = processZip(input, key, model) { msg -> runOnUiThread { status.text = msg } }
                translatedBytes = output
                val warn = if (skipped > 0) "\n(ข้ามข้อความที่ AI ตอบไม่สมบูรณ์ ${skipped} รายการ คงเป็นต้นฉบับ)" else ""
                runOnUiThread {
                    status.text = "แปลเสร็จแล้ว ${output.size / 1024} KB$warn\nกดบันทึกไฟล์เพื่อส่งออก"
                    saveButton.isEnabled = true; translateButton.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "เกิดข้อผิดพลาด: ${e.message}"; translateButton.isEnabled = true }
            }
        }
    }

    private fun processZip(bytes: ByteArray, key: String, model: String, progress: (String) -> Unit): ByteArray {
        val outBytes = ByteArrayOutputStream()
        var found = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
            ZipOutputStream(outBytes).use { output ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val name = entry.name
                    val data = if (entry.isDirectory) ByteArray(0) else input.readBytes()
                    val newData = if (!entry.isDirectory && name.endsWith(".lang", true)) {
                        found++
                        translateLang(name, data, key, model, progress)
                    } else data
                    val newEntry = ZipEntry(name)
                    if (entry.time != -1L) newEntry.time = entry.time
                    output.putNextEntry(newEntry)
                    if (!entry.isDirectory) output.write(newData)
                    output.closeEntry()
                    input.closeEntry()
                }
            }
        }
        if (found == 0) throw IllegalArgumentException("ไม่พบไฟล์ .lang ในแอดออน")
        return outBytes.toByteArray()
    }

    private fun translateLang(fileName: String, data: ByteArray, key: String, model: String, progress: (String) -> Unit): ByteArray {
        val text = data.toString(Charsets.UTF_8)
        val lines = text.split("\n")
        val parsed = mutableListOf<Pair<Int, String>>()
        val regex = Regex("""^(\s*[^#!\s][^=]*?=)(.*)$""")
        lines.forEachIndexed { index, line ->
            val m = regex.find(line.trimEnd('\r'))
            if (m != null && m.groupValues[2].isNotBlank()) {
                val value = splitComment(m.groupValues[2]).first
                if (value.any { it in 'A'..'Z' || it in 'a'..'z' }) {
                    parsed.add(index to value)
                }
            }
        }
        val translations = mutableMapOf<Int, String>()
        val batches = parsed.chunked(15)
        batches.forEachIndexed { bi, batch ->
            progress("กำลังแปล ${fileName.substringAfterLast('/')} (ชุด ${bi + 1}/${batches.size})...")
            val prot = batch.map { protect(it.second) }
            val arr = JSONArray()
            prot.forEachIndexed { idx, p -> arr.put(JSONObject().put("id", idx).put("text", p.first)) }
            val rows = requestBatch(arr, key, model)
            val byId = HashMap<Int, String>()
            for (r in 0 until rows.length()) {
                val o = rows.optJSONObject(r) ?: continue
                if (o.has("id") && o.has("text")) byId[o.optInt("id", -1)] = o.optString("text")
            }
            batch.forEachIndexed { idx, pair ->
                val restored = byId[idx]?.let { restore(it, prot[idx].second) }
                if (restored != null) {
                    translations[pair.first] = restored.replace("\r", " ").replace("\n", " ")
                } else {
                    skipped++ // คงข้อความต้นฉบับไว้แทนการล้มทั้งงาน
                }
            }
        }
        val result = lines.toMutableList()
        translations.forEach { (index, value) ->
            val line = result[index]
            val cr = if (line.endsWith("\r")) "\r" else ""
            val body = line.trimEnd('\r')
            val pos = body.indexOf('=')
            if (pos >= 0) {
                val suffix = splitComment(body.substring(pos + 1)).second
                result[index] = body.substring(0, pos + 1) + value + suffix + cr
            }
        }
        return result.joinToString("\n").toByteArray(Charsets.UTF_8)
    }

    /** แยกคอมเมนต์ท้ายบรรทัดแบบ Bedrock (`key=value<TAB>#comment`) ออกจากค่าที่จะแปล */
    private fun splitComment(raw: String): Pair<String, String> {
        val ci = raw.indexOf("\t#")
        return if (ci >= 0) raw.substring(0, ci) to raw.substring(ci) else raw to ""
    }

    private fun requestBatch(arr: JSONArray, key: String, model: String): JSONArray {
        val sys = """คุณเป็นนักแปล Minecraft Bedrock เป็นภาษาไทยธรรมชาติ ใช้คำว่า มายคราฟ, ดิสคอร์ด, เควสต์, วอยด์ เรียงชื่อไอเท็มให้เป็นไทย ห้ามแปลคีย์ ห้ามเปลี่ยนข้อความในโทเคน ⟦PHnX⟧ ตอบ JSON รูปแบบ {"translations":[{"id":0,"text":"..."}]} เท่านั้น"""
        val body = JSONObject().put("model", model).put("messages", JSONArray()
            .put(JSONObject().put("role", "system").put("content", sys))
            .put(JSONObject().put("role", "user").put("content", "แปลข้อความต่อไปนี้: $arr")))
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("max_completion_tokens", 8000)
        var last: Exception? = null
        for (attempt in 0 until 3) {
            if (attempt > 0) Thread.sleep(2000L * attempt)
            val conn = URL("https://api.openai.com/v1/chat/completions").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"; conn.connectTimeout = 20000; conn.readTimeout = 180000
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code == 429 || code >= 500) throw IOException("API $code: $response")
                if (code !in 200..299) throw RuntimeException("API $code: $response")
                val content = JSONObject(response).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                return JSONObject(content).getJSONArray("translations")
            } catch (e: IOException) {
                last = e
            } catch (e: JSONException) {
                last = e
            } finally {
                conn.disconnect()
            }
        }
        throw RuntimeException("เรียก API ไม่สำเร็จ: ${last?.message}")
    }

    private fun protect(s: String): Pair<String, List<String>> {
        val tokens = mutableListOf<String>()
        val re = Regex("""%(?:\d+\$)?[sdif]|%[A-Za-z]|\{[A-Za-z0-9_.:-]+}|§[0-9A-FK-ORa-fk-or]|\\n|\\r|\\t""")
        val out = re.replace(s) { m ->
            val marker = "⟦PH${tokens.size}X⟧"; tokens.add(m.value); marker
        }
        return out to tokens
    }

    private fun restore(s: String, tokens: List<String>): String? {
        var out = s
        tokens.forEachIndexed { i, token ->
            val marker = "⟦PH${i}X⟧"
            if (!out.contains(marker)) return null
            out = out.replace(marker, token)
        }
        return if (Regex("""⟦PH\d+X⟧""").containsMatchIn(out)) null else out
    }
}
