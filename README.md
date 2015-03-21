# BetterOntime
A bukkit plugin that tracks and runs console commands at certain player's playtime.

All my plugin's builds can be downloaded from http://kaikk.net/mc/#bukkit

This plugin needs UUIDProvider: https://github.com/KaiKikuchi/UUIDProvider
This plugin needs a database MySQL.

###Commands
- /betterontime | /bot | /ontime - shows your statistics
- /bot help - shows the help
- /bot [player] - shows player's statistics
- /bot leaderboard - shows the leaderboard
- /bot add [player] [time] - adds playtime to player
- /bot cmd [list|add|remove] - manages commands run at specified playtime
- /bot reload - reloads config and database data
- /msgraw [player] [message] - Send a raw message to the specified player. Colors are supported.

##Permissions
- betterontime.self - check your own statistics (default to all)
- betterontime.others - check others statistics (default to OPs)
- betterontime.manage - manage BetterOntime (default to OPs)

####/bot cmd add
Syntax: /bot cmd add [time] [repeated] [command...]
- time : after this amount of timer the command will run
- repeated : [true|false] if the command is repeated every [time]
- command : the command to run. Player name: {p.name} - Player UUID: {p.uuid}

Please report any issue! Suggestions are well accepted!
