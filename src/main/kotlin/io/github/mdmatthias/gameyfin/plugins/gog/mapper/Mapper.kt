package io.github.mdmatthias.gameyfin.plugins.gog.mapper

import org.gameyfin.pluginapi.gamemetadata.Genre
import org.gameyfin.pluginapi.gamemetadata.Theme
import org.gameyfin.pluginapi.gamemetadata.GameFeature

object Mapper {
    fun genre(gogName: String): Genre {
        return when (gogName.lowercase()) {
            "action" -> Genre.ACTION
            "adventure" -> Genre.ADVENTURE
            "indie" -> Genre.INDIE
            "rpg", "role-playing", "crpg", "jrpg" -> Genre.ROLE_PLAYING
            "strategy" -> Genre.STRATEGY
            "rts", "real-time strategy", "real-time" -> Genre.REAL_TIME_STRATEGY
            "turn-based strategy", "turn-based" -> Genre.TURN_BASED_STRATEGY
            "tactical", "tactical rpg" -> Genre.TACTICAL
            "simulation", "sim", "walking simulator" -> Genre.SIMULATOR
            "racing" -> Genre.RACING
            "sports", "team sport" -> Genre.SPORT
            "shooter", "fps", "fpp", "tpp", "shoot 'em up", "twin stick shooter" -> Genre.SHOOTER
            "arcade" -> Genre.ARCADE
            "puzzle", "logic", "puzzle platformer" -> Genre.PUZZLE
            "platformer" -> Genre.PLATFORM
            "fighting" -> Genre.FIGHTING
            "point-and-click", "point&click" -> Genre.POINT_AND_CLICK
            "hack and slash", "beat 'em up" -> Genre.HACK_AND_SLASH_BEAT_EM_UP
            "visual novel" -> Genre.VISUAL_NOVEL
            "card game", "board game" -> Genre.CARD_AND_BOARD_GAME
            "mmo" -> Genre.MMO
            "moba" -> Genre.MOBA
            "pinball" -> Genre.PINBALL
            "quiz", "trivia" -> Genre.QUIZ_TRIVIA
            else -> Genre.UNKNOWN
        }
    }

    fun theme(gogName: String): Theme {
        return when (gogName.lowercase()) {
            "fantasy", "magic", "supernatural", "medieval", "mythology" -> Theme.FANTASY
            "sci-fi", "science fiction", "science", "space", "cyberpunk", "robots", "steampunk", "dystopian", "post-apocalyptic" -> Theme.SCIENCE_FICTION
            "horror", "psychological horror", "survival horror" -> Theme.HORROR
            "thriller", "atmospheric", "dark" -> Theme.THRILLER
            "survival" -> Theme.SURVIVAL
            "historical", "world war ii", "world war i", "western", "noir", "classic" -> Theme.HISTORICAL
            "stealth" -> Theme.STEALTH
            "comedy", "funny", "parody", "dark comedy" -> Theme.COMEDY
            "business", "managerial", "management", "economic", "trading", "transportation" -> Theme.BUSINESS
            "drama", "emotional", "story rich", "narrative" -> Theme.DRAMA
            "non-fiction", "educational", "programming" -> Theme.NON_FICTION
            "sandbox" -> Theme.SANDBOX
            "kids", "family", "family friendly" -> Theme.KIDS
            "open world" -> Theme.OPEN_WORLD
            "warfare", "war", "military", "combat" -> Theme.WARFARE
            "mystery", "detective", "investigation", "detective-mystery", "lovecraftian" -> Theme.MYSTERY
            "romance", "dating sim" -> Theme.ROMANCE
            "erotic", "adult", "sexual content", "nudity", "nsfw", "hentai", "mature" -> Theme.EROTIC
            else -> Theme.UNKNOWN
        }
    }

    fun feature(gogName: String): GameFeature? {
        return when (gogName.lowercase()) {
            "single-player", "single" -> GameFeature.SINGLEPLAYER
            "multi-player", "multiplayer", "online multiplayer", "galaxy multiplayer" -> GameFeature.MULTIPLAYER
            "co-op", "cooperative", "online co-op", "local co-op" -> GameFeature.CO_OP
            "cross-platform multiplayer", "crossplay" -> GameFeature.CROSSPLAY
            "achievements" -> GameFeature.ACHIEVEMENTS
            "controller support", "full controller support", "partial controller support" -> GameFeature.CONTROLLER_SUPPORT
            "cloud saves" -> GameFeature.CLOUD_SAVES
            "leaderboards" -> GameFeature.LEADERBOARDS
            "overlay" -> GameFeature.ACHIEVEMENTS
            "split-screen", "split screen" -> GameFeature.SPLITSCREEN
            "moddable", "mods", "mod" -> GameFeature.MODDING
            "vr" -> GameFeature.VR
            "ar" -> GameFeature.AR
            "workshop" -> GameFeature.WORKSHOP
            "remote play" -> GameFeature.REMOTE_PLAY
            "local multiplayer" -> GameFeature.LOCAL_MULTIPLAYER
            "online pvp" -> GameFeature.ONLINE_PVP
            "online pve" -> GameFeature.ONLINE_PVE
            "local pvp" -> GameFeature.LOCAL_PVP
            "local pve" -> GameFeature.LOCAL_PVE
            else -> null
        }
    }
}
