package com.stacklens.adapter.out.database.scanner

internal object PostgresDialect : DatabaseDialect {
    override fun viewsSql(): String =
        """
        SELECT table_schema, table_name, view_definition
        FROM information_schema.views
        WHERE (LOWER(table_name) LIKE LOWER(?) OR LOWER(view_definition) LIKE LOWER(?))
          AND table_schema NOT IN ('information_schema','pg_catalog','sys','mysql','performance_schema')
        ORDER BY table_schema, table_name
        """

    override fun indexesSql(): String =
        """
        SELECT schemaname, tablename, indexname, indexdef
        FROM pg_indexes
        WHERE (LOWER(indexname) LIKE LOWER(?) OR LOWER(indexdef) LIKE LOWER(?))
          AND schemaname NOT IN ('pg_catalog','information_schema')
        ORDER BY schemaname, tablename, indexname
        """

    override fun indexParams(keyword: String): Array<Any> = arrayOf("%$keyword%", "%$keyword%")

    override fun supportsSequences(): Boolean = true

    override fun supportsTriggers(): Boolean = true
}
