package com.example.nfcemulator

import android.content.Context
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.io.ByteArrayOutputStream

class NdefHostApduService : HostApduService() {

    private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
    private val STATUS_FAILED = byteArrayOf(0x6A.toByte(), 0x82.toByte())

    // Selector de Aplicación NDEF (AID: D2760000850101)
    private val APDU_SELECT_NDEF_APP = byteArrayOf(
        0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(), 0x07.toByte(),
        0xD2.toByte(), 0x76.toByte(), 0x00.toByte(), 0x00.toByte(), 0x85.toByte(), 0x01.toByte(), 0x01.toByte()
    )

    // Selector de Archivo Capability Container (CC File)
    private val APDU_SELECT_CC_FILE = byteArrayOf(
        0x00.toByte(), 0xA4.toByte(), 0x00.toByte(), 0x0C.toByte(), 0x02.toByte(), 0xE1.toByte(), 0x03.toByte()
    )

    // Selector de Archivo NDEF Data
    private val APDU_SELECT_NDEF_FILE = byteArrayOf(
        0x00.toByte(), 0xA4.toByte(), 0x00.toByte(), 0x0C.toByte(), 0x02.toByte(), 0xE1.toByte(), 0x04.toByte()
    )

    // Definición de respuesta CC (Capability Container File Data)
    private val CC_FILE_BYTES = byteArrayOf(
        0x00, 0x0F, // CCLEN: 15 bytes
        0x20,       // Mapping Version 2.0
        0x00, 0x3B, // MLe: Tamaño máximo de lectura
        0x00, 0x34, // MLc: Tamaño máximo de escritura
        0x04,       // NDEF File Control TLV Tag
        0x06,       // Tag Length: 6 bytes
        0xE1.toByte(), 0x04.toByte(), // File ID NDEF
        0x00, 0xFF.toByte(), // Max NDEF size
        0x00,       // Read Access: Always
        0xFF.toByte()// Write Access: Never
    )

    private var selectedFile = SelectedFile.NONE

    private enum class SelectedFile {
        NONE, CC_FILE, NDEF_FILE
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return STATUS_FAILED

        // 1. Petición de Selección de Aplicación NDEF
        if (commandApdu.contentEquals(APDU_SELECT_NDEF_APP)) {
            selectedFile = SelectedFile.NONE
            return STATUS_SUCCESS
        }

        // 2. Selección de Archivo CC o NDEF
        if (commandApdu.contentEquals(APDU_SELECT_CC_FILE)) {
            selectedFile = SelectedFile.CC_FILE
            return STATUS_SUCCESS
        }

        if (commandApdu.contentEquals(APDU_SELECT_NDEF_FILE)) {
            selectedFile = SelectedFile.NDEF_FILE
            return STATUS_SUCCESS
        }

        // 3. Petición de Lectura (ReadBinary)
        if (commandApdu.size >= 5 && commandApdu[1] == 0xB0.toByte()) {
            val offset = ((commandApdu[2].toInt() and 0xFF) shl 8) or (commandApdu[3].toInt() and 0xFF)
            val length = commandApdu[4].toInt() and 0xFF

            val dataToReturn = when (selectedFile) {
                SelectedFile.CC_FILE -> CC_FILE_BYTES
                SelectedFile.NDEF_FILE -> getFullNdefFileData()
                SelectedFile.NONE -> return STATUS_FAILED
            }

            if (offset < dataToReturn.size) {
                val end = minOf(offset + length, dataToReturn.size)
                return dataToReturn.copyOfRange(offset, end) + STATUS_SUCCESS
            }
            return STATUS_FAILED
        }

        return STATUS_SUCCESS
    }

    override fun onDeactivated(reason: Int) {
        selectedFile = SelectedFile.NONE
    }

    private fun getFullNdefFileData(): ByteArray {
        val prefs = getSharedPreferences("NfcPrefs", Context.MODE_PRIVATE)
        val activeUrl = prefs.getString("ACTIVE_URL", "https://google.com") ?: "https://google.com"
        val ndefMessage = createNdefMessage(activeUrl)

        val stream = ByteArrayOutputStream()
        // Los dos primeros bytes deben indicar el tamaño del mensaje NDEF (Big-Endian)
        stream.write((ndefMessage.size shr 8) and 0xFF)
        stream.write(ndefMessage.size and 0xFF)
        stream.write(ndefMessage)

        return stream.toByteArray()
    }

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

        val stream = ByteArrayOutputStream()
        stream.write(0xD1) // MB=1, ME=1, CF=0, SR=1, IL=0, TNF=1 (Well-Known)
        stream.write(0x01) // Type Length: 1 byte
        stream.write(payload.size) // Payload Length
        stream.write('U'.code) // Type 'U' (URI)
        stream.write(payload)

        return stream.toByteArray()
    }
}

