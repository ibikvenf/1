package com.aegis.av

import com.aegis.av.data.SignatureDatabase
import com.aegis.av.engine.Hashes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 签名库解析与查询的单元测试（纯 JVM，可用 ./gradlew test 运行）。 */
class SignatureDatabaseTest {

    private fun hashes(
        md5: String = "0".repeat(32),
        sha1: String = "0".repeat(40),
        sha256: String = "0".repeat(64),
        size: Long = 68,
    ) = Hashes(md5, sha1, sha256, size)

    @Test
    fun parsesHsbAndMatchesSha256() {
        val db = SignatureDatabase()
        db.load(
            "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f:*:Eicar-Test-Signature\n"
                .byteInputStream(),
            isHdb = false,
        )
        assertEquals(1, db.signatureCount)
        assertEquals(
            "Eicar-Test-Signature",
            db.lookup(hashes(sha256 = "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f")),
        )
        assertNull(db.lookup(hashes(sha256 = "f".repeat(64))))
    }

    @Test
    fun parsesHdbAndMatchesMd5() {
        val db = SignatureDatabase()
        db.load(
            "44d88612fea8a8f36de82e1278abb02f:*:Eicar-Test-Signature\n".byteInputStream(),
            isHdb = true,
        )
        assertEquals(
            "Eicar-Test-Signature",
            db.lookup(hashes(md5 = "44d88612fea8a8f36de82e1278abb02f")),
        )
    }

    @Test
    fun sizeMustMatchUnlessWildcard() {
        val db = SignatureDatabase()
        db.load(
            "44d88612fea8a8f36de82e1278abb02f:68:Eicar-Test-Signature\n".byteInputStream(),
            isHdb = true,
        )
        val h = hashes(md5 = "44d88612fea8a8f36de82e1278abb02f", size = 68)
        assertEquals("Eicar-Test-Signature", db.lookup(h))
        assertNull(db.lookup(hashes(md5 = "44d88612fea8a8f36de82e1278abb02f", size = 69)))
    }

    @Test
    fun ignoresInvalidLines() {
        val db = SignatureDatabase()
        db.load(
            """
            # comment
            not-a-signature
            :::
            zz88612fea8a8f36de82e1278abb02f:*:BadHex
            44d88612fea8a8f36de82e1278abb02f:*:Eicar-Test-Signature

            """.trimIndent().byteInputStream(),
            isHdb = true,
        )
        assertEquals(1, db.signatureCount)
    }

    @Test
    fun hsbRejectsMd5LengthLines() {
        val db = SignatureDatabase()
        // 32 位哈希放进 .hsb（SHA 库）时应被拒绝
        db.load(
            "44d88612fea8a8f36de82e1278abb02f:*:ShouldBeRejected\n".byteInputStream(),
            isHdb = false,
        )
        assertEquals(0, db.signatureCount)
    }
}
