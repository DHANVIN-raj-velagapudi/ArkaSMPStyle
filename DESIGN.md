# ArkaSMPStyle: design

The paid ranks are the only way to support the server, so they must never make survival easier to win.
They change how a player **looks** and add a few **convenience** commands. Nothing else.

## The two ranks (monthly)

| | Free | **Pro** (Rs 99 / month) | **Ultra** (Rs 249 / month) |
|---|---|---|---|
| Homes | 3 | 8 | unlimited (cap 1000) |
| Player warps | 1 | 6 | 13 |
| Player warp creation fee (Rs 500) | pays | free | free |
| Bonus claim blocks, once a month | none | 2,000 | 5,000 |
| Chat colours | white | a few | all, plus gradient |
| Unique tags | none | a few | all |
| `/nick` | no | plain colours | colours and gradients |
| `/craft`, `/trash`, `/anvil` | no | yes | yes |
| `/enchant`, `/grindstone`, `/stonecutter`, `/loom`, `/smithing`, `/cartography`, `/ec` | no | no | yes |
| Hats, emotes, suits, pets, particle / projectile / death effects (UltraCosmetics) | none | a selection | all |
| Party-style gadgets (UltraCosmetics) | none | none | yes |

Ultra includes everything in Pro. A Pro player who upgrades pays Rs 150 (the difference).

## What a rank never gives
Items, money, flying, god mode, damage or combat help, kits, crates, a coin shop, gadgets that push,
pull, explode or teleport other players. The rank commands are **refused while the player is in PvP combat**
(`rank-commands.block-in-combat`, on by default).

## Monthly claim block bonus
Once per calendar month, the first time a Pro or Ultra player joins while their rank is active, Style adds the
bonus blocks (Pro 2,000, Ultra 5,000; both set in `config.yml`) and tells them. Missed months are not saved up.
For scale: Core gives 100 claim blocks per active hour, so Pro's bonus is worth 20 hours and Ultra's 50 hours.
The blocks are permanent bonus blocks (they stay after the rank ends). This is land, not looks, so it is the one
perk that goes beyond cosmetics and convenience; the amounts are deliberately small and easy to change.

## Who does what
- **LuckPerms** holds the ranks. Group `pro`, group `ultra`. A rank is a group.
- **The store** (Tebex) or staff by hand add the group for 30 days.
- **ArkaSMPCore** already enforces homes, player warps and the fee through permission nodes (nothing new in Core
  for limits).
- **UltraCosmetics** draws the effects. Its permission nodes (`ultracosmetics.hats.<name>` ...) are given to the
  groups in LuckPerms. Treasure chests, ammo and buying with money are switched off in its config.
- **ArkaSMPStyle** reads the group, formats chat and names, runs the rank commands, remembers each player's
  choices (tag, chat colour, nick), and exposes PlaceholderAPI values for TAB and holograms.

## Monthly ranks and expiry
Ranks are LuckPerms **temporary** groups, so LuckPerms removes them by itself when the month ends:

```
lp user <name> parent addtemp pro 30d accumulate
lp user <name> parent addtemp ultra 30d accumulate
```

`accumulate` adds 30 days to what is left, so renewing early loses nothing. When a rank ends, everything the player
chose that needs it (tag, chat colour, nick style) quietly falls back to the free look; the choices are kept and
come back when the rank is renewed. Nothing the player earned by playing is ever removed.

## Free for everyone (phase 2)
Earned titles (play hours, mining, fishing, daily streaks) and a few free UltraCosmetics effects as milestone
rewards, so a player who never pays still has something to show.

## Creator roles (phase 2)
YouTuber, Streamer and Builder badges, added by staff only (not for sale). A creator may use `/live <link>`
(allowed domains only, cooldown) to announce a stream or video.

## Build phases
1. **1.0.0** Ranks, chat format with tags and colours, the rank commands, `/style` menu, PlaceholderAPI values,
   LuckPerms setup guide.
2. **1.1.0** `/nick`, creator roles and `/live`, earned titles, join and leave message styles.
3. **1.2.0** UltraCosmetics unlock rewards, daily streaks, expiry reminders.

## Things to verify on the real server
UltraCosmetics menus with Geyser (Bedrock) players; which UltraCosmetics gadgets are safe; the exact LuckPerms
`addtemp ... accumulate` syntax on the installed version; that the chat plugin in use does not fight Style's chat
format (Style's chat can be switched off in config).
