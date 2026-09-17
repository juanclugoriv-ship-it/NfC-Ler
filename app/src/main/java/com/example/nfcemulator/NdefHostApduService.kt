package com.example.nfcemulator

import android.content.Context
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.io.ByteArrayOutputStream

class NdefHostApduService : HostApduService() {

    private val SW_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
    private val SW_FAIL = byteArrayOf(0x6A.toByte(), 0x82.toByte())

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < 4) return SW_FAIL

        val prefs = getSharedPreferences("NfcPrefs", Context.MODE_PRIVATE)
        val activeUrl = prefs.getString("ACTIVE_URL", "https://google.com") ?: "https://google.com"
        val ndefMessageBytes = createNdefMessage(activeUrl)

        if (commandApdu[1] == 0xA4.toByte()) {
            return SW_SUCCESS
        }

        if (commandApdu[1] == 0xB0.toByte()) {
            val offset = ((commandApdu[2].toInt() and 0xFF) shl 8) or (commandApdu[3].toInt() and 0xFF)
            val length = commandApdu[4].toInt() and 0xFF

            val fullFile = ByteArrayOutputStream().apply {
                write((ndefMessageBytes.size shr 8) and 0xFF)
                write(ndefMessageBytes.size and 0xFF)
                write(ndefMessageBytes)
            }.toByteArray()

            return if (offset < fullFile.size) {
                val end = minOf(offset + length, fullFile.size)
                fullFile.copyOfRange(offset, end) + SW_SUCCESS
            } else {
                SW_FAIL
            }
        }

        return SW_SUCCESS
    }

    override fun onDeactivated(reason: Int) {}

    private fun createNdefMessage(url: String): ByteArray {
        var cleanUrl = url
        var prefixByte: Byte = 0x00

        if (cleanUrl.startsWith("https://")) {
            prefixByte = 0x04.toByte()
            cleanUrl = cleanUrl.replace("https://", "")
        } else if (cleanUrl.startsWith("http://")) {
            prefixByte = 0x03.toByte()
            cleanUrl = cleanUrl.replace("http://", "")
        }

        val uriBytes = cleanUrl.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(1 + uriBytes.size)
        payload[0] = prefixByte
        System.arraycopy(uriBytes, 0, payload, 1, uriBytes.size)

        return ByteArrayOutputStream().apply {
            write(0xD1)
            write(0x01)
            write(payload.size)
            write('U'.code)
            write(payload)
        }.toByteArray()
    }
}

