package main.kotlin.io.taggit.common

import org.http4k.cloudnative.env.Environment
import org.http4k.cloudnative.env.EnvironmentKey
import org.http4k.lens.int
import org.http4k.lens.string

object AppProperties {
    val env = Environment.ENV
    val githubClientId = EnvironmentKey.required("GITHUB_CLIENT_ID")
    val githubClientSecret = EnvironmentKey.required("GITHUB_CLIENT_SECRET")
    val dbUrl = EnvironmentKey.required("JDBC_DATABASE_URL")
    val dbUser = EnvironmentKey.required("JDBC_DATABASE_USERNAME")
    val dbPassword = EnvironmentKey.required("JDBC_DATABASE_PASSWORD")
    val rootServiceUrl = EnvironmentKey.optional("SERVICE_URI")
    val uiURL = EnvironmentKey.optional("UI_URL")
}
