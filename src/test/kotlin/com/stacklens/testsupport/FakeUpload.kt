package com.stacklens.testsupport

import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class FakeUpload(
    private val fieldName: String = "file",
    private val fileName: String? = null,
    private val mime: String? = null,
    private val content: ByteArray = ByteArray(0),
) : MultipartFile {
    override fun getName(): String = fieldName

    override fun getOriginalFilename(): String? = fileName

    override fun getContentType(): String? = mime

    override fun isEmpty(): Boolean = content.isEmpty()

    override fun getSize(): Long = content.size.toLong()

    override fun getBytes(): ByteArray = content

    override fun getInputStream(): InputStream = ByteArrayInputStream(content)

    override fun transferTo(dest: File) {
        dest.writeBytes(content)
    }
}
