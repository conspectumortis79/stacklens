package com.stacklens.adapter.out.database.scanner

internal object MssqlDialect : DatabaseDialect {
    override fun viewsSql(): String =
        """
        SELECT table_schema, table_name, view_definition
        FROM information_schema.views
        WHERE (LOWER(table_name) LIKE LOWER(?) OR LOWER(view_definition) LIKE LOWER(?))
        ORDER BY table_schema, table_name
        """

    override fun indexesSql(): String =
        """
        SELECT SCHEMA_NAME(t.schema_id) as schemaname, t.name as tablename,
               i.name as indexname, i.type_desc as indexdef
        FROM sys.indexes i JOIN sys.tables t ON i.object_id = t.object_id
        WHERE LOWER(i.name) LIKE LOWER(?) AND i.name IS NOT NULL
        """

    override fun indexParams(keyword: String): Array<Any> = arrayOf("%$keyword%")

    override fun supportsSequences(): Boolean = false

    override fun supportsTriggers(): Boolean = false
}
