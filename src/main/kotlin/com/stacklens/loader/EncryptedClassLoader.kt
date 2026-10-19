
package com.stacklens.loader

import java.net.URL
import java.net.URLClassLoader

class EncryptedClassLoader(
    urls: Array<URL>,
    parent: ClassLoader?,
    private val key: ByteArray,
) : URLClassLoader(urls, parent) {
    override fun findClass(name: String): Class<*> {
        val encryptedPath = name.replace('.', '/') + ".clenc"
        val resource = findResource(encryptedPath)
        if (resource != null) {
            val plainBytes = decrypt(resource.readBytes(), key)
            return defineClass(name, plainBytes, 0, plainBytes.size)
        }
        return super.findClass(name)
    }
}
