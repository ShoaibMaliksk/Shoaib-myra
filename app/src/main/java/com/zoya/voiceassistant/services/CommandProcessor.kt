package com.zoya.voiceassistant.services

import java.net.URLEncoder

data class CommandResult(
    val actionText: String,
    val targetUrl: String? = null,
    val isAction: Boolean = false
)

object CommandProcessor {
    fun processCommand(commandText: String): CommandResult {
        val lowerCmd = commandText.lowercase().trim()

        // 1. WhatsApp Match: "send a whatsapp message to [number] saying [message]"
        val waRegex = Regex("^send\\s+a\\s+whatsapp\\s+message\\s+to\\s+([\\d\\+\\s]+)\\s+saying\\s+(.+)$")
        val waMatch = waRegex.find(lowerCmd)
        if (waMatch != null) {
            val phone = waMatch.groupValues[1].replace("\\s".toRegex(), "")
            val message = URLEncoder.encode(waMatch.groupValues[2].trim(), "UTF-8")
            return CommandResult(
                actionText = "Sending your message. Let's hope they reply, Shoaib.",
                targetUrl = "https://api.whatsapp.com/send?phone=$phone&text=$message",
                isAction = true
            )
        }

        // 2. Play on YouTube Match: "play [song] on youtube"
        val ytRegex = Regex("^play\\s+(.+?)\\s+on\\s+youtube$")
        val ytMatch = ytRegex.find(lowerCmd)
        if (ytMatch != null) {
            val query = URLEncoder.encode(ytMatch.groupValues[1].trim(), "UTF-8")
            return CommandResult(
                actionText = "Playing ${ytMatch.groupValues[1]} on YouTube. Don't judge my music taste.",
                targetUrl = "https://www.youtube.com/results?search_query=$query",
                isAction = true
            )
        }

        // 3. Search on Spotify Match: "search [query] on spotify"
        val spotifyRegex = Regex("^search\\s+(.+?)\\s+on\\s+spotify$")
        val spotifyMatch = spotifyRegex.find(lowerCmd)
        if (spotifyMatch != null) {
            val query = URLEncoder.encode(spotifyMatch.groupValues[1].trim(), "UTF-8")
            return CommandResult(
                actionText = "Searching ${spotifyMatch.groupValues[1]} on Spotify. Hope it's a banger.",
                targetUrl = "https://open.spotify.com/search/$query",
                isAction = true
            )
        }

        // 4. Open website Match: "open [website]"
        val openRegex = Regex("^open\\s+(.+)$")
        val openMatch = openRegex.find(lowerCmd)
        if (openMatch != null && !lowerCmd.contains("youtube") && !lowerCmd.contains("spotify")) {
            var website = openMatch.groupValues[1].trim().replace("\\s".toRegex(), "")
            if (!website.contains(".")) {
                website += ".com"
            }
            return CommandResult(
                actionText = "Opening ${openMatch.groupValues[1]} for you, ugh.",
                targetUrl = "https://www.$website",
                isAction = true
            )
        }

        return CommandResult("", null, false)
    }
}
