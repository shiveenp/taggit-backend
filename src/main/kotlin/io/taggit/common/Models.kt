package main.kotlin.io.taggit.common

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime
import java.util.*

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

