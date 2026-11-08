package dev.voice.offline.core.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** SQLite notes encrypted at the application layer with Android Keystore AES-GCM. */
class PrivateNoteStore(context: Context) : SQLiteOpenHelper(context, "notes.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, iv BLOB NOT NULL, ciphertext BLOB NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(text: String) {
        require(text.isNotBlank() && text.length <= 2000)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        val values = ContentValues().apply {
            put("created_at", System.currentTimeMillis())
            put("iv", cipher.iv)
            put("ciphertext", encrypted)
        }
        check(writableDatabase.insertOrThrow("notes", null, values) > 0)
    }

    fun listRecent(limit: Int = 20): List<String> {
        val notes = mutableListOf<String>()
        readableDatabase.query("notes", arrayOf("iv", "ciphertext"), null, null, null, null,
            "created_at DESC", limit.coerceIn(1, 100).toString()).use { cursor ->
            while (cursor.moveToNext()) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, cursor.getBlob(0)))
                notes += String(cipher.doFinal(cursor.getBlob(1)), Charsets.UTF_8)
            }
        }
        return notes
    }

    private fun key(): javax.crypto.SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("voice-notes-aes-v1", null) as? javax.crypto.SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder("voice-notes-aes-v1",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }
}
