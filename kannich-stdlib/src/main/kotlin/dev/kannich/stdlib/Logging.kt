package dev.kannich.stdlib

import org.slf4j.Logger
import org.slf4j.LoggerFactory

interface Logging {
    fun log(message: String)
    fun logWarning(message: String)
    fun logError(message: String)
}


class LoggingImpl(name:String) : Logging {
    val logger: Logger = LoggerFactory.getLogger("dev.kannich.$name")
    override fun log(message: String) {
        logger.info(message)
    }

    override fun logWarning(message: String) {
        logger.warn(message)
    }

    override fun logError(message: String) {
        logger.error(message)
    }
}