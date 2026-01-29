package org.gameyfin.plugins.metadata.gog.mapper

import org.gameyfin.pluginapi.gamemetadata.Genre

class Mapper {
    companion object {
        fun genre(gogGenreName: String): Genre {
            return when (gogGenreName.lowercase()) {
                "action" -> Genre.ACTION
                "adventure" -> Genre.ADVENTURE
                "indie" -> Genre.INDIE
                "rpg", "role-playing" -> Genre.ROLE_PLAYING
                "strategy" -> Genre.STRATEGY
                "rts", "real-time strategy" -> Genre.REAL_TIME_STRATEGY
                "turn-based strategy", "turn-based" -> Genre.TURN_BASED_STRATEGY
                "tactical" -> Genre.TACTICAL
                "simulation" -> Genre.SIMULATOR
                "racing" -> Genre.RACING
                "sports" -> Genre.SPORT
                "shooter" -> Genre.SHOOTER
                "arcade" -> Genre.ARCADE
                "puzzle" -> Genre.PUZZLE
                "platformer" -> Genre.PLATFORM
                "fighting" -> Genre.FIGHTING
                "point-and-click" -> Genre.POINT_AND_CLICK
                "hack and slash", "beat 'em up" -> Genre.HACK_AND_SLASH_BEAT_EM_UP
                "visual novel" -> Genre.VISUAL_NOVEL
                "card game", "board game" -> Genre.CARD_AND_BOARD_GAME
                else -> Genre.UNKNOWN
            }
        }
    }
}
