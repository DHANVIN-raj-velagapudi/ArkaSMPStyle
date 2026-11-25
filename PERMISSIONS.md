# Permissions and blocking: the full list

Two parts: **who gets what**, and **what is blocked so nobody can abuse it**. Everything is LuckPerms.
Paste the command blocks into the console in order.

**Read this first**

* Permission nodes of the Arka plugins below are taken from their `plugin.yml`, so they are exact.
* Nodes of other software (vanilla commands, Bukkit, Geyser, UltraCosmetics, your ban plugin) are the usual ones but
  **I could not test them on your server**. Check any node with LuckPerms' verbose mode:

  ```
  /lp verbose on
  (run the command as a normal player)
  /lp verbose off
  ```

  The output lists the exact permission the command asked for. Use that name if it differs from this page.
* Test with a **non-op** account. Ops bypass most checks and hide mistakes.
* **Nobody is op. Not even Admin.** Op is all-or-nothing, so it cannot be limited. Admin is powerful through the
  named permissions below instead, and the "complete destruction" commands stay on the console only.
  The server owner uses the console (or the host's panel) for those.

### No ops

1. Open `ops.json` in the server folder. It should contain only `[]`.
2. For anyone listed there, run `deop <name>` in the console.
3. Do not use `op` again. Every Arka permission with `default: op` (like `arka.admin`) is then **false for everyone**
   until you give it to a group, which is what the lists below do.

---

## Part 1: who gets what

### The easy way: let the plugin do it (ArkaSMPStyle 1.1.0 and newer)

Everything in Part 1 and the blocking in Part 2 is already written into the `roles:` section of the Style config. You do
not have to type the LuckPerms commands below. Run these **from the server console**:

```
style setup
style setup apply
```

* `style setup` is a **preview**. It lists what it would create or change, writes every detail to
  `plugins/ArkaSMPStyle/setup-preview.txt`, and **changes nothing**.
* `style setup apply` does it. Before changing anything it saves the groups as they are to
  `plugins/ArkaSMPStyle/setup-backups/<time>.yml`, so a mistake can be put back by hand.

Why it is safe:

* It only touches what the config lists, and only plain, permanent, global permissions. Anything else on a group is
  left alone. It never deletes a group. Running it twice changes nothing the second time.
* If the config has a mistake it **refuses to run** and says exactly where: a loop between parents, a bad group name,
  a node in both allow and deny, a wildcard (the only wildcards allowed are `ultracosmetics.hats.*`,
  `ultracosmetics.emotes.*` and `ultracosmetics.suits.*`), or a dangerous permission.
* The plugin will **never allow** the console-only list (`/op`, `/stop`, `/execute`, `/fill`, LuckPerms editing and so
  on), whatever the config says. A typo cannot hand someone `/op`.
* `deny` sets a permission to false on the group and `remove` takes an old, too wide permission off. That is how the
  clean-up of `minecraft.command.*`, `luckperms.*` and `arkaeco.admin.*` on Admin is done for you.
* `/style setup` needs the permission `arkastyle.setup`, which nobody is given. Only the console has it. That keeps
  permission editing out of every staff account, including Admin.

It needs LuckPerms. The `roles` section is yours to edit: change a weight or add a node, run the preview, check it,
then apply. Group names there are the LuckPerms group names.

What the plugin cannot do (so check these yourself): it cannot test that a vanilla or other-plugin permission name is
right on your version. Use `/lp verbose` as described at the top.

The rest of this page explains each permission. The manual commands in Part 1 are what `style setup apply` runs for you,
and they are kept as a reference and for fixing one thing by hand.

### Groups, parents and weights

| Group | Parent(s) | Weight |
|---|---|---|
| default | (everyone) | 0 |
| pro | | 30 |
| media | pro | 35 |
| ultra | pro | 40 |
| youtuber | ultra | 45 |
| helper | | 50 |
| builder | | 55 |
| mod | helper | 60 |
| developer | | 65 |
| srmod | mod, ultra | 70 |
| admin | srmod | 100 |

A player with several groups gets everything from all of them. The highest weight decides the prefix shown.
Limits (homes, player warps) take the highest number the player has.

```
/lp creategroup media
/lp creategroup youtuber
/lp creategroup helper
/lp creategroup mod
/lp creategroup srmod
/lp creategroup developer
/lp creategroup builder
/lp creategroup admin
/lp group media parent add pro
/lp group youtuber parent add ultra
/lp group mod parent add helper
/lp group srmod parent add mod
/lp group srmod parent add ultra
/lp group admin parent add srmod
/lp group media setweight 35
/lp group ultra setweight 40
/lp group youtuber setweight 45
/lp group helper setweight 50
/lp group builder setweight 55
/lp group mod setweight 60
/lp group developer setweight 65
/lp group srmod setweight 70
/lp group admin setweight 100
```

`pro` and `ultra` and their permissions already exist from `/style setup`.

### Every Arka permission, what it does, and who gets it

**ArkaSMPCore**

| Node | What it does | Who |
|---|---|---|
| `arka.homes.limit.<n>` | Home limit | pro 8, ultra 1000 (set by `/style setup`) |
| `arka.pwarps.limit.<n>` | Player warp limit | pro 6, ultra 13 (set by `/style setup`) |
| `arka.warp.free` | Make player warps without the fee | pro, ultra (set by `/style setup`) |
| `arka.afk.bypass` | Never marked AFK | helper, developer, builder |
| `arka.leaderboard.hide` | Hidden from the leaderboards | helper, developer |
| `arka.stats` | Server statistics dashboard | helper, developer |
| `arka.lag` | `/lag` | helper, developer |
| `arka.claims.bypass` | Build in anyone's claim | srmod, admin |
| `arka.admin` | Core admin commands (reload, clear, setspawn) | admin |
| `arka.warp.admin` | Create and delete server warps, delete any player warp | admin |
| `arka.bypass.combat` | Teleport while in combat | nobody |
| `arka.bypass.cooldown` | Skip command cooldowns | nobody |
| `arka.bypass.warmup` | Skip teleport warmups | nobody |

**ArkaSMPEconomy**

| Node | What it does | Who |
|---|---|---|
| `arkaeco.baltop.hide` | Hidden from `/baltop` | helper, developer |
| `arkaeco.admin.balance` | Look at anyone's balance | mod and above |
| `arkaeco.admin.log` | View the transaction log | srmod, admin |
| `arkaeco.admin.freeze` | Freeze an account | srmod, admin |
| `arkaeco.admin.give`, `take`, `set`, `reset` | Change one account's balance (logged with who did it) | admin |
| `arkaeco.admin.reload` | Reload the economy config (McBackup also uses it to flush the database) | admin |
| `arkaeco.admin.import` | Import Essentials balances. Can overwrite real balances | **nobody** (console only) |
| `arkaeco.admin` and `arkaeco.admin.*` | Umbrella nodes. They **include `import`**, so do not give them | **nobody** |
| `arkaeco.shop.admin` | Change shop prices (`/shopadmin`) | admin |
| `arkaeco.shop.bypass` | Skip the minimum play time before selling | nobody (add to your own account only to test) |
| `arkaeco.trade.bypass` | Skip trade safety checks (AFK, combat, creative) | nobody |

**ArkaSMPStyle**

| Node | What it does | Who |
|---|---|---|
| `arkastyle.admin` | Reload, look up a rank, give and remove paid ranks | admin only |
| `arkastyle.setup` | `/style setup` and `/style setup apply`: create and change LuckPerms groups | **nobody** (the console only) |
| `arkastyle.staff` | `/style staff give or remove <player> <group>`: give and take staff and creator groups (helper, mod, builder, developer, srmod, youtuber, media). Never admin or default, never an Admin's groups, never your own | admin only |
| `essentials.gamemode`, `essentials.gamemode.survival, .creative, .adventure, .spectator` | `/gamemode` and `/gm` (Essentials replaces the vanilla command when it is installed) | admin |
| `essentials.vanish` | `/vanish` (Essentials) | admin |

**UltraCosmetics** (set by `style setup apply`, Ultra only; YouTuber, Sr.Mod and Admin inherit it)

| Node | What it does | Who |
|---|---|---|
| `ultracosmetics.openmenu` | Open the cosmetics menu (`/uc menu`, and the Cosmetics button in `/style`) | ultra |
| `ultracosmetics.hats.*`, `ultracosmetics.emotes.*`, `ultracosmetics.suits.*` | All hats, emotes and suits | ultra |
| gadgets, pets, effects, mounts, morphs | Not given. Add one permission at a time under `roles.ultra.allow` | nobody yet |

These node names are the usual ones from third-party sources, not confirmed against your UltraCosmetics version.
Check with `/lp verbose` while equipping a hat as an Ultra player.
| `arkastyle.cmd.<name>` | Use a rank command without the rank | nobody (the Pro and Ultra groups already unlock them) |
| `arkastyle.bypass.combat` | Use rank commands in combat | nobody |

**McBackup**

| Node | What it does | Who |
|---|---|---|
| `mcbackup.admin` | `/mcbackup` (status, list, verify, run a backup, cancel, download a snapshot with `restore`). `restore` only downloads into `plugins/McBackup/restore/`: it never touches the live world | admin only |

### Commands to give the permissions

```
/lp group helper permission set arka.afk.bypass true
/lp group helper permission set arka.leaderboard.hide true
/lp group helper permission set arkaeco.baltop.hide true
/lp group helper permission set arka.stats true
/lp group helper permission set arka.lag true
/lp group helper permission set minecraft.command.kick true

/lp group mod permission set minecraft.command.teleport true
/lp group mod permission set arkaeco.admin.balance true

/lp group srmod permission set minecraft.command.ban true
/lp group srmod permission set minecraft.command.pardon true
/lp group srmod permission set minecraft.command.banlist true
/lp group srmod permission set arka.claims.bypass true
/lp group srmod permission set arkaeco.admin.log true
/lp group srmod permission set arkaeco.admin.freeze true

/lp group developer permission set arka.afk.bypass true
/lp group developer permission set arka.leaderboard.hide true
/lp group developer permission set arkaeco.baltop.hide true
/lp group developer permission set arka.stats true
/lp group developer permission set arka.lag true
/lp group developer permission set minecraft.command.teleport true
/lp group developer permission set bukkit.command.plugins true
/lp group developer permission set bukkit.command.version true

/lp group builder permission set arka.afk.bypass true

/lp group admin permission set arka.admin true
/lp group admin permission set arka.warp.admin true
/lp group admin permission set arkaeco.admin.give true
/lp group admin permission set arkaeco.admin.take true
/lp group admin permission set arkaeco.admin.set true
/lp group admin permission set arkaeco.admin.reset true
/lp group admin permission set arkaeco.admin.reload true
/lp group admin permission set arkaeco.shop.admin true
/lp group admin permission set arkastyle.admin true
/lp group admin permission set mcbackup.admin true
/lp group admin permission set luckperms.user.info true
/lp group admin permission set luckperms.user.parent.info true
/lp group admin permission set arkastyle.staff true
/lp group admin permission set essentials.gamemode true
/lp group admin permission set essentials.gamemode.survival true
/lp group admin permission set essentials.gamemode.creative true
/lp group admin permission set essentials.gamemode.adventure true
/lp group admin permission set essentials.gamemode.spectator true
/lp group admin permission set essentials.vanish true
/lp group admin permission set luckperms.group.info true
/lp group admin permission set bukkit.command.plugins true
/lp group admin permission set bukkit.command.version true
/lp group admin permission set minecraft.command.ban-ip true
/lp group admin permission set minecraft.command.pardon-ip true
/lp group admin permission set minecraft.command.gamemode true
/lp group admin permission set minecraft.command.time true
/lp group admin permission set minecraft.command.weather true
/lp group admin permission set minecraft.command.whitelist true
```

**If you already ran any of these earlier suggestions, remove them.** They were too wide: `minecraft.command.*`
includes `/op`, `/stop`, `/execute` and `/give`; `luckperms.*` lets the holder rewrite every permission, delete groups
and bulk-edit everyone; `arkaeco.admin` and `arkaeco.admin.*` include the Essentials import.

```
/lp group admin permission unset minecraft.command.*
/lp group admin permission unset luckperms.*
/lp group admin permission unset arkaeco.admin
/lp group admin permission unset arkaeco.admin.*
```

An unset of something the group never had does nothing, so running all four is safe.

The LuckPerms nodes above let Admin see a player's groups and move people between groups (promote, demote, give a timed
rank). They do **not** let Admin set or change permissions, create or delete groups, import, export, bulk-update or open
the web editor. Those stay with the console. The LuckPerms node names are the usual ones but could differ on your version:
check with `/lp verbose` as described at the top.

Builders: if you use WorldEdit or creative mode, give those **only in the build world**:

```
/lp group builder permission set worldedit.* true world=build
/lp group builder permission set minecraft.command.gamemode true world=build
```

Mute, warn and timed-ban permissions are not listed here because I do not know which plugin you use for them. Add its
nodes the same way, from the least powerful (helper: mute, warn) to the most (srmod: permanent ban).

---

## Part 2: what is blocked

### A. What Admin can do, and what even Admin cannot

**Admin is powerful where it can be undone:** ban and unban (including by IP), kick, teleport, change game mode, time and
weather, whitelist, create and delete server warps, delete player warps and claims, change one player's money (every
change is logged with who did it), freeze an account, change shop prices, start and check backups, give and remove paid
ranks, and move staff between groups. Mistakes with these are small, or can be reversed from the logs or a backup.

**Nobody gets these, not even Admin. Run them from the server console:**

| Group of commands | Why it stays on the console |
|---|---|
| `/op`, `/deop` | Gives or removes all power. There are no ops |
| `/stop`, `/restart`, `/reload`, `/save-off` | Ends the server, breaks plugins, or stops saving so a crash loses progress |
| `/execute`, `/function`, `/data`, `/datapack`, `/forceload` | Run anything as anyone, or edit world data directly |
| `/fill`, `/clone`, `/setblock` | Destroy or flood large parts of the world in one command |
| `/minecraft:*` and `/bukkit:*` | The same commands under their long names. Blocking the permission blocks both |
| LuckPerms: set or unset a permission, create or delete a group, bulk update, import, export, web editor | An account with these can give itself everything. This is the one that makes a stolen staff account a disaster |
| `/arkaeco import essentials` | Overwrites real balances |
| Essentials `/sudo`, `/eco`, `/backup`, `/nuke` | Run commands as someone else (even the console), change balances, backups, kill everything |
| WorldEdit outside the build world, if you use it | A wrong selection can erase a build in seconds |


### What an Admin can do (everything except the list above)

The Admin role is a trusted all-round staff role. Besides everything Sr.Mod and below can do, Admin has these, all
listed one by one in `roles.admin` (wildcards are never used):

* **Staff-only vanilla commands** that nobody else gets (the default group denies them with `@staff-only`): `/give`,
  `/kill`, `/summon`, `/effect`, `/xp`, `/gamerule`, `/difficulty`, `/worldborder`, `/setworldspawn`, `/item`, `/loot`,
  `/attribute`.
* **Other vanilla commands**: `/gamemode`, `/time`, `/weather`, `/whitelist`, `/ban-ip`, `/pardon-ip`, `/enchant`,
  `/clear`, `/spawnpoint`, `/playsound`, `/title`, `/advancement`, `/recipe`, `/seed`, `/damage`, `/save-all`, and the
  server information commands (`/plugins`, `/version`, `/timings`, `/tps`).
* **Essentials**: gamemode (all four modes) and `/vanish`; teleporting (`/tp`, `/tphere`, `/tpall`, `/tppos`, `/back`,
  `/top`, `/jump`); players (`/fly`, `/god`, `/heal`, `/feed`, `/speed`, `/invsee`, `/enderchest`, `/clearinventory`,
  `/repair`, `/nick`, `/seen`, `/whois`, `/socialspy`); moderation (`/mute`, `/kick`, `/ban`, `/tempban`, `/unban`,
  `/banip`, `/unbanip`, `/jail`); items and world (`/give`, `/item`, `/more`, `/time`, `/weather`, `/thunder`,
  `/broadcast`).
* **Economy and shop admin**, `/style give` and `/style remove` for Pro and Ultra, `/style staff` for the other ranks,
  `/mcbackup`, and the Core admin commands.

What Admin **cannot** do: `/op`, `/stop`, `/reload`, `/execute`, `/function`, `/data`, `/fill`, `/clone`, `/setblock`,
Essentials `/sudo`, `/eco`, `/backup`, `/nuke`, any LuckPerms editing, and give another player the `admin` rank (only
`/style staff`, which refuses it, and the server console can change ranks).

Vanilla `/enchant` is not the same as our `/enchant`. When a player types `/enchant`, our Ultra rank command answers.
The vanilla one is still reachable as `/minecraft:enchant`, but only with a permission that nobody is given.

**One honest limit.** The Core permission `arka.admin` covers several commands that cannot be split without a code
change: reload, clear ground items, set the spawn, edit `config.yml` live (`/arka admin set <path> <value>`), and delete
all of a player's claims at once (`/arka admin deleteclaims <player>`). Those last two are the only destructive ones in
there. Until I split the permission, only give `arka.admin` to an Admin you trust.

### B. Information that should not leak

```
/lp group default permission set bukkit.command.plugins false
/lp group default permission set bukkit.command.version false
/lp group default permission set bukkit.command.help false
```

These stop `/plugins`, `/version` (and its aliases `/ver` and `/about`) and `/help` for everyone except Admin and the
Developer, who are given the first two above. Staff groups that need them set them to `true` on their own group.

In `spigot.yml`, set `commands: send-namespaced: false` so the `plugin:command` forms disappear from tab-complete. This
only hides them; the permissions above are what block them.

### C. Chat commands that are used to bypass a mute

```
/lp group default permission set minecraft.command.me false
/lp group default permission set minecraft.command.teammsg false
```

`/msg`, `/tell` and `/w` are left on so players can talk privately. If your mute plugin does not also stop these,
a muted player can still talk through them. Check that when you pick the mute plugin.

### D. Our own plugins

* Rank commands (`/craft`, `/anvil`, `/ec` and the rest) are blocked while a player is in combat
  (`rank-commands.block-in-combat: true` in the Style config).
* `/ec`, `/enchant` and the rest only open for Pro or Ultra (or a group that inherits them). Nobody gets the
  `arkastyle.cmd.*` nodes.
* Only Admin can run `/style give` and `/style remove`. A mod who could would be able to hand out paid ranks.
* The `*.bypass.*` nodes in Part 1 are held by nobody. Add them to your own account for a few minutes of testing and
  remove them after.

### E. Server settings that must stay safe (Paper)

Settings I would check in your own files. Names are the standard ones and I could not open your server to confirm them:

| File | Setting | Should be |
|---|---|---|
| `server.properties` | `enable-command-block` | `false` |
| `paper-global.yml` | everything under `unsupported-settings` (piston duplication, headless pistons, permanent block break, unsafe end portal) | `false`, the default. Never turn these on |
| `paper-world-defaults.yml` | `anticheat.anti-xray.enabled` | `true` (with `engine-mode: 2`) |
| `spigot.yml` | `commands.send-namespaced` | `false` |

### F. Things I did not install or cover

* An **anticheat** (movement, flight, reach). Paper blocks some exploits, not hacked clients. Choose one that supports
  Bedrock players through Floodgate and test it, or Bedrock players may be kicked wrongly.
* A **block-logging plugin** (for example CoreProtect) so mods can see and undo griefing.
* A **mute and ban plugin**. Vanilla cannot mute or do timed bans.
* **Geyser, Floodgate, TAB, DecentHolograms, UltraCosmetics and PlaceholderAPI** all have their own permission nodes.
  Check each one with a normal account and `/lp verbose`: none of their admin commands should work for a non-op.

### G. Test it

With a normal (non-op, non-staff) account, try these. Each should say "unknown command" or "no permission":
`/plugins`, `/version`, `/me hello`, `/op yourself`, `/give yourself diamond`, `/gamemode creative`,
`/style give yourself ultra 30`, `/arkaeco give yourself 1000`, `/mcbackup`.

Then, with the same account but with the Pro group, `/craft` and `/anvil` should work, and `/ec` should be refused with
"needs the Ultra rank".
