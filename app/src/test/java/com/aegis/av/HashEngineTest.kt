package com.aegis.av

import com.aegis.av.engine.HashEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/** 哈希引擎单元测试：对照公开的 EICAR 与 RFC 测试向量。 */
class HashEngineTest {

    @Test
    fun eicarVectorsMatchPublishedValues() {
        // https://www.eicar.org/ 公布的标准测试串
        val eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*"
        val h = HashEngine.ofBytes(eicar.toByteArray(Charsets.US_ASCII))
        assertEquals(68L, h.size)
        assertEquals("44d88612fea8a8f36de82e1278abb02f", h.md5)
        assertEquals("3395856ce81f2b7382dee72602f798b642f14140", h.sha1)
        assertEquals(
            "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f",
            h.sha256,
        )
    }

    @Test
    fun abcMatchesRfcVectors() {
        val h = HashEngine.ofBytes("abc".toByteArray())
        assertEquals("900150983cd24fb0d6963f7d28e17f72", h.md5)
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", h.sha1)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            h.sha256,
        )
    }

    @Test
    fun streamMatchesBytes() {
        val data = "AegisAV-Test-String-Harmless-Demo-Signature".toByteArray()
        val a = HashEngine.ofBytes(data)
        val b = HashEngine.ofStream(data.inputStream())!!
        assertEquals(a.md5, b.md5)
        assertEquals(a.sha1, b.sha1)
        assertEquals(a.sha256, b.sha256)
        assertEquals(a.size, b.size)
        // 与内置签名库中的演示签名一致
        assertEquals("03780f3d02898a42f41880176c4f52e2", a.md5)
        assertEquals("177df138d247f867ba54f73fc9aa5fb11195e535", a.sha1)
        assertEquals(
            "3efafae8e6501ffc104e2edc90e6c1cb530475c803445a690271eb3bb9d24271",
            a.sha256,
        )
    }
}
