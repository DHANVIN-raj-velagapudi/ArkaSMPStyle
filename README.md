# ArkaSMPStyle

Paid cosmetic ranks, chat tags and colours, rank commands and a monthly claim block bonus for the Arka SMP.
Part of the Arka plugin set. It needs **ArkaSMPCore 1.3.0 or newer**.

**No pay to win.** A rank never gives coins, items, kits, crates, flight, god mode or anything that makes
survival easier in a way a free player cannot match with time. Ranks are about standing out, a few
convenience commands, and a small land bonus.

Paper 1.21+, Java 21+. Bedrock (Geyser/Floodgate) players can use everything: the menus are tap only.

## The ranks

| | Free | Pro (Rs 99/month) | Ultra (Rs 249/month) |
|---|---|---|---|
| Homes | base | +5 | unlimited |
| Player warps | base | +5 (6 total) | +12 (13 total) |
| Player warp creation fee (Rs 500) | pays | free | free |
| Chat colours | white | 5 (white and 4 more) | all 16 |
| Tags | none | 6 | all 16 |
| Monthly claim blocks | none | 2,000 | 5,000 |
| /craft /trash /anvil | no | yes | yes |
| /enchant /grindstone /stonecutter /loom /smithing /cartography /ec | no | no | yes |
| Cosmetics (UltraCosmetics) | none | none | hats, emotes and suits (more can be added) |

Numbers in the table are the defaults in `config.yml`. The home and warp limits and the fee waiver live in
LuckPerms permissions (see below), the rest in the config.

Rank commands are blocked while a player is in combat (`rank-commands.block-in-combat`, on by default).

## Player commands

| Command | What it does |
|---|---|
| `/style` | Opens the menu (tags, colours, bonus, cosmetics) |
| `/style tag [id\|none]` | Pick a tag, or open the tag menu |
| `/style color [id\|none]` | Pick a chat colour, or open the colour menu |
| `/style bonus` | Claim this month's claim blocks |
| `/style info` | Show your rank |
| `/craft` `/trash` `/anvil` | Pro and Ultra |
| `/enchant` `/grindstone` `/stonecutter` `/loom` `/smithing` `/cartography` `/ec` | Ultra |

## Admin commands

| Command | What it does |
|---|---|
| `/style reload` | Reload `config.yml` (permission `arkastyle.admin`) |
| `/style rank <player>` | Show an online player's rank |
| `/style setup` and `/style setup apply` | Console only. Preview, then apply, the `roles:` section to LuckPerms. Needs LuckPerms |
| `/style give <player or uuid> <rank> [days]` | Give a rank for some days (default 30). This is what Tebex runs. Needs LuckPerms |
| `/style remove <player or uuid> <rank>` | Take a rank away completely. Needs LuckPerms |
| `/style staff <give or remove> <player> <group>` | Admin only. Give or take a staff or creator group (list in `staff-ranks.grantable`). Never handles `admin` or `default`, never changes an Admin or yourself. Needs LuckPerms |

## Install

1. Install **ArkaSMPCore 1.3.0+** and put `ArkaSMPStyle-1.2.2.jar` in `plugins/`.
2. LuckPerms is needed for ranks. PlaceholderAPI is optional (for TAB and scoreboards). UltraCosmetics is optional.
3. Start the server. `plugins/ArkaSMPStyle/config.yml` is created and kept up to date on updates
   (`config-version: auto`; your `ranks`, `chat-colors` and `tags` sections are never overwritten).

## LuckPerms setup

A rank is a LuckPerms group. The `roles:` section of the config lists every group of your server (players, paid ranks,
creators, staff) with its weight, its parents, what it is allowed to do and what is blocked. The plugin makes LuckPerms
match it. Run these **from the console**:

```
style setup          (preview: shows what would change, changes nothing)
style setup apply    (does it, after saving the groups as they are now)
```

* It creates the groups that are missing, sets weights and parents, puts the allowed permissions on each group, sets
  the blocked ones to false and removes old, too wide grants you list under `remove`.
* It only touches what the config lists, never deletes a group, and running it again changes nothing that is already
  right. Everything it changes is listed in `plugins/ArkaSMPStyle/setup-preview.txt`, and a copy of the groups from
  before is kept in `plugins/ArkaSMPStyle/setup-backups/`.
* If the section has a mistake (a loop between parents, a bad name, a permission in both allow and deny) it refuses to
  run and says where.
* **Dangerous permissions can never be allowed**, whatever the config says: `/op`, `/stop`, `/execute`, `/fill`,
  `/give`, LuckPerms editing and similar stay on the console. `@console-only` in a deny list blocks all of them.
  Wildcards (`*`) cannot be allowed either, except `ultracosmetics.hats.*`, `ultracosmetics.emotes.*` and
  `ultracosmetics.suits.*`.
* It needs the permission `arkastyle.setup`, which nobody has, so only the console can run it.

`/style give` refuses to run until the group exists and tells you to run `/style setup apply`.

The home and warp numbers are totals, not extras: 8 homes is the base 3 plus 5, and 6 player warps is the base 1 plus 5.
If your base limits in ArkaSMPCore's `config.yml` are different, change the numbers in `roles:` and run the preview again.
If you update from 1.0.0 your `roles:` section is added to the config automatically (a copy of the old file is kept).

The Style plugin itself only checks `group.pro` and `group.ultra`. A player who has both gets Ultra.
UltraCosmetics: `roles.ultra` gets `ultracosmetics.openmenu` and the hat, emote and suit wildcards, so only Ultra (and
the groups that inherit it) can use cosmetics. Gadgets, pets and effects must be added one permission at a time under
`allow:`, because other wildcards are refused. These node names are the usual ones but I could not confirm them against
your UltraCosmetics version: check with `/lp verbose`.
See `PERMISSIONS.md` for what every permission does and who gets it.

### Monthly ranks

A rank is a temporary LuckPerms group, so it ends on its own. `/style give` sets that up for you (see below).
By hand, the same thing is:

```
/lp user <name> parent addtemp pro 30d accumulate
```

`accumulate` adds to the time left when someone renews early. The exact syntax can differ between LuckPerms
versions. When the group expires the player is a free player again and their tag and colour simply stop showing.
Their picks are kept, so they come back if the rank is renewed.

### Selling ranks (Tebex)

The plugin does not take payments. Your store runs a command from the console when someone pays, and the
plugin does the rest: it gives the LuckPerms group for the number of days, tells the player, announces it to
everyone, and pays the monthly claim block bonus straight away.

In Tebex, make one package per rank and add this as the command (run it as a console command):

| Package | Command on purchase | Command on refund / chargeback |
|---|---|---|
| Pro | `style give {id} pro 30` | `style remove {id} pro` |
| Ultra | `style give {id} ultra 30` | `style remove {id} ultra` |

* `{id}` is the player's UUID, which is safer than a name. `{username}` also works. The plugin accepts a UUID
  with or without dashes.
* If someone renews while they still have time left, the 30 days are added to the time left.
* If the days are left out, `purchase.default-days` is used (30).
* In the Tebex package, turn on "only run when the player is online", or make sure the buyer has joined the
  server once. A player who has never joined is refused with a message in the console.
* If someone buys Ultra while they have Pro, they have both groups and Ultra wins. Remove Pro by hand if you
  want it gone (`style remove <player> pro`).
* Every give and remove is written to the server log with who ran it.
* Test one purchase with a Java account and one Bedrock account before you sell.

You can run the same commands by hand from the console or in game (permission `arkastyle.admin`) to give a rank
on Discord or as a prize:

```
style give <player or uuid> pro 30
```

If you would rather not use these, `/lp user <name> parent addtemp pro 30d accumulate` does the same for the
group alone, without the announcement and the instant bonus.

## Monthly claim block bonus

Pro and Ultra get a claim block bonus once per calendar month (defaults 2,000 and 5,000). It is given
automatically a few seconds after they join if it is due, or with `/style bonus`. Nobody gets it twice in a
month. It is paid through ArkaSMPCore's `ClaimBlockBonus` service, so it needs Core 1.3.0.
Switch it off with `bonus.enabled: false`.

If someone goes from Pro to Ultra mid month they receive the difference only.

## Placeholders (PlaceholderAPI)

| Placeholder | Value |
|---|---|
| `%style_rank%` | `pro`, `ultra` or `free` |
| `%style_rank_name%` | Rank name without colours |
| `%style_badge%` | Rank badge with colour codes |
| `%style_tag%` | The tag the player picked, with colour codes |
| `%style_prefix%` | Badge and tag together, for a TAB prefix |
| `%style_is_bedrock%` | `true` for Bedrock players, `false` for Java |
| `%style_platform%` | `bedrock` or `java` |

`%style_badge_mm%` and `%style_tag_mm%` give MiniMessage text for plugins that read it.

### Different TAB for Bedrock and Java

Bedrock players have different screen space and fonts, so a shorter TAB header or footer often looks better.
Use `%style_is_bedrock%` as a TAB condition. In TAB's `config.yml`:

```yaml
conditions:
  bedrock:
    conditions:
      - "%style_is_bedrock%=true"
    yes: "bedrock-design"
    no: "java-design"
```

Then point your header/footer or per-player design at that condition. TAB's own documentation covers the
exact keys for your version; the only thing Style provides is the placeholder.

## UltraCosmetics

Style does not talk to UltraCosmetics directly. The `/style` menu has a Cosmetics button that runs `/uc menu`,
and what each rank may use is decided by UltraCosmetics permissions in LuckPerms. Suggested UltraCosmetics
`config.yml` settings for a no pay to win server:

```yaml
TreasureChests:
  Enabled: false
Ammo-System-For-Gadgets:
  Enabled: false
No-Permission:
  Allow-Purchase: false
Cosmetics-Affect-Entities: false
Pets-Drop-Items: false
```

(Key names have changed between UltraCosmetics versions. Compare with the `config.yml` it generated, not this
page, and pick the closest keys.)

Given to Ultra by default: the menu, hats, emotes and suits. You can add particle effects, projectile effects, death
effects, pets (no item drops) and gadgets that cannot grief or move other players, one permission at a time. Leave out
anything that gives a gameplay edge. Permission nodes look like `ultracosmetics.<category>.<name>`. Look up the exact
node of each cosmetic in UltraCosmetics' own `permissions` list before adding it under `roles.ultra.allow`.

## Chat

When `chat.enabled` is true, Style formats chat as `{badge}{tag}{name} » {message}` using the player's picks.
Colours apply to plain text only: players cannot type MiniMessage or `&` codes. If another chat plugin already
formats chat, set `chat.enabled: false` and use `%style_prefix%` there instead.

## Files

| File | What it holds |
|---|---|
| `config.yml` | Ranks, colours, tags, messages |
| `profiles.yml` | Each player's tag, colour and last bonus month |
| `data-backups/` | Automatic copies kept before a data format change |

## Error log

If something goes wrong, the plugin writes it to `plugins/ArkaSMPStyle/logs/errors.log` (and a short line to the console):
a numbered entry with the time, versions, which feature failed, who was involved and the full stack trace. Warnings and
errors the plugin prints, and server errors that come from its code, are copied there automatically. Players who hit a
crashing command see an error number to give to an admin. The same problem is written at most 5 times a minute, and the
file rotates at 2 MB. To send a problem for diagnosis, send that one file. In game, an admin can run the `errors`
sub-command of the plugin's admin command to see the latest entries (`errors test` writes a test entry).

## Building

```
mvn package
```

Needs ArkaSMPCore 1.3.0 installed locally (`mvn install` in the Core folder).

## Licence

AGPL-3.0. See `LICENSE`.
