package com.stacklens.loader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class EncryptedClassLoaderTest {
    private val passphrase = "loader-test-key"
    private val key = deriveKey(passphrase)

    private val targetClass = "com.stacklens.loader.ClassCrypto"
    private val targetResource = "com/stacklens/loader/ClassCrypto.class"

    private fun classCryptoBytes(): ByteArray =
        EncryptedClassLoaderTest::class.java.classLoader
            .getResourceAsStream(targetResource)
            ?.readBytes()
            ?: error("Could not find $targetResource on the classpath — run compileKotlin first")

    @Test
    fun `findClass loads a class from an encrypted clenc file`(
        @TempDir tempDir: Path,
    ) {
        val plainBytes = classCryptoBytes()
        val encryptedBytes = encrypt(plainBytes, key)

        val targetFile = tempDir.resolve("com/stacklens/loader/ClassCrypto.clenc").toFile()
        targetFile.parentFile.mkdirs()
        targetFile.writeBytes(encryptedBytes)

        EncryptedClassLoader(arrayOf(tempDir.toUri().toURL()), null, key).use { loader ->
            val clazz = loader.loadClass(targetClass)
            assertNotNull(clazz)
            assertEquals(targetClass, clazz.name)
        }
    }

    @Test
    fun `findClass defines the class in the loader's namespace`(
        @TempDir tempDir: Path,
    ) {
        val encryptedBytes = encrypt(classCryptoBytes(), key)
        val targetFile = tempDir.resolve("com/stacklens/loader/ClassCrypto.clenc").toFile()
        targetFile.parentFile.mkdirs()
        targetFile.writeBytes(encryptedBytes)

        EncryptedClassLoader(arrayOf(tempDir.toUri().toURL()), null, key).use { loader ->
            val clazz = loader.loadClass(targetClass)
            assertEquals(loader, clazz.classLoader)
        }
    }

    @Test
    fun `findClass falls back to plain class file when no clenc exists`(
        @TempDir tempDir: Path,
    ) {
        val targetFile = tempDir.resolve("com/stacklens/loader/ClassCrypto.class").toFile()
        targetFile.parentFile.mkdirs()
        targetFile.writeBytes(classCryptoBytes())

        EncryptedClassLoader(arrayOf(tempDir.toUri().toURL()), null, key).use { loader ->
            val clazz = loader.loadClass(targetClass)
            assertNotNull(clazz)
            assertEquals(targetClass, clazz.name)
        }
    }

    @Test
    fun `findClass throws ClassNotFoundException when neither clenc nor class exists`(
        @TempDir tempDir: Path,
    ) {
        EncryptedClassLoader(arrayOf(tempDir.toUri().toURL()), null, key).use { loader ->
            assertThrows<ClassNotFoundException> {
                loader.loadClass("com.example.NonExistent")
            }
        }
    }

    @Test
    fun `findClass re-throws decryption error for wrong key`(
        @TempDir tempDir: Path,
    ) {
        val encryptedBytes = encrypt(classCryptoBytes(), key)
        val targetFile = tempDir.resolve("com/stacklens/loader/ClassCrypto.clenc").toFile()
        targetFile.parentFile.mkdirs()
        targetFile.writeBytes(encryptedBytes)

        val wrongKey = deriveKey("wrong-passphrase")
        EncryptedClassLoader(arrayOf(tempDir.toUri().toURL()), null, wrongKey).use { loader ->
            assertThrows<Exception> {
                loader.loadClass(targetClass)
            }
        }
    }

    @Test
    fun `full roundtrip - encrypt class file then load it through EncryptedClassLoader`(
        @TempDir tempDir: Path,
    ) {
        val plainBytes = classCryptoBytes()

        val classFile = tempDir.resolve("com/stacklens/loader/ClassCrypto.class").toFile()
        classFile.parentFile.mkdirs()
        classFile.writeBytes(plainBytes)

        val clencFile = File(classFile.absolutePath.replace(".class", ".clenc"))
        clencFile.writeBytes(encrypt(plainBytes, key))
        classFile.delete()

        EncryptedClassLoader(arrayOf(tempDir.toUri().toURL()), null, key).use { loader ->
            val clazz = loader.loadClass(targetClass)
            assertEquals(targetClass, clazz.name)
        }
    }
}
