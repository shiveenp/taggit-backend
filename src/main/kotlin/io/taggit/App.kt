package io.taggit

import main.kotlin.io.taggit.DAO.getRepoSyncJobUsingId
import main.kotlin.io.taggit.GitStarsService.addTag
import main.kotlin.io.taggit.GitStarsService.deleteTag
import main.kotlin.io.taggit.GitStarsService.getAllTags
import main.kotlin.io.taggit.GitStarsService.getUser
import main.kotlin.io.taggit.GitStarsService.getUserRepos
import main.kotlin.io.taggit.GitStarsService.loginOrRegister
import main.kotlin.io.taggit.GitStarsService.searchUserRepoByTags
import main.kotlin.io.taggit.GitStarsService.syncUserRepos
import main.kotlin.io.taggit.common.AppProperties.dbPassword
import main.kotlin.io.taggit.common.AppProperties.dbUrl
import main.kotlin.io.taggit.common.AppProperties.dbUser
import main.kotlin.io.taggit.common.AppProperties.env
import main.kotlin.io.taggit.common.AppProperties.githubClientId
import main.kotlin.io.taggit.common.AppProperties.githubClientSecret
import main.kotlin.io.taggit.common.AppProperties.rootServiceUrl
import main.kotlin.io.taggit.common.AppProperties.uiURL
import main.kotlin.io.taggit.common.GithubUser
import main.kotlin.io.taggit.common.TagInput
import main.kotlin.io.taggit.common.toUUID
import org.flywaydb.core.Flyway
import org.http4k.client.ApacheClient
import org.http4k.core.*
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Status.Companion.ACCEPTED
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.TEMPORARY_REDIRECT
import org.http4k.filter.CorsPolicy
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson.asJsonObject
import org.http4k.format.Jackson.asPrettyJsonString
import org.http4k.format.Jackson.auto
import org.http4k.lens.Query
import org.http4k.lens.string
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.security.InsecureCookieBasedOAuthPersistence
import org.http4k.security.OAuthProvider
import org.http4k.security.gitHub
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.slf4j.LoggerFactory

fun main() {

    val logger = LoggerFactory.getLogger(GithubUser::class.java)
    // run migrations
    logger.info ("Running database migrations...")
    val flyway = Flyway.configure().dataSource(dbUrl(env), dbUser(env), dbPassword(env)).load()
    flyway.migrate()
    logger.info("Database migrations complete!")

    val port = System.getenv("PORT")?.toInt() ?: 9001

    val callbackUri = Uri.of( "${rootServiceUrl(env) ?: "http://localhost:9001"}/callback")

    val oauthPersistence = InsecureCookieBasedOAuthPersistence("taggit-dev")

    val tagStringLens = Body.auto<TagInput>().toLens()
    val tagSearchQueryLens = Query.string().multi.required("tag")

    val oauthProvider = OAuthProvider.gitHub(
        ApacheClient(),
        Credentials(githubClientId(env), githubClientSecret(env)),
        callbackUri,
        oauthPersistence
    )

    val app: HttpHandler =
        routes(
            "/" bind GET to {
              Response(OK).body("Welcome to Taggit API")
            },
            callbackUri.path bind GET to oauthProvider.callback,
            "/login" bind GET to oauthProvider.authFilter.then {
                val token = oauthPersistence.retrieveToken(it)?.value?.substringBefore("&scope")?.split("=")?.last()
                val savedUserId = loginOrRegister(token!!)
                Response(TEMPORARY_REDIRECT).header("location", "${uiURL(env) ?: "http://localhost:8080"}/user/$savedUserId")
            },
            "/user/{userId}" bind GET to { request ->
                Response(OK).body(getUser(request.path("userId")?.toUUID()
                    ?: throw IllegalArgumentException("userId param cannot be left empty")).asJsonObject().asPrettyJsonString())
            },
            "/user/{userId}/repos" bind GET to { request ->
                Response(OK).body(getUserRepos(request.path("userId")?.toUUID()
                    ?: throw IllegalArgumentException("userId param cannot be left empty")).asJsonObject().asPrettyJsonString())
            },
            "/user/{userId}/sync" bind POST to { request ->
                val syncJobId = syncUserRepos(request.path("userId")?.toUUID()
                    ?: throw IllegalArgumentException("userId param cannot be left null"))
                Response(ACCEPTED).headers((listOf(Pair("Location", "/sync/$syncJobId"))))
            },
            "/user/{userId}/tags" bind GET to { request ->
                Response(OK).body(getAllTags(request.path("userId")?.toUUID()
                    ?: throw IllegalArgumentException("userId param cannot be left empty")).asJsonObject().asPrettyJsonString())
            },
            "/user/{userId}/repo/search" bind GET to { request ->
                Response(OK).body(searchUserRepoByTags(request.path("userId")?.toUUID()
                    ?: throw IllegalArgumentException("userId param cannot be left empty"), tagSearchQueryLens(request)).asJsonObject().asPrettyJsonString())
            },
            "/sync/{jobId}" bind GET to { request ->
                Response(OK).body(getRepoSyncJobUsingId(request.path("jobId")?.toUUID()
                    ?: throw IllegalArgumentException("jobId param cannot be left null")).asJsonObject().asPrettyJsonString())
            },
            "repo" bind routes(
                "{repoId}/tag" bind POST to { request ->
                    Response(OK).body(addTag(request.path("repoId")?.toUUID()
                        ?: throw IllegalArgumentException("repoId param cannot be left null"), tagStringLens(request)).asJsonObject().asPrettyJsonString())
                },
                "{repoId}/tag/{tag}" bind Method.DELETE to { request ->
                    Response(OK).body(deleteTag(request.path("repoId")?.toUUID()
                        ?: throw IllegalArgumentException("repoId param cannot be left null"), request.path("tag")
                        ?: throw IllegalArgumentException("Tag to delete cannot be null")).asJsonObject().asPrettyJsonString())
                }
            )
        )

    ServerFilters.Cors(CorsPolicy.UnsafeGlobalPermissive)
        .then(app)
        .asServer(Netty(port))
        .start()
        .block()
}
