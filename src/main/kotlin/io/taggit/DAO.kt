package main.kotlin.io.taggit

import main.kotlin.io.taggit.common.*
import me.liuwj.ktorm.database.Database
import me.liuwj.ktorm.dsl.*
import me.liuwj.ktorm.schema.*
import me.liuwj.ktorm.support.postgresql.PostgreSqlDialect
import org.http4k.format.Jackson.asJsonObject
import org.http4k.format.Jackson.asA
import java.time.LocalDateTime
import java.util.*

object DAO {

    val db = Database.connect(
        url = AppProperties.dbUrl(AppProperties.env),
        driver = "org.postgresql.Driver",
        user = AppProperties.dbUser(AppProperties.env),
        password = AppProperties.dbPassword(AppProperties.env),
        dialect = PostgreSqlDialect()
    )

    object UsersTable : Table<Nothing>("users") {
        val id by uuid("id").primaryKey()
        val userName by text("user_name")
        val email by text("email")
        val password by text("password")
        val githubUserName by text("github_user_name")
        val githubUserId by long("github_user_id")
        val accessToken by text("access_token")
        val tokenRefreshedAt by datetime("token_refreshed_at")
        val lastLoginAt by datetime("last_login_at")
        val createdAt by datetime("created_at")
        val updatedAt by datetime("updated_at")
    }

    object RepoTable : Table<Nothing>("repo") {
        val id by uuid("id").primaryKey()
        val userId by uuid("user_id")
        val repoId by long("repo_id")
        val repoName by text("repo_name")
        val githubLink by text("github_link")
        val githubDescription by text("github_description")
        val starCount by int("star_count")
        val ownerAvatarUrl by text("owner_avatar_url")
        val metadata by jsonb("metadata", typeRef<Metadata>())
    }

    object RepoSyncJobsTable : Table<Nothing>("repo_sync_jobs") {
        val id by uuid("id").primaryKey()
        val userId by uuid("user_id")
        val completed by boolean("completed")
        val createdAt by datetime("created_at")
    }

    fun getUserToken(userId: UUID): String {
        return UsersTable.select(UsersTable.accessToken)
            .where { UsersTable.id eq userId }
            .map { row -> row[UsersTable.accessToken]!! }[0]
    }

    fun insertGitstarsUser(githubUser: GithubUser, token: String): Int {
        return UsersTable.insert {
            UsersTable.id to UUID.randomUUID()
            UsersTable.userName to githubUser.name
            UsersTable.email to githubUser.email
            UsersTable.password to "hello_it_me"
            UsersTable.githubUserName to githubUser.login
            UsersTable.githubUserId to githubUser.id
            UsersTable.accessToken to token
            UsersTable.tokenRefreshedAt to LocalDateTime.now()
            UsersTable.lastLoginAt to LocalDateTime.now()
            UsersTable.createdAt to LocalDateTime.now()
            UsersTable.updatedAt to LocalDateTime.now()
        } as Int
    }

    fun updateGitstarsUser(githubUser: GithubUser, oldAccessToken: String, newAccessToken: String): Int {
        return if (oldAccessToken != newAccessToken) {
            UsersTable.update {
                UsersTable.userName to githubUser.name
                UsersTable.githubUserName to githubUser.login
                UsersTable.githubUserId to githubUser.id
                UsersTable.accessToken to newAccessToken
                UsersTable.tokenRefreshedAt to LocalDateTime.now()
                UsersTable.lastLoginAt to LocalDateTime.now()
                UsersTable.updatedAt to LocalDateTime.now()
            }
        } else {
            UsersTable.update {
                UsersTable.userName to githubUser.name
                UsersTable.githubUserName to githubUser.login
                UsersTable.githubUserId to githubUser.id
                UsersTable.lastLoginAt to LocalDateTime.now()
            }
        }
    }

    fun getGitStarUser(userId: UUID): List<GitstarUser> {
        return UsersTable.select()
            .where { UsersTable.id eq userId }
            .map { row ->
                GitstarUser(
                    id = row[UsersTable.id]!!,
                    userName = row[UsersTable.userName]!!,
                    email = row[UsersTable.email],
                    githubUserName = row[UsersTable.githubUserName]!!,
                    githubUserId = row[UsersTable.githubUserId]!!,
                    accessToken = row[UsersTable.accessToken]!!,
                    createdAt = row[UsersTable.createdAt]!!,
                    updatedAt = row[UsersTable.updatedAt]!!
                )
            }
    }

    fun getCurrentUserByGithubUserId(githubUserId: Long): List<GitstarUser> {
        return UsersTable.select()
            .where { UsersTable.githubUserId eq githubUserId }
            .map { row ->
                GitstarUser(
                    id = row[UsersTable.id]!!,
                    userName = row[UsersTable.userName]!!,
                    email = row[UsersTable.email]!!,
                    githubUserName = row[UsersTable.githubUserName]!!,
                    githubUserId = row[UsersTable.githubUserId]!!,
                    accessToken = row[UsersTable.accessToken]!!,
                    createdAt = row[UsersTable.createdAt]!!,
                    updatedAt = row[UsersTable.updatedAt]!!
                )
            }
    }

    fun insertRepo(stargazingResponse: StargazingResponse, userId: UUID) {
        RepoTable.insert {
            RepoTable.id to UUID.randomUUID()
            RepoTable.userId to userId
            RepoTable.repoId to stargazingResponse.id
            RepoTable.repoName to stargazingResponse.name
            RepoTable.githubLink to stargazingResponse.url
            RepoTable.githubDescription to stargazingResponse.description
            RepoTable.starCount to stargazingResponse.stargazersCount
            RepoTable.ownerAvatarUrl to stargazingResponse.owner.avatarUrl
        }
    }

    fun getUserRepos(userId: UUID): List<GitStarsRepo> {
        return RepoTable.select()
            .where { RepoTable.userId eq userId }
            .orderBy(RepoTable.repoName.asc())
            .map { row ->
                GitStarsRepo(
                    id = row[RepoTable.id]!!,
                    userId = row[RepoTable.userId]!!,
                    repoName = row[RepoTable.repoName]!!,
                    githubLink = row[RepoTable.githubLink]!!,
                    githubDescription = row[RepoTable.githubDescription],
                    ownerAvatarUrl = row[RepoTable.ownerAvatarUrl]!!,
                    metadata = row[RepoTable.metadata]
                )
            }
    }

    /**
     * Very 🌶 code
     */
    fun getUserReposByTags(userId: UUID, tags: List<String>): List<GitStarsRepo> {
        val tagsJsonBQuery = tags.map {
            "r.metadata @> '{\"tags\":[\"$it\"]}'"
        }.joinToString(" OR ")

        val sql = """
                select * from repo r
                where r.user_id = '$userId'
                and ($tagsJsonBQuery)
                order by r.repo_name asc
            """.trimIndent()

        db.useConnection { conn ->
            return conn.prepareStatement(sql).use { ps ->
                ps.executeQuery().iterable().map {
                    GitStarsRepo(
                        id = it.getObject("id") as UUID,
                        userId = it.getObject("user_id") as UUID,
                        repoName = it.getString("repo_name"),
                        githubLink = it.getString("github_link"),
                        githubDescription = it.getString("github_description"),
                        ownerAvatarUrl = it.getString("owner_avatar_url"),
                        metadata = it.getString("metadata").asJsonObject().asA()
                    )
                }
            }
        }
    }


    fun insertTagInRepo(repoId: UUID, tag: String): GitStarsRepo {
        val existingMetadata = RepoTable.select(RepoTable.metadata)
            .where { RepoTable.id eq repoId }
            .map { row -> row[RepoTable.metadata] }[0]
        val metadataToSave = if (existingMetadata != null) {
            val currentTags = existingMetadata.tags
            val updatedTags = currentTags.toMutableList()
            updatedTags.add(tag)
            // avoid saving same tag twice
            Metadata(tags = updatedTags.toSet().toList())
        } else {
            Metadata(tags = listOf(tag))
        }
        RepoTable.update {
            RepoTable.metadata to metadataToSave
            where { RepoTable.id eq repoId }
        }
        return RepoTable.select()
            .where { RepoTable.id eq repoId }
            .map { row ->
                GitStarsRepo(
                    id = row[RepoTable.id]!!,
                    userId = row[RepoTable.userId]!!,
                    repoName = row[RepoTable.repoName]!!,
                    githubLink = row[RepoTable.githubLink]!!,
                    githubDescription = row[RepoTable.githubDescription]!!,
                    ownerAvatarUrl = row[RepoTable.ownerAvatarUrl]!!,
                    metadata = row[RepoTable.metadata]!!
                )
            }[0]
    }

    fun deleteTagFromRepo(repoId: UUID, tag: String): GitStarsRepo {
        val existingMetadata = RepoTable.select(RepoTable.metadata)
            .where { RepoTable.id eq repoId }
            .map { row -> row[RepoTable.metadata] }[0]

        val metadataToSave = if (existingMetadata != null) {
            val currentTags = existingMetadata.tags
            val updatedTags = currentTags.toMutableList()
            updatedTags.remove(tag)
            // avoid saving same tag twice
            Metadata(tags = updatedTags.toSet().toList())
        } else {
            // shouldn't ever come here, but save an empty list any ways coz I hate playing with nulls
            Metadata(tags = listOf())
        }
        RepoTable.update {
            RepoTable.metadata to metadataToSave
            where { RepoTable.id eq repoId }
        }
        return RepoTable.select()
            .where { RepoTable.id eq repoId }
            .map { row ->
                GitStarsRepo(
                    id = row[RepoTable.id]!!,
                    userId = row[RepoTable.userId]!!,
                    repoName = row[RepoTable.repoName]!!,
                    githubLink = row[RepoTable.githubLink]!!,
                    githubDescription = row[RepoTable.githubDescription]!!,
                    ownerAvatarUrl = row[RepoTable.ownerAvatarUrl]!!,
                    metadata = row[RepoTable.metadata]!!
                )
            }[0]
    }

    fun getAllDistinctTags(userId: UUID): List<String> {
        return RepoTable.select(RepoTable.metadata)
            .where { RepoTable.userId eq userId }
            .map { row -> row[RepoTable.metadata] }
            .filterNotNull()
            .flatMap {
                it.tags
            }.toSortedSet().toList()
    }

    fun getRepoSyncJobUsingId(jobId: UUID): RepoSyncJob {
        return RepoSyncJobsTable.select()
            .where { RepoSyncJobsTable.id eq jobId }
            .map { row ->
                RepoSyncJob(
                    row[RepoSyncJobsTable.id]!!,
                    row[RepoSyncJobsTable.userId]!!,
                    row[RepoSyncJobsTable.completed]!!,
                    row[RepoSyncJobsTable.createdAt]!!)
            }[0]
    }

    fun getMostRecentUnfinishedRepoSyncJob(userId: UUID): RepoSyncJob {
        return RepoSyncJobsTable.select()
            .where {
                RepoSyncJobsTable.userId eq userId
                RepoSyncJobsTable.completed eq false
            }
            .orderBy(RepoSyncJobsTable.createdAt.desc())
            .map { row ->
                RepoSyncJob(
                    row[RepoSyncJobsTable.id]!!,
                    row[RepoSyncJobsTable.userId]!!,
                    row[RepoSyncJobsTable.completed]!!,
                    row[RepoSyncJobsTable.createdAt]!!)
            }[0]
    }

    fun completeRepoSyncJob(jobId: UUID) {
        RepoSyncJobsTable.update {
            RepoSyncJobsTable.completed to true
            where { RepoSyncJobsTable.id eq jobId }
        }
    }

    fun createNewRepoSyncJob(userId: UUID) {
        RepoSyncJobsTable.insert {
            RepoSyncJobsTable.id to UUID.randomUUID()
            RepoSyncJobsTable.userId to userId
            RepoSyncJobsTable.completed to false
        }
    }

}
