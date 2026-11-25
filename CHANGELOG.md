# Changelog

## 1.0.0

First release.

* Two paid ranks, Pro and Ultra, read from LuckPerms groups (`group.pro`, `group.ultra`). The highest rank wins and
  Ultra includes everything in Pro.
* Chat formatting with a rank badge, a tag and a chat colour. Plain text only: players cannot type colour codes
  or MiniMessage.
* 16 chat colours and 16 tags, each locked to free, Pro or Ultra. All of it is in `config.yml`.
* Rank commands: `/craft` `/trash` `/anvil` (Pro) and `/enchant` `/grindstone` `/stonecutter` `/loom`
  `/smithing` `/cartography` `/ec` (Ultra). Blocked in combat by default.
* Monthly claim block bonus once per calendar month (Pro 2,000, Ultra 5,000), given on join or with
  `/style bonus`. Uses the `ClaimBlockBonus` service from ArkaSMPCore 1.3.0.
* `/style give <player or uuid> <rank> [days]` and `/style remove <player or uuid> <rank>` for the store (Tebex) to
  run from the console. They change the LuckPerms group through its API (renewals add to the time left), thank
  the player, announce it (`purchase.broadcast`) and pay the monthly bonus at once.
* Tap-only `/style` menu (works for Bedrock) with a button to the UltraCosmetics menu. The rank item is
  configurable per rank (`icon`, default Pro gold ingot, Ultra nether star, free iron ingot).
* PlaceholderAPI expansion `%style_...%`, including `%style_is_bedrock%` for separate Bedrock and Java TAB designs.
* `config-version: auto` updates; the `ranks`, `chat-colors` and `tags` sections are never overwritten.
