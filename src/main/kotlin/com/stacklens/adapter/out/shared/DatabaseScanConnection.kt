package com.stacklens.adapter.out.shared

import com.stacklens.domain.model.DatabaseConfig
import java.sql.Connection

fun interface DatabaseScanConnection {
    fun open(config: DatabaseConfig): Connection
}
