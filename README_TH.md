# WDEV1 Add-on Thai Translator (Android Source)

โครงการต้นแบบแอป Android แบบ Native สำหรับเลือกไฟล์ `.mcaddon`, `.mcpack` หรือ `.zip` จากมือถือ แปลค่าในไฟล์ `.lang` ผ่าน OpenAI API และบันทึกเป็นไฟล์ ZIP กลับไปยังเครื่อง

## สถานะ
- เป็นซอร์สโปรเจกต์ Android Studio ไม่ใช่ APK ที่คอมไพล์แล้ว
- รองรับการแปลไฟล์ `.lang` ใน ZIP
- คงคีย์เดิมและปกป้อง placeholder รูปแบบ `%s`, `%1$s`, `{0}`, `§a`
- ยังไม่รองรับการแปล JSON / JavaScript / รูปภาพ / ฟอนต์
- ยังไม่ทดสอบนำเข้าเกมจริง
- ต้องใช้อินเทอร์เน็ตและ OpenAI API Key ของผู้ใช้

## วิธีสร้าง APK
1. เปิดโฟลเดอร์นี้ด้วย Android Studio
2. รอ Gradle Sync และติดตั้ง Android SDK 35 หากระบบร้องขอ
3. เลือก Build > Build Bundle(s) / APK(s) > Build APK(s)
4. ไฟล์ APK จะอยู่ที่ `app/build/outputs/apk/debug/app-debug.apk`

## ดาวน์โหลด APK จาก GitHub Actions
1. สร้าง repo บน GitHub แล้วอัปโหลดไฟล์ทั้งหมดในโฟลเดอร์นี้ (รวมโฟลเดอร์ `.github`) ไว้ที่ราก repo
2. ไปที่แท็บ **Actions** > **Build Android APK** (จะรันอัตโนมัติเมื่อ push เข้า `main`/`master` หรือกด **Run workflow** เอง)
3. เมื่อ build เสร็จ ดาวน์โหลด APK ได้ที่หัวข้อ **Artifacts** ท้ายหน้ารัน (ไฟล์ `WDEV1_Addon_Thai_Translator_v0.1.0.apk`)
4. ถ้าต้องการลิงก์ดาวน์โหลดถาวร ให้ push tag เช่น `git tag v0.1.0 && git push origin v0.1.0` ระบบจะแนบ APK ไว้ในหน้า **Releases**

APK ที่ได้เป็นรุ่น debug (เซ็นด้วย debug key) ติดตั้งได้ทันทีโดยเปิด "ติดตั้งจากแหล่งที่ไม่รู้จัก"

## ความปลอดภัย
API Key ถูกใช้ในหน่วยความจำระหว่างทำงาน ไม่ได้เขียนลงไฟล์แอดออนหรือเก็บลงฐานข้อมูลโดยแอปต้นแบบนี้ ควรใช้ API Key ส่วนตัวและอย่าแชร์ไฟล์/ภาพหน้าจอที่เปิดเผยคีย์
