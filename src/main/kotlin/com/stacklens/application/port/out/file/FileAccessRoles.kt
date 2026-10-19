package com.stacklens.application.port.out.file

interface FileReadWritePort :
    FileContentStore,
    FileAttributes

interface FileReadWriteDeletePort :
    FileReadWritePort,
    FileDeletion

interface FileWriteDeletePort :
    FileContentStore,
    FileDeletion

interface FileListingReadWritePort :
    FileReadWritePort,
    FileListing

interface FileListingReadWriteDeletePort :
    FileListingReadWritePort,
    FileDeletion

interface TempFileWritePort :
    FileContentStore,
    FileDeletion,
    TempFileCreation

interface TempFileReadWriteDeletePort :
    TempFileWritePort,
    FileReadWriteDeletePort
