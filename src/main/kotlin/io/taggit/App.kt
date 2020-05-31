package io.taggit

import io.taggit.common.AppProperties.githubClientId
import io.taggit.common.AppProperties.githubClientSecret
import io.taggit.common.AppProperties.port
import io.taggit.common.AppProperties.rootServiceUrl
import io.taggit.common.AppProperties.uiURL
import io.taggit.common.Lenses.pageNumberQueryLens
import io.taggit.common.Lenses.pageSizeQueryLens
import io.taggit.common.Lenses.tagSearchQueryLens
import io.taggit.common.Lenses.tagStringLens
import io.taggit.common.Lenses.userUpdateLens
import io.taggit.common.config
import io.taggit.common.toUUID
import io.taggit.db.Dao.getRepoSyncJobUsingId
import io.taggit.db.DbMigrationService
import io.taggit.services.TaggitService.addTag
import io.taggit.services.TaggitService.deleteTag
import io.taggit.services.TaggitService.deleteUser
import io.taggit.services.TaggitService.getAllTags
import io.taggit.services.TaggitService.getUser
import io.taggit.services.TaggitService.getUserReposPaged
import io.taggit.services.TaggitService.loginOrRegister
import io.taggit.services.TaggitService.searchUserRepoByTags
import io.taggit.services.TaggitService.syncUserRepos
import io.taggit.services.TaggitService.updateUser
import org.http4k.client.ApacheClient
import org.http4k.core.*
import org.http4k.core.Method.*
import org.http4k.core.Status.Companion.ACCEPTED
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.TEMPORARY_REDIRECT
import org.http4k.filter.CorsPolicy
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson.asJsonObject
import org.http4k.format.Jackson.asPrettyJsonString
import org.http4k.lens.Path
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.routing.websockets
import org.http4k.security.InsecureCookieBasedOAuthPersistence
import org.http4k.security.OAuthProvider
import org.http4k.security.gitHub
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.websocket.PolyHandler
import org.http4k.websocket.Websocket
import org.http4k.websocket.WsMessage

fun main() {
    // run database migrations
    DbMigrationService().runMigrations()

    val callbackUri = Uri.of("${config[rootServiceUrl]}/callback")

    val syncJobPath = Path.of("syncJobId")

    val ws = websockets(
        "/{syncJobId}" bind { ws: Websocket ->
            val syncJobId = syncJobPath(ws.upgradeRequest)
            var keepChecking = true
            while (keepChecking) {
                val syncJob = getRepoSyncJobUsingId(syncJobId.toUUID())
                ws.send(WsMessage(syncJob.asJsonObject().asPrettyJsonString()))
                if (syncJob.completed) {
                    keepChecking = false
                }
            }
            ws.close()
        }
    )

    val oauthPersistence = InsecureCookieBasedOAuthPersistence("taggit-dev")

    val oauthProvider = OAuthProvider.gitHub(
        ApacheClient(),
        Credentials(config[githubClientId], config[githubClientSecret]),
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
                println("token is $token")
                val savedUserId = loginOrRegister(token!!)
                Response(TEMPORARY_REDIRECT).header(
                    "location",
                    "${config[uiURL]}/user/$savedUserId"
                )
            },
            "/user/{userId}" bind GET to { request ->
                Response(OK).body(
                    getUser(
                        request.path("userId")?.toUUID()
                            ?: throw IllegalArgumentException("userId param cannot be left empty")
                    ).asJsonObject().asPrettyJsonString()
                )
            },
            "/user/{userId}" bind PUT to { request ->
                Response(OK).body(
                    updateUser(
                        request.path("userId")?.toUUID()
                            ?: throw IllegalArgumentException("userId param cannot be left empty")
                        , userUpdateLens(request)
                    ).asJsonObject().asPrettyJsonString()
                )
            },
            "/user/{userId}" bind DELETE to { request ->
                deleteUser(
                    request.path("userId")?.toUUID()
                        ?: throw IllegalArgumentException("userId param cannot be left empty")
                )
                Response(ACCEPTED)
            },
            "/user/{userId}/repos" bind GET to { request ->
                Response(OK).body(
                    getUserReposPaged(
                        request.path("userId")?.toUUID()
                            ?: throw IllegalArgumentException("userId param cannot be left empty"),
                        pageNumberQueryLens(request),
                        pageSizeQueryLens(request)
                    ).asJsonObject().asPrettyJsonString()
                )
            },
            "/user/{userId}/sync" bind POST to { request ->
                val syncJobId = syncUserRepos(
                    request.path("userId")?.toUUID()
                        ?: throw IllegalArgumentException("userId param cannot be left null")
                )
                Response(ACCEPTED).headers((listOf(Pair("Location", "/$syncJobId")))).body(syncJobId.toString())
            },
            "/user/{userId}/tags" bind GET to { request ->
                Response(OK).body(
                    getAllTags(
                        request.path("userId")?.toUUID()
                            ?: throw IllegalArgumentException("userId param cannot be left empty")
                    ).asJsonObject().asPrettyJsonString()
                )
            },
            "/user/{userId}/repo/search" bind GET to { request ->
                Response(OK).body(
                    searchUserRepoByTags(
                        request.path("userId")?.toUUID()
                            ?: throw IllegalArgumentException("userId param cannot be left empty"),
                        tagSearchQueryLens(request)
                    ).asJsonObject().asPrettyJsonString()
                )
            },
            "/sync/{jobId}" bind GET to { request ->
                Response(OK).body(
                    getRepoSyncJobUsingId(
                        request.path("jobId")?.toUUID()
                            ?: throw IllegalArgumentException("jobId param cannot be left null")
                    ).asJsonObject().asPrettyJsonString()
                )
            },
            "repo" bind routes(
                "{repoId}/tag" bind POST to { request ->
                    Response(OK).body(
                        addTag(
                            request.path("repoId")?.toUUID()
                                ?: throw IllegalArgumentException("repoId param cannot be left null"),
                            tagStringLens(request)
                        ).asJsonObject().asPrettyJsonString()
                    )
                },
                "{repoId}/tag/{tag}" bind Method.DELETE to { request ->
                    Response(OK).body(
                        deleteTag(
                            request.path("repoId")?.toUUID()
                                ?: throw IllegalArgumentException("repoId param cannot be left null"),
                            request.path("tag")
                                ?: throw IllegalArgumentException("Tag to delete cannot be null")
                        ).asJsonObject().asPrettyJsonString()
                    )
                }
            )
        )

    val http = ServerFilters
        .Cors(CorsPolicy.UnsafeGlobalPermissive)
        .then(app)

    PolyHandler(http, ws)
        .asServer(Jetty(config[port]))
        .start()
        .block()
}
