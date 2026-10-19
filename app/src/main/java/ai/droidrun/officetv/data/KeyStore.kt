package ai.droidrun.officetv.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore as PlatformKeyStore
import javax.crypto.Cipher
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val statsKeyPattern = Regex("^[A-Za-z0-9._~+/=_-]{16,512}$")
fun isValidStatsKey(key: String?): Boolean = key != null && statsKeyPattern.matches(key)

/** Only AES/GCM ciphertext and its IV leave AndroidKeyStore, in app-private, non-backed-up storage. */
class KeyStore(
    context: Context,
    private val alias: String = "officetv_stats",
    prefsName: String = "stats_key",
    private val validate: (String?) -> Boolean = ::isValidStatsKey,
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    val hasValue: Boolean get() = prefs.contains("ciphertext") && prefs.contains("iv")
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()

    @Synchronized fun store(value: String) {
        require(validate(value))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("ciphertext", encode(encrypted))
            .putString("iv", encode(cipher.iv)).commit())
        revision.value++
    }

    @Synchronized fun read(): String? {
        return try {
            val encrypted = prefs.getString("ciphertext", null)
            val iv = prefs.getString("iv", null)
            if (encrypted == null || iv == null) {
                if (encrypted != null || iv != null) clear()
                return null
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, decode(iv)))
            String(cipher.doFinal(decode(encrypted)), Charsets.UTF_8).let {
                if (validate(it)) it else { clear(); null }
            }
        } catch (_: AEADBadTagException) {
            clear()
            null
        } catch (_: KeyPermanentlyInvalidatedException) {
            clear()
            null
        } catch (_: Exception) {
            null // Transient keystore/storage failures must not destroy the provisioned value.
        }
    }

    @Synchronized fun clear() {
        prefs.edit().clear().apply()
        revision.value++
    }

    private fun encryptionKey(): SecretKey {
        val store = PlatformKeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
