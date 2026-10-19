package com.stacklens.adapter.out.filesystem

import com.stacklens.application.port.out.repo.RepoReader
import com.stacklens.application.port.out.repo.ShellCommandRunner
import com.stacklens.application.port.out.repo.ShellRepoReaderFactory
import org.springframework.stereotype.Component

@Component
class ShellRepoReaderFactoryAdapter : ShellRepoReaderFactory {
    override fun create(runCommand: ShellCommandRunner): RepoReader = ShellRepoReader(runCommand)
}
