package com.stacklens.adapter.out.filesystem

import java.io.File

internal fun LocalRepoScanAdapter.listFilesOrEmptyForTest(dir: File): Array<File> = listFilesOrEmpty(dir)
