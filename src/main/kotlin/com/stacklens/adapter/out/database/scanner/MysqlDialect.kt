package com.stacklens.adapter.out.database.scanner

internal object MysqlDialect : DatabaseDialect {
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
        SELECT INDEX_SCHEMA as schemaname, TABLE_NAME as tablename, INDEX_NAME as indexname,
               CONCAT('INDEX ', INDEX_NAME, ' ON ', TABLE_NAME, ' (', GROUP_CONCAT(COLUMN_NAME), ')') as indexdef
        FROM information_schema.STATISTICS
        WHERE LOWER(INDEX_NAME) LIKE LOWER(?)
          AND INDEX_SCHEMA NOT IN ('information_schema','mysql','performance_schema','sys')
        GROUP BY INDEX_SCHEMA, TABLE_NAME, INDEX_NAME
        """

    override fun indexParams(keyword: String): Array<Any> = arrayOf("%$keyword%")

    override fun supportsSequences(): Boolean = false

    override fun supportsTriggers(): Boolean = false
}
