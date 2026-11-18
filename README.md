# PreSight

See players through walls, and what they are carrying.

Minecraft **1.8.9** + Forge (`11.15.1.2318`). Client-side.

## Keys

| Key | Feature | What it does |
|---|---|---|
| **G** | Player ESP | A bright box around every player, name above it, any distance, through walls |
| **Y** | Gear View | The gear of the player you are looking at, drawn as a panel beside them |
| **/presight** | — | List features, keys and state |

Both keys are **rebindable**: Options → Controls → PreSight. Your choice is saved.

## What Gear View shows, and why it is not a full inventory

A player's 36 inventory slots are **never sent to your client**. The server
transmits only what a player is visibly wearing and holding; that is the whole of
what exists on your side of the connection. So the panel shows those five slots:

```
helmet  chestplate  leggings  boots  held item
```

Each icon is drawn by Minecraft's own item renderer, so it looks exactly like the
real inventory — correct model, correct stack count.

Showing 36 empty boxes would look more like "the inventory" and be a lie. If that is
what you want anyway, it is a one-line change in `GearViewFeature.presentItems()`.

## Install

1. Forge for **1.8.9**.
2. Drop `presight-1.0.0.jar` in `.minecraft/mods/`.
3. Start the game. Press **G**.

Config: `config/presight.cfg` — stores each feature's on/off state and key.

## Architecture

Built to grow. Adding a feature is one class and one line:

```java
// 1. write it
public class TracerFeature implements Feature {
    public String id()          { return "tracer"; }
    public String displayName() { return "Tracers"; }
    public int defaultKey()     { return Keyboard.KEY_T; }

    public void renderWorld(RenderContext ctx) {
        // draw here; ctx gives you the interpolated camera and entity positions
    }
}

// 2. register it
FeatureRegistry.register(new TracerFeature());
```

That is all. The keybinding appears in Controls, the on/off state persists to the
config, the toggle chat message works, and `/presight toggle tracer` works.

```
com.mohamed.presight
├── PreSight                 mod entry point, config file
├── feature/
│   ├── Feature              one ability: id, key, state, two render hooks
│   └── FeatureRegistry      the list; nothing else knows which features exist
├── render/
│   ├── RenderContext        camera position + interpolation, computed once per frame
│   ├── Draw                 box / text3d / rect / outline primitives
│   ├── Projection           world → screen via the live GL matrices
│   └── ScreenContext        HUD helpers, item icons
├── esp/PlayerEspFeature
├── gear/GearViewFeature
│   └── GearSnapshot         what a client can actually read about a player
├── keybind/Keybinds         bindings generated from the registry
├── config/ConfigStore       persistence
├── command/PreSightCommand  /presight
└── client/PreSightClient    wiring + the single world render pass
```

Rules this code follows, so it stays that way:

- **One world render pass.** The camera maths happens once per frame and is handed
  to every feature as a `RenderContext`. Each feature recomputing it is how the
  overlays end up one frame apart from each other.
- **A feature cannot break the frame.** Each `renderWorld` is wrapped: a throw logs
  and skips, it does not kill rendering.
- **Client-only classes never load on a server.** `@SideOnly(Side.CLIENT)` on the
  package entry points, and registration is guarded by `event.getSide()`.
- **No feature owns global GL state.** `Draw` pushes and restores everything it
  touches. A leaked depth mask is what makes an entire HUD disappear.

## Build

```bash
./build.sh          # -> build/presight-1.0.0.jar
```

### Why there is no Gradle

The 2017 Forge MDK cannot build on a current host, and the failure looks like your
mistake. Four separate reasons:

| What | Why |
|---|---|
| `jcenter()` | Shut down 2021; every artifact 404s |
| `http://files.minecraftforge.net/maven` | plain http, host moved |
| Gradle 2.14 (shipped) | **SIGSEGV inside glibc `setenv`** from `native-platform.so` |
| Gradle 4.10.3 | same crash |

The crash log names it:

```
SIGSEGV (0xb)   Problematic frame:  C  [libc.so.6+0x18badd]
Java_net_rubygrapefruit_platform_internal_jni_PosixProcessFunctions_setEnvironmentVariable
```

`build.sh` replaces the toolchain. Two translations, done explicitly:

```
1. compile   javac against deobfuscated MC + Forge + LWJGL  (readable MCP names)
2. remap     FART: MCP → SRG   ← what "reobf" means, and what makes the jar load
3. pack
```

Step 2 is the one that matters. Forge 1.8.9 runs vanilla under **SRG** names
(`func_77624_a`), not readable MCP names (`addInformation`). Ship readable names and
the jar compiles, loads, then throws `NoSuchMethodError` the first time it touches
the game — an error with no build-time symptom at all.

`tools/verify_symbols.py` checks exactly that, and it runs on every build: every
`func_`/`field_` in the jar must exist in that version's own SRG map, and no readable
name may survive.

### Requirements

- JDK **8** (not just a JRE — `javac` is needed)
- `tools/` — the prebuilt maps and libraries are committed so the build is
  reproducible without network access

Rebuilding the maps (only when changing Minecraft version):

```bash
python3 tools/build_maps.py     # needs deobfuscation_data-<ver>.lzma + an MCP csv
```

## 1.8.9 traps this code hit

Keeping them here because they cost the most time:

- **`ICommand` wants `processCommand`.** `execute` is a 1.13+ name.
- **`addInformation` is on `Item`**, not `ItemStack`.
- **`getTagCompound()` is null on a fresh stack** — create and set it, or writes vanish.
- **`stackSize` is a public field.**
- **LWJGL is not on a mod's compile classpath** by default: `GL11` resolves only if
  `lwjgl.jar` is added, and `GLU.gluProject` needs `lwjgl_util.jar` too.
- **`GLU.gluProject` returns a boolean** in LWJGL 2, not an int.
- **`world.getEntitiesWithinAABB` needs Guava on the classpath** (`guava.jar`), which
  the launcher provides but a hand-rolled javac invocation does not.
- **An identity mapping (`CL: X X`) breaks FART** with an NPE inside
  `EnhancedRemapper.computeClass`. Omit it; identity means "no mapping".
- **FART needs the full class hierarchy in its input.** Remap the mod inside a jar
  that also contains Minecraft, then take only your own classes back out.
