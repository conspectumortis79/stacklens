package com.stacklens.adapter.out.textfilter

import com.stacklens.application.port.out.textfilter.OutputFilterPort
import com.stacklens.application.port.out.textfilter.TextFilterConfigPort
import com.stacklens.domain.text.filter.FilterBundle
import com.stacklens.domain.text.filter.FilterResult
import com.stacklens.domain.text.filter.FilterSpec
import com.stacklens.domain.text.filter.FilterTestCase
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component

@Component
class TomlFilterAdapter(
    private val resourceLoader: ResourceLoader,
    private val config: TextFilterConfigPort?,
    @Value(TextFilterProperties.ENABLED_VALUE)
    private val enabledProp: Boolean = TextFilterProperties.ENABLED_DEFAULT,
) : OutputFilterPort {
    private val log = LoggerFactory.getLogger(TomlFilterAdapter::class.java)

    override val enabled: Boolean get() = config?.enabled ?: enabledProp

    private val engine = RegexFilterEngine()

    private val bundle: FilterBundle by lazy { loadBundle() }

    private fun loadBundle(): FilterBundle {
        val files = loadAllTomlFiles()
        log.info("[TextFilter] Loading {} TOML filter spec(s) from classpath", files.size)
        val (loaded, failed) = parseFilterFiles(files)
        if (failed.isNotEmpty()) {
            log.warn(
                "[TextFilter] {} TOML file(s) failed to parse and were skipped: {}",
                failed.size,
                failed.joinToString(", "),
            )
        }
        val tests = loaded.tests.values.sumOf { it.size }
        log.info(
            "[TextFilter] {} spec(s) loaded, {} test case(s) available",
            loaded.specs.size,
            tests,
        )
        return loaded
    }

    private fun loadAllTomlFiles(): List<Pair<String, String>> {
        val dir = System.getProperty("stacklens.ai.filter.directory")
        if (dir != null) {
            val fsDir = java.io.File(dir)
            if (!fsDir.isDirectory) {
                log.warn("[TextFilter] Directory '{}' not found — using bundled TOMLs", dir)
                return bundledResources()
            }
            val listed = fsDir.listFiles { f -> f.extension == "toml" }.orEmpty()
            if (listed.isEmpty()) {
                log.warn("[TextFilter] Directory '{}' contains no .toml files", dir)
                return bundledResources()
            }
            return listed.map { it.name to it.readText(Charsets.UTF_8) }
        }
        return bundledResources()
    }

    private fun bundledResources(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for (name in BUNDLED_NAMES) {
            val resource = resourceLoader.getResource("classpath:textfilter/$name.toml")
            if (!resource.exists()) {
                log.warn("[TextFilter] Bundled TOML missing on classpath: {}", name)
                continue
            }
            val text = resource.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            out += "$name.toml" to text
        }
        return out
    }

    override fun filter(
        command: List<String>,
        stdout: String,
        stderr: String,
    ): FilterResult {
        if (!enabled) {
            return FilterResult(
                text = stdout,
                originalChars = stdout.length,
                filteredChars = stdout.length,
                specName = "<disabled>",
            )
        }
        val cmdString = command.joinToString(" ")
        val spec =
            findBestSpec(cmdString)
                ?: return FilterResult(
                    text = stdout,
                    originalChars = stdout.length,
                    filteredChars = stdout.length,
                    specName = "<none>",
                )
        val input = if (spec.filterStderr) stderr.ifEmpty { stdout } else stdout
        return engine.apply(spec, input)
    }

    override fun filterByName(
        specName: String,
        output: String,
    ): FilterResult {
        val spec =
            bundle.spec(specName)
                ?: throw IllegalArgumentException(
                    "No filter spec named '$specName'. Known specs: ${knownFilters()}",
                )
        return engine.apply(spec, output)
    }

    override fun knownFilters(): List<String> = bundle.specs.map { it.name }.sorted()

    internal fun findBestSpec(cmdString: String): FilterSpec? {
        var best: FilterSpec? = null
        var bestLen = -1
        for (spec in bundle.specs) {
            val re = spec.matchCommand ?: continue
            if (!re.containsMatchIn(cmdString)) continue
            if (re.pattern.length > bestLen) {
                best = spec
                bestLen = re.pattern.length
            }
        }
        return best
    }

    companion object {
        val BUNDLED_NAMES: List<String> =
            listOf(
                "ansible-playbook",
                "basedpyright",
                "biome",
                "brew-install",
                "bundle-install",
                "composer-install",
                "df",
                "dotnet-build",
                "du",
                "fail2ban-client",
                "gcc",
                "gcloud",
                "gradle",
                "hadolint",
                "helm",
                "iptables",
                "jira",
                "jj",
                "jq",
                "just",
                "k6",
                "liquibase",
                "make",
                "markdownlint",
                "mise",
                "mix-compile",
                "mix-format",
                "nx",
                "ollama",
                "oxlint",
                "ping",
                "pio-run",
                "poetry-install",
                "pre-commit",
                "ps",
                "pulumi-destroy",
                "pulumi-preview",
                "pulumi-refresh",
                "pulumi-stack",
                "pulumi-up",
                "quarto-render",
                "rsync",
                "shellcheck",
                "shopify-theme",
                "skopeo",
                "sops",
                "spring-boot",
                "ssh",
                "stat",
                "swift-build",
                "systemctl-status",
                "task",
                "terraform-plan",
                "tofu-fmt",
                "tofu-init",
                "tofu-plan",
                "tofu-validate",
                "trunk-build",
                "turbo",
                "ty",
                "uv-sync",
                "xcodebuild",
                "yadm",
                "yamllint",
            )
    }
}

internal fun parseFilterFiles(files: List<Pair<String, String>>): Pair<FilterBundle, List<String>> {
    val specs = mutableListOf<FilterSpec>()
    val tests = mutableMapOf<String, MutableList<FilterTestCase>>()
    val failed = mutableListOf<String>()
    for ((name, content) in files) {
        try {
            val (spec, ts) = TomlFilterParser().parse(content, name)
            if (spec != null) {
                specs += spec
                if (ts.isNotEmpty()) tests[spec.name] = ts.toMutableList()
            }
        } catch (e: TomlParseException) {
            failed += name
        } catch (e: Exception) {
            failed += name
        }
    }
    return FilterBundle(specs = specs, tests = tests) to failed
}
