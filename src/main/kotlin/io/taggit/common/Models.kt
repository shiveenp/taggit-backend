package main.kotlin.io.taggit.common

import com.fasterxml.jackson.annotation.JsonProperty
import org.http4k.core.Body
import org.http4k.format.Jackson.auto
import org.http4k.lens.Query
import org.http4k.lens.int
import org.http4k.lens.string
import java.time.LocalDateTime
import java.util.*

object Constants {
    val DEFAULT_PAGE_NM = 0
    val DEFAULT_PAGE_SIZE = 20
}

object Lenses {
    val tagStringLens = Body.auto<TagInput>().toLens()
    val tagSearchQueryLens = Query.string().multi.required("tag")
    val pageNumberQueryLens = Query.int().optional("pageNm")
    val pageSizeQueryLens = Query.int().optional("pageSize")
}

data class StargazingResponse(
    val id: Long,
    val name: String,
    @JsonProperty("stargazers_count")
    val stargazersCount: Int,
    @JsonProperty("html_url")
    val url: String,
    val description: String?,
    val owner: StarredRepoOwner
)

data class StarredRepoOwner(
    @JsonProperty("avatar_url")
    val avatarUrl: String
)

data class GithubUser(
    val id: Long,
    val login: String,
    val name: String,
    val email: String?
)

data class GitstarUser(
    val id: UUID,
    val userName: String,
    val email: String?,
    val githubUserName: String,
    val githubUserId: Long,
    val accessToken: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class TagInput(
    val tag: String
)

data class Metadata(
    val tags: List<String>
)

data class GitStarsRepo(
    val id: UUID,
    val userId: UUID,
    val repoId: Long,
    val repoName: String,
    val githubLink: String,
    val githubDescription: String?,
    val ownerAvatarUrl: String,
    val metadata: Metadata?
)

data class RepoSyncJob(
    val id: UUID,
    val userId: UUID,
    val completed: Boolean,
    val createdAt: LocalDateTime
)

data class PagedResponse<T>(
    val data: List<T>,
    val pageNum: Int,
    val pageSize: Int,
    val total: Int
)
