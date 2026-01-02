package dev.kannich.cli

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import dev.kannich.stdlib.SecretRegistry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Custom Logback appender that outputs colored text using Mordant.
 * - ERROR level: light red
 * - WARN level: yellow
 * - INFO/DEBUG/TRACE: default color
 *
 * Pattern is controlled by the 'verbose' property:
 * - Normal: HH:mm:ss.SSS message
 * - Verbose: HH:mm:ss.SSS [ClassName] message
 */
class ColoredConsoleAppender : AppenderBase<ILoggingEvent>() {

    private val terminal = Terminal()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    private val maskedSecret = TextStyles.italic("**secret**")

    var verbose: Boolean = false

    override fun append(event: ILoggingEvent) {
        val time = timeFormatter.format(Instant.ofEpochMilli(event.timeStamp))
        val message = maskSecrets(event.formattedMessage)

        for (line in  message.lines()) {
            val formattedMessage = if (verbose) {
                val className = event.loggerName.substringAfterLast('.')
                "$time [$className] $line"
            } else {
                "$time $line"
            }

            val coloredMessage = when (event.level) {
                Level.ERROR -> TextColors.brightRed(formattedMessage)
                Level.WARN -> TextColors.yellow(formattedMessage)
                else -> formattedMessage
            }

            // Print to stdout (stderr would interfere with piping)
            terminal.println(coloredMessage)
        }
    }

    private fun maskSecrets(message: String): String {
        val secrets = SecretRegistry.getSecrets()
        if (secrets.isEmpty()) return message

        var masked = message
        for (secret in secrets) {
            masked = masked.replace(secret, maskedSecret)
        }
        return masked
    }
}
