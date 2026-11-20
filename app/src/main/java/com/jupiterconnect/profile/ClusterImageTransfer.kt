package com.jupiterconnect.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Rider/caller photo transfer to the cluster, recovered from TVS Connect 8.14.3
 * (com.tvs.ble.feature.utils.BLEImageTransferUtils).
 *
 *   init   : 5A 53 <picNo> <totalFrames:u16be> <frameNo:u16be> <state> <picType>
 *            <picName (10 bytes)> <zero pad> FF         -> 20 bytes
 *   request: cluster -> phone, 5A 54, picNo at [2], frameNo at [5..6] (BE)
 *   chunk  : 5A 55 <frameNo:u16be> <173 bytes> 00 FF    -> 179 bytes (JUPITER_U566)
 *
 * picNo 4 = rider profile photo, 5 = incoming caller photo.
 * state 1 = transferring, 3 = completed, 16 = delete.
 */
object ClusterImageTransfer {

    private const val PIC_PROFILE = 4
    private const val PIC_CALLER = 5
    private const val CHUNK = 173 // JUPITER_U566, from getSubByteArraySize()
    private const val IMAGE_SIZE = 80 // 80x80 for Jupiter family
    private const val FILE_NAME = "rider_profile.jpg"

    @Volatile
    private var chunks: List<ByteArray> = emptyList()
    @Volatile
    private var picNo: Int = PIC_PROFILE

    val hasImage: Boolean get() = chunks.isNotEmpty()

    fun clear() { chunks = emptyList() }

    /** Cache raw JPEG bytes and split them into 173-byte wire chunks. */
    fun loadBytes(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        chunks = bytes.asList().chunked(CHUNK).map { it.toByteArray() }
        picNo = PIC_PROFILE
        return true
    }

    /** Decode, resize to 80x80, JPEG-compress and cache the chunks. */
    fun prepare(context: Context, uri: Uri): Boolean {
        val source = runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return false
        val square = runCatching {
            Bitmap.createScaledBitmap(source, IMAGE_SIZE, IMAGE_SIZE, true)
        }.getOrNull() ?: return false
        val bytes = ByteArrayOutputStream().use { out ->
            square.compress(Bitmap.CompressFormat.JPEG, 50, out)
            out.toByteArray()
        }
        // Persist so the photo survives a process restart.
        runCatching {
            File(context.filesDir, FILE_NAME).writeBytes(bytes)
        }
        return loadBytes(bytes)
    }

    /** Restore a previously saved photo on app start. */
    fun restore(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return
        loadBytes(bytes)
    }

    /** 0x5A 53 init frame announcing the transfer. */
    fun initFrame(state: Int = 1, frameNo: Int = 0): ByteArray {
        val b = ByteArray(20)
        b[0] = 0x5A
        b[1] = 0x53
        b[2] = picNo.toByte()
        val total = chunks.size
        b[3] = (total ushr 8).toByte(); b[4] = total.toByte()
        b[5] = (frameNo ushr 8).toByte(); b[6] = frameNo.toByte()
        b[7] = state.toByte()
        b[8] = if (picNo == PIC_PROFILE) 5 else if (picNo == PIC_CALLER) 10 else 0
        "Test".toByteArray(Charsets.UTF_8).copyInto(b, 9)
        b[19] = 0xFF.toByte()
        return b
    }

    /** 0x5A 55 data frame for one chunk, or null when out of range. */
    fun chunkFrame(frameNo: Int): ByteArray? {
        val data = chunks.getOrNull(frameNo) ?: return null
        val b = ByteArray(2 + 2 + data.size + 2)
        b[0] = 0x5A
        b[1] = 0x55
        b[2] = (frameNo ushr 8).toByte(); b[3] = frameNo.toByte()
        data.copyInto(b, 4)
        b[b.lastIndex - 1] = 0
        b[b.lastIndex] = 0xFF.toByte()
        return b
    }

    /** Handle an inbound 0x5A 54 request; returns a chunk frame to write. */
    fun onRequest(raw: ByteArray): ByteArray? {
        if (raw.size < 7 || (raw[1].toInt() and 0xFF) != 0x54) return null
        val requestedPic = raw[2].toInt() and 0xFF
        if (requestedPic != picNo) return null
        val frameNo = ((raw[5].toInt() and 0xFF) shl 8) or (raw[6].toInt() and 0xFF)
        return chunkFrame(frameNo)
    }

    /** 0x5A 53 with state=3 marks the transfer complete. */
    fun completedFrame(): ByteArray = initFrame(state = 3, frameNo = chunks.size)
}
