package main.kotlin.io.taggit

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import main.kotlin.io.taggit.DAO.completeRepoSyncJob
import main.kotlin.io.taggit.DAO.createNewRepoSyncJob
import main.kotlin.io.taggit.DAO.deleteTagFromRepo
import main.kotlin.io.taggit.DAO.getAllDistinctTags
import main.kotlin.io.taggit.DAO.getCurrentUserByGithubUserId
import main.kotlin.io.taggit.DAO.getGitStarUser
import main.kotlin.io.taggit.DAO.getMostRecentUnfinishedRepoSyncJob
import main.kotlin.io.taggit.DAO.getRepoSyncJobUsingId
import main.kotlin.io.taggit.DAO.getUserReposByTags
import main.kotlin.io.taggit.DAO.getUserToken
import main.kotlin.io.taggit.DAO.insertGitstarsUser
import main.kotlin.io.taggit.DAO.insertTagInRepo
import main.kotlin.io.taggit.DAO.updateGitstarsUser
import main.kotlin.io.taggit.common.*
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

    fun getUserRepos(userId: UUID): List<GitStarsRepo> {
        return try {
            DAO.getUserRepos(userId)
        } catch (ex: Exception) {
            println(ex.localizedMessage)
            emptyList()
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
        val currentUserRepos = DAO.getUserRepos(userId)
        getUserStargazingData(token).forEach {
            if (currentUserRepos.notContains(it.id)) {
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
