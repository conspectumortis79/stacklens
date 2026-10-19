package com.stacklens.adapter.out.docker

sealed interface MysqlRootLogin {
    data class Password(
        val value: String,
    ) : MysqlRootLogin

    data object NoPassword : MysqlRootLogin
}

fun MysqlRootLogin.cliOption(): String? =
    when (this) {
        is MysqlRootLogin.Password -> "-p$value"
        MysqlRootLogin.NoPassword -> null
    }

fun MysqlRootLogin.logLabel(): String =
    when (this) {
        is MysqlRootLogin.Password -> "the configured root password"
        MysqlRootLogin.NoPassword -> "an empty root password"
    }

object MysqlDemoContainer {
    const val NAME: String = "stacklens-demo-mysql"
    const val USER: String = "stacklens"
    const val USER_PASSWORD: String = "stacklens"
    const val ROOT_PASSWORD: String = "rootpassword"
    const val DEMO_DATABASE: String = "auth_db"
    const val INIT_SQL_PATH: String = "demo-mysql/initdb/01-schema.sql"

    fun rootLogins(): List<MysqlRootLogin> =
        listOf(
            MysqlRootLogin.Password(ROOT_PASSWORD),
            MysqlRootLogin.NoPassword,
        )
}
