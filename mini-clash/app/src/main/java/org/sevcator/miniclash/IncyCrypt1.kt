package org.sevcator.miniclash

import android.util.Base64
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Decoder for INCY's public crypt1 link format. The key is intentionally public obfuscation material. */
object IncyCrypt1 {
    private const val PREFIX = "incy://crypt1/"
    // Derived from the MIT-licensed @incy/link-encoder v1.3.0 public key material.
    private const val KEY_HEX = "f6d40ea0c8a8899d7c682d09ba0d4165dfe2b3dd45e6bb3e25cb233cf00c2462"

    fun decode(link: String): Pair<String, String> {
        require(link.startsWith(PREFIX)) { "Not an INCY crypt1 link" }
        val encoded = link.removePrefix(PREFIX).trimEnd('/')
        val wire = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP)
        require(wire.size >= 29) { "Invalid INCY crypt1 payload" }
        val key = KEY_HEX.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, wire.copyOfRange(0, 12)))
        val data = JSONObject(String(cipher.doFinal(wire.copyOfRange(12, wire.size)), Charsets.UTF_8))
        return data.getString("url") to data.optString("n")
    }
}
