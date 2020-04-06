package io.taggit.services

import io.taggit.common.*
import io.taggit.db.DAO
import io.taggit.db.DAO.completeRepoSyncJob
import io.taggit.db.DAO.createNewRepoSyncJob
import io.taggit.db.DAO.deleteTagFromRepo
import io.taggit.db.DAO.getAllDistinctTags
import io.taggit.db.DAO.getCurrentUserByGithubUserId
import io.taggit.db.DAO.getGitStarUser
import io.taggit.db.DAO.getMostRecentUnfinishedRepoSyncJob
import io.taggit.db.DAO.getRepoSyncJobUsingId
import io.taggit.db.DAO.getUserReposByTags
import io.taggit.db.DAO.getUserToken
import io.taggit.db.DAO.insertGitstarsUser
import io.taggit.db.DAO.insertTagInRepo
import io.taggit.db.DAO.updateGitstarsUser
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import me.liuwj.ktorm.dsl.eq
import me.liuwj.ktorm.dsl.select
import me.liuwj.ktorm.dsl.where
import java.util.*

object GitStarsService {
    fun loginOrRegister(token: String): UUID {
        val githubUser = getUserData(token)
        val existingUser = getCurrentUserByGithubUserId(githubUser.id)
        // if existing user is present update, otherwise insert a new user
        if (existingUser.isNotEmpty()) {
            updateGitstarsUser(githubUser, existingUser[0].accessToken, token)
        } else {
            insertGitstarsUser(githubUser, token)
        }
        return DAO.UsersTable.select(DAO.UsersTable.id).where {
            DAO.UsersTable.githubUserId eq githubUser.id
        }
            .map { queryRowSet -> queryRowSet[DAO.UsersTable.id] }[0]!!
    }

    fun getUser(userId: UUID): GitstarUser {
        return getGitStarUser(userId)[0]
    }

    fun getUserReposPaged(userId: UUID, pageNm: Int?, pageSize: Int?): PagedResponse<GitStarsRepo> {
        val limit = (pageSize ?: Constants.DEFAULT_PAGE_SIZE).let {
            if (it <= 0) {
                throw IllegalArgumentException("pageSize field need to be greater than or equal to 1")
            } else {
                it
            }
        }
        val offset = (pageNm ?: Constants.DEFAULT_PAGE_NM).let {
            if (it <= 0) {
                throw IllegalArgumentException("pageNm fields need to be greater than or equal to 1")
            } else {
                // reduce by 1 since the database offset is zero indexed
                (it - 1).times(limit)
            }
        }
        return try {
            DAO.getUserReposPaged(userId, offset, limit)
        } catch (ex: Exception) {
            println(ex.localizedMessage)
            PagedResponse(emptyList(), Constants.DEFAULT_PAGE_NM, Constants.DEFAULT_PAGE_SIZE, 0)
        }
    }

    fun syncUserRepos(userId: UUID): UUID {
        createNewRepoSyncJob(userId)
        val syncJob = getMostRecentUnfinishedRepoSyncJob(userId)
        GlobalScope.launch {
            val token = getUserToken(userId)
            updateUserRepos(userId, token)
            completeRepoSyncJob(syncJob.id)
        }
        return syncJob.id
    }

    fun getsyncJob(jobId: UUID): RepoSyncJob {
        return getRepoSyncJobUsingId(jobId)
    }

    fun updateUserRepos(userId: UUID, token: String) {
        val currentUserRepoIds = DAO.getUserRepos(userId)
            .map { it.repoId }
        getUserStargazingData(token).forEach {
            if (currentUserRepoIds.notContains(it.id)) {
                // only add the repo for the user if not previously added
                DAO.insertRepo(it, userId)
            }
        }
    }

    fun addTag(repoId: UUID, tagInput: TagInput): GitStarsRepo {
        return insertTagInRepo(repoId, tagInput.tag)
    }

    fun deleteTag(repoId: UUID, tagToDelete: String): GitStarsRepo {
        return deleteTagFromRepo(repoId, tagToDelete)
    }

    fun getAllTags(userId: UUID): List<String> {
        return getAllDistinctTags(userId)
    }

    fun searchUserRepoByTags(userId: UUID, tags: List<String>) = getUserReposByTags(userId, tags)
}
