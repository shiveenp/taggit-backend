package io.taggit.common

import org.http4k.cloudnative.env.Environment
import org.http4k.cloudnative.env.EnvironmentKey
import org.http4k.lens.int
import org.http4k.lens.string
import com.natpryce.konfig.*


val config = EnvironmentVariables overriding
        ConfigurationProperties. fromResource("application.properties")

object AppProperties {
    val port = Key("PORT", intType)
    val githubClientId = Key("GITHUB_CLIENT_ID", stringType)
    val githubClientSecret = Key("GITHUB_CLIENT_SECRET", stringType)
    val dbUrl = Key("DATABASE_URL", stringType)
    val dbUser = Key("DATABASE_USER", stringType)
    val dbPassword = Key("DATABASE_PASSWORD", stringType)
    val rootServiceUrl = Key("SERVICE_URI", stringType)
    val uiURL = Key("UI_URL", stringType)
}
