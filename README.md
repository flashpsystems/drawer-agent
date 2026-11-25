# Flash Drawer Agent (Android)

APK שמריץ על קופת האנדרואיד את **אותו שירות** שהריץ עד היום `cashdraw_agent.py` על מחשב Windows — פותח מגירת **Prolific PL2303 (USB)** לפי בקשת HTTP מהשרת.

```
[שרת החניה]  --HTTP /open-->  [קופת אנדרואיד + ה-APK]  --USB-->  [מגירת כסף PL2303]
```

**השרת לא צריך שום שינוי** — הוא כבר תומך ב-`drawer_agent_url`. רק מפנים את ה-URL ל-IP של הקופה.

---

## מה ה-APK חושף (זהה ל-cashdraw_agent.py)

מאזין על **פורט 9101** בכל הממשקים:

| בקשה | תגובה |
|---|---|
| `GET /health` | `{"ok": true, "drawer_present": true/false}` |
| `GET /open` | `{"ok": true/false, "message": "..."}` (נוח לבדיקה מהדפדפן) |
| `POST /open` | פותח את המגירה — זה מה שהשרת קורא לו |

פרוטוקול פתיחת המגירה (רצף אתחול PL2303 + פקודות ESC/POS kick) הוא העתק מדויק של `cashdraw.py`.

---

## איך בונים את ה-APK

### אפשרות א' — GitHub Actions (בלי להתקין כלום) ✅ מומלץ
1. פתח repo חדש ב-GitHub והעלה אליו את תוכן התיקייה הזו (או `git push`).
2. לשונית **Actions** → ה-workflow "Build APK" רץ אוטומטית (או הפעל ידנית ב-"Run workflow").
3. בסיום, הורד את ה-APK מ-**Artifacts** → `FlashDrawerAgent-apk` → הקובץ `app-debug.apk`.

### אפשרות ב' — Android Studio
1. `File → Open` ובחר את התיקייה הזו. אם מבקש — אשר יצירת Gradle wrapper / Gradle 8.7.
2. `Build → Build Bundle(s)/APK(s) → Build APK(s)`.
3. ה-APK ייווצר ב-`app/build/outputs/apk/debug/app-debug.apk`.

> זהו APK בחתימת debug — מתאים להתקנה ידנית (sideload) על הקופה. אין צורך ב-Play Store.

---

## התקנה והגדרה על הקופה

1. העבר את `app-debug.apk` לקופה (USB / כבל / Drive) והתקן. אם צריך — אפשר "התקנה ממקורות לא ידועים".
2. חבר את מגירת ה-PL2303 ל-USB של הקופה (דרך מתאם OTG אם צריך).
3. פתח את האפליקציה **Flash Drawer Agent**:
   - אם קופץ חלון הרשאת USB — אשר, וסמן **"Always open … with this app"**.
   - אשר הרשאת התראות ויציאה מאופטימיזציית סוללה (כדי שהשירות לא ייהרג).
4. באפליקציה יופיע ה-URL, למשל: `http://192.168.1.50:9101`. לחץ **Test — open drawer** כדי לוודא שהמגירה נפתחת.
5. בשרת החניה → **Settings → Drawer agent URL** → הזן את ה-URL הזה → **Test** (בודק `/health`).

מאותו רגע, כל "פתח מגירה" בקופה (מזומן/כפתור) ייצא כ-`POST` ל-`http://<ip-הקופה>:9101/open`.

---

## אמינות (חשוב לקופה שרצה 24/7)

- **קבע IP קבוע לקופה** ב-ראוטר (DHCP reservation) — כדי שה-URL לא ישתנה.
- האפליקציה רצה כ-**foreground service**, **מתניעה לבד אחרי אתחול** (BOOT_COMPLETED), וביקשה פטור מאופטימיזציית סוללה.
- אם יצרן הקופה חוסם אתחול-אוטומטי — הוסף את האפליקציה ל-Autostart/Protected apps בהגדרות הקופה.

---

## פרטים טכניים
- יעד: Android 5.0+ (minSdk 21), USB Host API.
- מגירה: VID `0x067B` / PID `0x2303` (Prolific PL2303). לשינוי דגם — ערוך את הקבועים ב-`Pl2303Drawer.kt` ואת `res/xml/device_filter.xml`.
- שרת HTTP: NanoHTTPD, פורט 9101 (זהה לסוכן ה-Python).
