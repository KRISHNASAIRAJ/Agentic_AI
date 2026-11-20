package com.jupiterconnect

import com.jupiterconnect.profile.ClusterImageTransfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ImageTransferTest {

    private fun u(b: ByteArray, i: Int) = b[i].toInt() and 0xFF

    @Test
    fun init_frame_announces_profile_photo() {
        ClusterImageTransfer.loadBytes(ByteArray(173 * 3) { 0x41 })
        val f = ClusterImageTransfer.initFrame()
        assertEquals(20, f.size)
        assertEquals(0x5A, u(f, 0)); assertEquals(0x53, u(f, 1))
        assertEquals(4, u(f, 2))            // picNo 4 = rider profile
        assertEquals(0, u(f, 3)); assertEquals(3, u(f, 4)) // total frames = 3
        assertEquals(0, u(f, 5)); assertEquals(0, u(f, 6)) // frame No = 0
        assertEquals(1, u(f, 7))            // state = transferring
        assertEquals(5, u(f, 8))            // picType for profile
        assertEquals("Test", String(f, 9, 4, Charsets.UTF_8))
        assertEquals(0xFF, u(f, 19))
        ClusterImageTransfer.clear()
    }

    @Test
    fun chunk_frame_is_179_bytes_for_jupiter() {
        ClusterImageTransfer.loadBytes(ByteArray(173) { 0x42 })
        val f = ClusterImageTransfer.chunkFrame(0)
        assertNotNull(f)
        f!!
        assertEquals(179, f.size)
        assertEquals(0x5A, u(f, 0)); assertEquals(0x55, u(f, 1))
        assertEquals(0, u(f, 2)); assertEquals(0, u(f, 3)) // frame No big-endian
        assertEquals(0x42, u(f, 4))
        assertEquals(0, u(f, f.size - 2))
        assertEquals(0xFF, u(f, f.size - 1))
        assertNull(ClusterImageTransfer.chunkFrame(1))
        ClusterImageTransfer.clear()
    }

    @Test
    fun request_frame_returns_the_asked_chunk() {
        ClusterImageTransfer.loadBytes(ByteArray(173 * 2) { 0x43 })
        // inbound 0x5A 54: picNo at [2], frameNo at [5..6] big-endian
        val request = ByteArray(20)
        request[0] = 0x5A; request[1] = 0x54; request[2] = 4
        request[5] = 0; request[6] = 1
        val response = ClusterImageTransfer.onRequest(request)
        assertNotNull(response)
        assertEquals(1, u(response!!, 3)) // frame No on the chunk
        val wrongPic = request.copyOf(); wrongPic[2] = 5
        assertNull(ClusterImageTransfer.onRequest(wrongPic))
        ClusterImageTransfer.clear()
    }
}
