# Changelog

## 1.4.1

### Changed
- **`/plugins` and `/seed` are Admin only.** `minecraft.command.seed` is now denied for everyone by the default group, and
  the Developer rank no longer gets `/plugins` (it keeps `/version`). Only Admin is given both. New servers get this from
  the default config; on a server that already has a `roles` section, copy the two changes in (see the README of this
  release) and run `/style setup apply`.

## 1.4.0

### Changed
- **Admin is now a full trusted staff role.** It can do everything except the dangerous few. New in `roles.admin`: `/give`,
  `/kill`, `/summon`, `/effect`, `/xp`, `/gamerule`, `/difficulty`, `/worldborder`, `/setworldspawn`, `/item`, `/loot`,
  `/attribute`, more vanilla commands (`/enchant`, `/clear`, `/title`, `/save-all`, `/tps`, `/timings` and others) and a long
  list of Essentials commands: teleporting (`/tp`, `/tphere`, `/back`...), players (`/fly`, `/god`, `/heal`, `/feed`,
  `/invsee`, `/enderchest`, `/repair`, `/nick`...), moderation (`/mute`, `/ban`, `/tempban`, `/jail`...) and items.
  Every command is listed one by one: wildcards are still refused.
- **Two guard lists instead of one.** `@console-only` is now only the really dangerous commands: `/op`, `/stop`, `/reload`,
  `/execute`, `/function`, `/data`, `/datapack`, `/forceload`, `/fill`, `/clone`, `/setblock`, LuckPerms editing, the
  Economy import, and Essentials `/sudo`, `/eco`, `/backup` and `/nuke`. No group can ever be given these. The new
  `@staff-only` token stands for `/give`, `/kill`, `/summon`, `/effect`, `/xp`, `/gamerule`, `/difficulty`, `/worldborder`,
  `/setworldspawn`, `/item`, `/loot`, `/attribute` and `/defaultgamemode`: the default group denies them and only Admin
  is given them.
- Admin still cannot give the admin rank or edit LuckPerms. `/style staff` handles every other rank.
- Already installed? Your `roles:` section is yours and is not rewritten. To get the new roles, delete the whole `roles:`
  section from `config.yml` and restart (a backup of the old file is kept), then run `/style reload`, `style setup` and
  `style setup apply` from the console.

## 1.3.0

### Added
- **`/style staff give|remove <player> <group>`** (permission `arkastyle.staff`, given to Admin). An Admin can now give and
  take the staff and creator groups listed in `staff-ranks.grantable` (helper, mod, builder, developer, srmod, youtuber,
  media). The groups `admin` and `default` are always refused, an Admin's own groups cannot be changed, and nobody can
  change their own. Only the console can make an Admin (`lp user NAME parent add admin`).
- Admin can use `/gamemode` and `/vanish`: `essentials.gamemode`, the four `essentials.gamemode.<mode>` nodes and
  `essentials.vanish` are in the admin role. Essentials replaces the vanilla `/gamemode` when it is installed, so the
  vanilla permission alone was not enough.

### Changed
- Admin no longer has `luckperms.user.parent.add`, `.remove`, `.addtemp` and `.removetemp`. With them an Admin could give
  the admin rank to anyone. They are in the admin role's `remove:` list, so `style setup apply` takes them away.
- Already installed? Your `roles:` section is yours and is not rewritten. Edit `roles.admin` as in the new default
  `config.yml`, run `/style reload`, then `style setup` and `style setup apply`. The new `staff-ranks:` section and the
  new messages are added to your config automatically.

## 1.2.2

* UltraCosmetics is blocked for the `default` group: `ultracosmetics.*` is in its `deny` list. Ultra still gets the menu,
  hats, emotes and suits, because an exact or more specific permission beats a wildcard in LuckPerms.
* The Cosmetics button in `/style` is only shown to players who have `ultracosmetics.openmenu`.
* Already on an older version? Your `roles:` section is yours and is not rewritten: add `ultracosmetics.*` to
  `roles.default.deny` (and the four Ultra lines from 1.2.0 to `roles.ultra.allow`), run `/style reload`, then
  `style setup` and `style setup apply`.

## 1.2.1

* Fix: `/style setup` and `/style setup apply` said "You cannot do that" even in the server console. The permission
  `arkastyle.setup` has the default `false`, and Bukkit treats a false default as false for everyone, console included.
  The console is now allowed by name; anyone else still needs `arkastyle.setup` given to them in LuckPerms.

## 1.2.0

* UltraCosmetics for **Ultra only**: `roles.ultra.allow` now has `ultracosmetics.openmenu` and the wildcards
  `ultracosmetics.hats.*`, `ultracosmetics.emotes.*` and `ultracosmetics.suits.*`. Pro and free players get none, and
  YouTuber, Sr.Mod and Admin get them by inheriting Ultra.
* The wildcard rule: `/style setup` still refuses every wildcard except those three. Gadgets, pets and effects must
  be listed one permission at a time.
* If you already updated to 1.1.0 your `roles:` section is yours and is not rewritten. Add the four lines above to
  `roles.ultra.allow`, or delete the `roles:` section and restart to get the new default (a backup is kept).

## 1.1.0

* New `roles:` section in `config.yml`: every LuckPerms group of the server with its weight, parents, `allow`,
  `deny` and `remove` lists. Added to your config automatically on update.
* `/style setup` now **previews** what it would change in LuckPerms and changes nothing. `/style setup apply` does it,
  after saving the groups as they are to `setup-backups/`. It creates missing groups, sets weights and parents, adds
  allowed permissions, sets denied ones to false and removes old too wide grants. It only touches what the config lists,
  never deletes a group, and changes nothing that is already right.
* Safety checks that stop it before anything changes: parent loops, bad group names, a node in both allow and deny,
  wildcards, LuckPerms special nodes.
* A built-in list of console-only permissions (`/op`, `/stop`, `/execute`, `/fill`, `/give`, LuckPerms editing and
  similar) that can never be allowed on any group. `@console-only` in a deny list blocks them all.
* `/style setup` needs the new permission `arkastyle.setup`, which nobody has by default, so only the console can run it.
* The old `permissions:` list under each rank is no longer used; those permissions are now in `roles:`.
* `PERMISSIONS.md`: every permission, who gets it, and what is blocked.

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
* `/style setup` creates the rank groups in LuckPerms (Ultra inherits Pro) and adds the home, player warp and
  no-fee permissions from `ranks.<rank>.permissions`. It only adds. `/style give` refuses when the group is missing.
* Tap-only `/style` menu (works for Bedrock) with a button to the UltraCosmetics menu. The rank item is
  configurable per rank (`icon`, default Pro gold ingot, Ultra nether star, free iron ingot).
* PlaceholderAPI expansion `%style_...%`, including `%style_is_bedrock%` for separate Bedrock and Java TAB designs.
* `config-version: auto` updates; the `ranks`, `chat-colors` and `tags` sections are never overwritten.
