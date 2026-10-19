package com.stacklens.application.port.out.repo

interface ShellRepoReaderFactory {
    fun create(runCommand: ShellCommandRunner): RepoReader
}
