package com.stacklens.application.port.out.file

interface FileSystemPort :
    FileReadWritePort,
    FileReadWriteDeletePort,
    FileWriteDeletePort,
    FileListingReadWritePort,
    FileListingReadWriteDeletePort,
    TempFileReadWriteDeletePort,
    TempFileWritePort
