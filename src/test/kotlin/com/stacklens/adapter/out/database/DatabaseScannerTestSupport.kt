package com.stacklens.adapter.out.database

import com.stacklens.adapter.out.database.scanner.ColumnScanner
import com.stacklens.adapter.out.database.scanner.IndexScanner
import com.stacklens.adapter.out.database.scanner.RoutineScanner
import com.stacklens.adapter.out.database.scanner.SequenceScanner
import com.stacklens.adapter.out.database.scanner.TableScanner
import com.stacklens.adapter.out.database.scanner.TriggerScanner
import com.stacklens.adapter.out.database.scanner.ViewScanner

object DatabaseScannerTestSupport {
    fun allScanners(): List<DatabaseObjectScanner> =
        listOf(
            TableScanner(),
            ColumnScanner(),
            ViewScanner(),
            RoutineScanner(),
            IndexScanner(),
            SequenceScanner(),
            TriggerScanner(),
        )
}
