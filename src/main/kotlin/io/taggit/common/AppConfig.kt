package io.taggit.common

import com.natpryce.konfig.*


val config = EnvironmentVariables overriding
        ConfigurationProperties. fromResource("application.properties")

object AppProperties {
    val port = Key("PORT", intType)
    val corsOrigins = Key("CORS_ORIGINS", stringType)
    val githubClientId = Key("GITHUB_CLIENT_ID", stringType)
    val githubClientSecret = Key("GITHUB_CLIENT_SECRET", stringType)
    val dbUrl = Key("DATABASE_URL", stringType)
    val dbUser = Key("DATABASE_USER", stringType)
    val dbPassword = Key("DATABASE_PASSWORD", stringType)
    val rootServiceUrl = Key("SERVICE_URI", stringType)
    val uiURL = Key("UI_URL", stringType)
}
