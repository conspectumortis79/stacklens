import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private fun org.gradle.api.Task.requiredProperty(name: String): String = providers.gradleProperty(name).get()

val slencMagic = byteArrayOf(0x53, 0x4C, 0x45, 0x4E, 0x43, 0x01)

fun slencDeriveKey(passphrase: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(passphrase.toByteArray(Charsets.UTF_8))

fun slencEncrypt(
    data: ByteArray,
    key: ByteArray,
): ByteArray {
    val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    val ciphertext = cipher.doFinal(data)
    return slencMagic + iv + ciphertext
}

fun slencDecrypt(
    data: ByteArray,
    key: ByteArray,
): ByteArray {
    check(data.size > slencMagic.size + 12) { "Data too short — not a valid SLENC blob" }
    for (i in slencMagic.indices) {
        check(data[i] == slencMagic[i]) { "Invalid SLENC magic at byte $i — wrong file or not encrypted" }
    }
    val iv = data.copyOfRange(slencMagic.size, slencMagic.size + 12)
    val ciphertext = data.copyOfRange(slencMagic.size + 12, data.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    return cipher.doFinal(ciphertext)
}

tasks.register("encryptSources") {
    group = "security"
    description = "Encrypts Kotlin source files to *.kt.enc (key: -Pencryption.key=<passphrase>)"
    doLast {
        val passphrase = requiredProperty("encryption.key")
        val key = slencDeriveKey(passphrase)
        var count = 0
        fileTree("src").matching { include("**/*.kt") }.forEach { file ->
            val encFile = File("${file.absolutePath}.enc")
            encFile.writeBytes(slencEncrypt(file.readBytes(), key))
            file.delete()
            count++
        }
        logger.lifecycle("encryptSources: $count file(s) encrypted. Restore with: ./gradlew decryptSources -Pencryption.key=<passphrase>")
    }
}

tasks.register("decryptSources") {
    group = "security"
    description = "Decrypts *.kt.enc source files back to *.kt (key: -Pencryption.key=<passphrase>)"
    doLast {
        val passphrase = requiredProperty("encryption.key")
        val key = slencDeriveKey(passphrase)
        var count = 0
        fileTree("src").matching { include("**/*.kt.enc") }.forEach { file ->
            val originalFile = File(file.absolutePath.removeSuffix(".enc"))
            originalFile.writeBytes(slencDecrypt(file.readBytes(), key))
            file.delete()
            count++
        }
        logger.lifecycle("decryptSources: $count file(s) decrypted.")
    }
}

tasks.register("encryptClasses") {
    group = "security"
    description = "Encrypts compiled *.class files to *.clenc (key: -Pencryption.key=<passphrase>)"
    dependsOn(tasks.named("compileKotlin"))

    val classDirProvider = layout.buildDirectory.dir("classes/kotlin/main")
    doLast {
        val passphrase = requiredProperty("encryption.key")
        val key = slencDeriveKey(passphrase)
        val classDir = classDirProvider.get().asFile
        var count = 0
        fileTree(classDir)
            .matching {
                include("**/*.class")
                exclude("**/loader/**")
            }.forEach { file ->
                val encFile = File(file.absolutePath.removeSuffix(".class") + ".clenc")
                encFile.writeBytes(slencEncrypt(file.readBytes(), key))
                file.delete()
                count++
            }
        logger.lifecycle("encryptClasses: $count class file(s) encrypted in $classDir")
    }
}

tasks.register("decryptClasses") {
    group = "security"
    description = "Decrypts *.clenc files back to *.class (key: -Pencryption.key=<passphrase>)"
    val classDirProvider = layout.buildDirectory.dir("classes/kotlin/main")
    doLast {
        val passphrase = requiredProperty("encryption.key")
        val key = slencDeriveKey(passphrase)
        val classDir = classDirProvider.get().asFile
        var count = 0
        fileTree(classDir)
            .matching { include("**/*.clenc") }
            .forEach { file ->
                val originalFile = File(file.absolutePath.removeSuffix(".clenc") + ".class")
                originalFile.writeBytes(slencDecrypt(file.readBytes(), key))
                file.delete()
                count++
            }
        logger.lifecycle("decryptClasses: $count class file(s) decrypted in $classDir")
    }
}
