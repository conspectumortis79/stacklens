package com.stacklens.adapter.out.filesystem

import com.stacklens.application.port.out.classpath.ClasspathEntry
import com.stacklens.application.port.out.classpath.ClasspathResourcePort
import com.stacklens.shared.utf8Reader
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.stereotype.Component

@Component
class
ClasspathResourceAdapter internal constructor(
    private val resolver: ResourcePatternResolver,
) : ClasspathResourcePort {
    constructor() : this(PathMatchingResourcePatternResolver())

    override fun listEntries(pattern: String): List<ClasspathEntry> =
        resolver.getResources(pattern).mapNotNull { res ->
            val name = res.filename ?: return@mapNotNull null
            val text = res.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            ClasspathEntry(name, text)
        }

    override fun readText(path: String): String =
        resolver
            .getResource("classpath:$path")
            .inputStream
            .utf8Reader()
            .use { it.readText() }

    override fun exists(path: String): Boolean = resolver.getResource("classpath:$path").exists()
}
