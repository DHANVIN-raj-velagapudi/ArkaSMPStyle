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
| Cosmetics (UltraCosmetics) | free ones | selection | all safe ones |

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

## Install

1. Install **ArkaSMPCore 1.3.0+** and put `ArkaSMPStyle-1.0.0.jar` in `plugins/`.
2. LuckPerms is needed for ranks. PlaceholderAPI is optional (for TAB and scoreboards). UltraCosmetics is optional.
3. Start the server. `plugins/ArkaSMPStyle/config.yml` is created and kept up to date on updates
   (`config-version: auto`; your `ranks`, `chat-colors` and `tags` sections are never overwritten).

## LuckPerms setup

A rank is a LuckPerms group. Create the groups once:

```
/lp creategroup pro
/lp creategroup ultra
/lp group ultra parent add pro
```

Then give each group its limits. Homes and player warps are read by ArkaSMPCore from these permissions:

```
/lp group pro   permission set arka.homes.limit.8 true
/lp group pro   permission set arka.pwarps.limit.6 true
/lp group pro   permission set arka.warp.free true
/lp group ultra permission set arka.homes.limit.1000 true
/lp group ultra permission set arka.pwarps.limit.13 true
```

The Style plugin itself only checks `group.pro` and `group.ultra`. A player who has both gets Ultra.
Ultra includes everything in Pro.

Adjust the three numbers above if your base limits differ: Pro is "base + 5" homes and "base + 5" player warps,
Ultra is unlimited homes and "base + 12" player warps. Check Core's `config.yml` for your base values.

### Monthly ranks

Give a rank for 30 days with a temporary group, so it expires on its own:

```
/lp user <name> parent addtemp pro 30d accumulate
```

`accumulate` adds to the time left when someone renews early. The exact syntax can differ between LuckPerms
versions: run `/lp user <name> parent addtemp` to see the one on your server. When the group expires the player
is a free player again and their tag and colour simply stop showing. Their picks are kept, so they come back
if the rank is renewed.

### Selling ranks

The plugin does not sell anything. It only reads LuckPerms groups, so any way of running that command works:

* **Tebex:** make a package for each rank and set its command to
  `lp user {username} parent addtemp pro 30d accumulate` (and `ultra`).
  Test one purchase with a Bedrock account before you sell: the name Tebex hands over must match the name
  LuckPerms knows the player by.
* **Discord / by hand:** run the same command from the console.

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

Allowed for paid ranks: hats, emotes, suits, particle effects, projectile effects, death effects, pets (no item
drops) and gadgets that cannot grief or move other players. Leave out anything that gives a gameplay edge.
Permission nodes look like `ultracosmetics.<category>.<name>`. Look up the exact node of each cosmetic in
UltraCosmetics' own `permissions` list before adding it to a group.

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

## Building

```
mvn package
```

Needs ArkaSMPCore 1.3.0 installed locally (`mvn install` in the Core folder).

## Licence

AGPL-3.0. See `LICENSE`.
