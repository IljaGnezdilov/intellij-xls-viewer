package org.cprime.xlsviewer.parse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LfsPointerTest {

    private val pointer = """
        version https://git-lfs.github.com/spec/v1
        oid sha256:acab370cfa03a87fe704eca343a6b25a7cd00565cf58ce2e0c04847b5615f158
        size 30208
    """.trimIndent().toByteArray()

    @Test
    fun `detects git lfs pointer`() {
        val exception = LfsPointer.detect(pointer)!!
        assertEquals("acab370cfa03a87fe704eca343a6b25a7cd00565cf58ce2e0c04847b5615f158", exception.oid)
        assertEquals(30208L, exception.size)
    }

    @Test
    fun `parser reports lfs pointer instead of a generic error`() {
        val exception = assertThrows(XlsLfsPointerException::class.java) {
            XlsParser.open(pointer)
        }
        assertEquals(30208L, exception.size)
        assertEquals(
            "acab370cfa03a87fe704eca343a6b25a7cd00565cf58ce2e0c04847b5615f158",
            exception.oid,
        )
    }

    @Test
    fun `binary data is not treated as a pointer`() {
        assertNull(LfsPointer.detect(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte())))
        assertNull(LfsPointer.detect("just,some,text".toByteArray()))
    }
}
