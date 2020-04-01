package main.kotlin.io.taggit

import main.kotlin.io.taggit.common.GithubUser
import main.kotlin.io.taggit.common.StargazingResponse
import mu.KotlinLogging
import org.http4k.client.ApacheClient
import org.http4k.core.Body
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.format.Jackson.auto

val client = ApacheClient()
val githubLinkMatchRegex = "<(.*?)>".toRegex()
val stargazingLens = Body.auto<List<StargazingResponse>>().toLens()
private val logger = KotlinLogging.logger { }

fun getUserData(token: String): GithubUser {
    val request = Request(Method.GET, "https://api.github.com/user").header("Authorization", "token $token")
    val userLens = Body.auto<GithubUser>().toLens()
    return userLens.extract(client(request))
}

fun getUserStargazingData(token: String): List<StargazingResponse> {
    val startPage = 1
    var lastPage: Int? = null
    val stargazingList = mutableListOf<StargazingResponse>()

    logger.info { "Getting first bit of user data" }

    val stargazingData = requestGithubStargazingResponse(startPage, token)
    stargazingList.addAll(stargazingData.second)

    // check if more data is available; github usually sends it via a link header
    val linksHeader = stargazingData.first.header("Link")
    logger.info { "Link data is: $linksHeader" }
    if (linksHeader != null) {
        val lastPageLink = linksHeader.split(",").last()
        lastPage = githubLinkMatchRegex.find(lastPageLink)?.groupValues?.last()?.substringAfter("=")?.toInt()
    }
    // if yes, retrieve it iteratively
    if (lastPage != null) {
        logger.info { "Last page found, getting all user data till last page: $lastPage" }
        for (i in 2..lastPage) {
            logger.info { "Getting data for page: $i" }
            val tempStargazingData = requestGithubStargazingResponse(i, token)
            stargazingList.addAll(tempStargazingData.second)
        }
    }
    return stargazingList
}

fun requestGithubStargazingResponse(page: Int, token: String): Pair<Response, List<StargazingResponse>> {
    val request = Request(Method.GET, "https://api.github.com/user/starred?page=$page").header("Authorization", "token $token")
    val response = client(request)
    return Pair(response, stargazingLens.extract(response))
}

