package com.stacklens.infrastructure.config

import com.stacklens.adapter.out.database.DatabaseScanAdapter
import com.stacklens.adapter.out.filesystem.DiagramStorageAdapter
import com.stacklens.adapter.out.filesystem.DirectRepoReader
import com.stacklens.adapter.out.filesystem.LocalRepoScanAdapter
import com.stacklens.adapter.out.filesystem.ReviewStorageAdapter
import com.stacklens.adapter.out.git.GitCloneAdapter
import com.stacklens.adapter.out.shared.DatabaseScanConnection
import com.stacklens.application.port.out.database.DatabaseScanPort
import com.stacklens.application.port.out.repo.GitClonePort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.port.out.repo.RepoReader
import com.stacklens.application.port.out.storage.DiagramStoragePort
import com.stacklens.application.port.out.storage.ReviewStoragePort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FilesystemAdapterConfig {
    @Bean
    fun localRepoScanPort(): LocalRepoScanPort = LocalRepoScanAdapter()

    @Bean
    fun diagramStoragePort(): DiagramStoragePort = DiagramStorageAdapter()

    @Bean
    fun reviewStoragePort(): ReviewStoragePort = ReviewStorageAdapter()

    @Bean
    fun gitClonePort(): GitClonePort = GitCloneAdapter()

    @Bean
    fun directRepoReader(): RepoReader = DirectRepoReader()

    @Bean
    fun databaseScanPort(connectionPort: DatabaseScanConnection): DatabaseScanPort = DatabaseScanAdapter(connectionPort)
}
