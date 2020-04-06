package io.taggit.db

import main.kotlin.io.taggit.common.AppProperties
import main.kotlin.io.taggit.common.GithubUser
import mu.KotlinLogging
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.lang.Exception

class DbMigrationService {

    val logger = KotlinLogging.logger {  }

    /**
     * Runs migrations and returns true if successfull
     */
    fun runMigrations(): Boolean {
        return try {
            logger.info { "Running database migrations..." }
            val flyway = Flyway.configure().dataSource(
                AppProperties.dbUrl(AppProperties.env),
                AppProperties.dbUser(AppProperties.env),
                AppProperties.dbPassword(AppProperties.env)
            ).locations("classpath:/db/migration").load()
            flyway.migrate()
            logger.info{ "Database migrations complete!" }
            true
        } catch (e: Exception) {
            logger.error(e) {"Database migrations failed!"}
            false
        }
    }
}
