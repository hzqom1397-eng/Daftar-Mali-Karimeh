package ir.karimeh.finance

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Build
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Monthly, user-selected-folder backup for Karimeh Finance. */
object KarimehBackup {
    const val PREF_TREE_URI = "karimeh_backup_tree_uri"
    const val ACTION_MONTHLY = "ir.karimeh.finance.action.MONTHLY_BACKUP"
    const val ACTION_BOOT = "android.intent.action.BOOT_COMPLETED"
    private const val PREFS_NAME = "karimeh_finance"
    private const val PREF_LAST_AUTO_MONTH = "karimeh_last_auto_backup_month"

    fun schedule(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(PREF_TREE_URI, null).isNullOrBlank()) return

        val next = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, 1)
        }
        val intent = Intent(context, KarimehBackupReceiver::class.java).setAction(ACTION_MONTHLY)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pending = PendingIntent.getBroadcast(context, 7413, intent, flags)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
    }

    fun createBackup(context: Context, automatic: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val treeText = prefs.getString(PREF_TREE_URI, null) ?: return false
        val treeUri = Uri.parse(treeText)
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        if (automatic && prefs.getString(PREF_LAST_AUTO_MONTH, "") == monthKey) return true

        return try {
            val root = JSONObject()
                .put("backupVersion", 1)
                .put("createdAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                .put("transactions", prefs.getString("transactions", "[]"))
                .put("inventory_products", prefs.getString("inventory_products", "[]"))
                .put("karimeh_customers", prefs.getString("karimeh_customers", "[]"))
            val allPrefs = JSONObject()
            prefs.all.forEach { (key, value) ->
                when (value) {
                    is Set<*> -> allPrefs.put(key, org.json.JSONArray(value.filterIsInstance<String>()))
                    null -> Unit
                    else -> allPrefs.put(key, value)
                }
            }
            root.put("preferences", allPrefs)

            val treeDocument = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri)
            )
            val filename = "Karimeh-Backup-${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())}.json"
            val fileUri = DocumentsContract.createDocument(
                context.contentResolver, treeDocument, "application/json", filename
            ) ?: return false
            context.contentResolver.openOutputStream(fileUri, "w")?.use { stream ->
                stream.write(root.toString(2).toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: return false

            if (automatic) prefs.edit().putString(PREF_LAST_AUTO_MONTH, monthKey).apply()
            prefs.edit().putString("karimeh_last_backup_time", root.getString("createdAt")).apply()
            true
        } catch (_: Exception) {
            false
        }
    }
}

class KarimehBackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            KarimehBackup.ACTION_BOOT -> KarimehBackup.schedule(context)
            KarimehBackup.ACTION_MONTHLY -> {
                val saved = KarimehBackup.createBackup(context, true)
                if (saved) {
                    KarimehBackup.schedule(context)
                } else {
                    // Retry tomorrow if storage access is temporarily unavailable.
                    val retryIntent = Intent(context, KarimehBackupReceiver::class.java).setAction(KarimehBackup.ACTION_MONTHLY)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
                    val pending = PendingIntent.getBroadcast(context, 7413, retryIntent, flags)
                    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 24L * 60L * 60L * 1000L, pending)
                }
            }
        }
    }
}
