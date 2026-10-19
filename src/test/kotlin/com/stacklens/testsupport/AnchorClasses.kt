package com.stacklens.testsupport

import java.io.File
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.security.CodeSource
import java.security.Permissions
import java.security.ProtectionDomain
import java.security.cert.Certificate

class ProbeAnchor

object AnchorClasses {
    private val NO_CERTIFICATES: Array<Certificate>? = null

    fun urlOf(spec: String): URL = URI(spec).toURL()

    fun codeSourceWith(uri: String): CodeSource = CodeSource(URI(uri).toURL(), NO_CERTIFICATES)

    fun codeSourceWithoutLocation(): CodeSource = CodeSource(null, NO_CERTIFICATES)

    fun uriOf(file: File): String = file.toURI().toString()

    fun defined(
        resourceUrl: String? = null,
        codeSource: CodeSource? = null,
    ): Class<*> = ProbeAnchorLoader(resourceUrl, codeSource).loadClass(ProbeAnchor::class.java.name)

    fun definedFromUrls(
        urls: List<URL>,
        resourceUrl: String? = null,
        parent: ClassLoader? = null,
    ): Class<*> = ProbeUrlLoader(urls, resourceUrl, parent).loadClass(ProbeAnchor::class.java.name)

    fun definedWithFailingResourceLookup(): Class<*> = FailingResourceLoader().loadClass(ProbeAnchor::class.java.name)

    fun probeResourceName(): String = ProbeAnchor::class.java.name.replace('.', '/') + ".class"
}

private fun probeAnchorBytes(): ByteArray =
    ProbeAnchor::class.java
        .classLoader!!
        .getResourceAsStream(AnchorClasses.probeResourceName())!!
        .readBytes()

private fun probeAnchorName(): String = ProbeAnchor::class.java.name

private class ProbeAnchorLoader(
    private val resourceUrl: String?,
    private val codeSource: CodeSource?,
) : ClassLoader(null) {
    override fun findResource(name: String): URL? = resourceUrl?.let { AnchorClasses.urlOf(it) }

    override fun findClass(name: String): Class<*> {
        if (name != probeAnchorName()) throw ClassNotFoundException(name)
        val bytes = probeAnchorBytes()
        return defineClass(name, bytes, 0, bytes.size, ProtectionDomain(codeSource, Permissions()))
    }
}

private class ProbeUrlLoader(
    urls: List<URL>,
    private val resourceUrl: String?,
    parent: ClassLoader?,
) : URLClassLoader(urls.toTypedArray(), parent) {
    override fun findResource(name: String): URL? = super.findResource(name) ?: resourceUrl?.let { AnchorClasses.urlOf(it) }

    override fun findClass(name: String): Class<*> {
        if (name != probeAnchorName()) throw ClassNotFoundException(name)
        val bytes = probeAnchorBytes()
        return defineClass(name, bytes, 0, bytes.size)
    }
}

private class FailingResourceLoader : ClassLoader(null) {
    override fun findResource(name: String): URL = throw IllegalStateException("resource lookup broken: $name")

    override fun findClass(name: String): Class<*> {
        if (name != probeAnchorName()) throw ClassNotFoundException(name)
        val bytes = probeAnchorBytes()
        return defineClass(name, bytes, 0, bytes.size)
    }
}
