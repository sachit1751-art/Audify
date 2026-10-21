import os, re, sys

CWD = os.getcwd()

def read(p):
    with open(p, "r", newline="") as f:
        return f.read()

def write(p, c):
    with open(p, "w", newline="\n") as f:
        f.write(c)

def ensure_dir(p):
    d = os.path.dirname(p)
    if d and not os.path.isdir(d):
        os.makedirs(d)

# ============================================================
# FIX 1 — ArtistNameAliases.kt: add 3-arg overload for ArtistNameAliases
# ============================================================
p = "app/src/main/kotlin/com/sachit/music/utils/ArtistNameAliases.kt"
s = read(p)
if "aliases: ArtistNameAliases" not in s:
    needle = "    fun serialize()"
    assert needle in s, "serialize() marker not found"
    overload = (
        "    fun resolve(\n"
        "        aliases: ArtistNameAliases,\n"
        "        artistId: String?,\n"
        "        fallback: String,\n"
        "    ): String = resolve(aliases._aliases.value, artistId, fallback)\n\n"
    )
    s = s.replace(needle, overload + needle, 1)
    write(p, s)
    print("OK ArtistNameAliases.kt — added 3-arg overload")
else:
    print("SKIP ArtistNameAliases.kt — already has overload")

# ============================================================
# FIX 2 — Items.kt: add onLongClick + shapes to SongListItem signature
# ============================================================
p = "app/src/main/kotlin/com/sachit/music/ui/component/Items.kt"
s = read(p)
if "onLongClick" not in s:
    needle = ("    isSwipeable: Boolean = true,\n"
              "    trailingContent: @Composable RowScope.() -> Unit = {},")
    repl = ("    isSwipeable: Boolean = true,\n"
            "    onLongClick: (() -> Unit)? = null,\n"
            "    shapes: RoundedCornerShape = RoundedCornerShape(12.dp),\n"
            "    trailingContent: @Composable RowScope.() -> Unit = {},")
    assert needle in s, "SongListItem signature needle not found"
    s = s.replace(needle, repl, 1)
    if "import androidx.compose.foundation.shape.RoundedCornerShape" not in s:
        # Insert import after the last import line (before first blank line + non-import)
        idx = s.find("\n\nfun ")
        if idx == -1:
            idx = s.find("\n\n//")
        if idx == -1:
            idx = s.find("\n\ndata ")
        if idx == -1:
            idx = s.find("\n\nclass ")
        if idx == -1:
            idx = s.find("\n\nobject ")
        if idx > 0:
            s = s[:idx] + "import androidx.compose.foundation.shape.RoundedCornerShape\n" + s[idx:]
    write(p, s)
    print("OK Items.kt — added onLongClick + shapes + RoundedCornerShape import")
else:
    print("SKIP Items.kt — already has onLongClick")

# ============================================================
# FIX 3 — Remove onLongClick= / shapes= from callers that don't accept them
#          (these were added by the 29-file bundle but the component signatures
#           were not updated to accept them)
# ============================================================
screens = [
    "app/src/main/kotlin/com/sachit/music/ui/screens/playlist/AutoPlaylistScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/playlist/LocalPlaylistScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/playlist/OnlinePlaylistScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/playlist/TopPlaylistScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/playlist/CachePlaylistScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/HistoryScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/StatsScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/ChartsScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/HomeScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/artist/ArtistScreen.kt",
]
fixed_any = False
for p in screens:
    if not os.path.exists(p):
        continue
    s = read(p)
    before = s
    # Remove onLongClick = { ... } named argument blocks
    s = re.sub(
        r",\s*\n(\s*)onLongClick\s*=\s*\{.*?\n\1\}",
        "",
        s,
        flags=re.DOTALL
    )
    # Remove shapes = ... named arguments (shapes = RoundedCornerShape(...))
    s = re.sub(
        r",\s*\n(\s*)shapes\s*=\s*[^{};]*?\n\1\}?",
        "",
        s,
        flags=re.DOTALL
    )
    if s != before:
        write(p, s)
        print(f"OK {os.path.basename(p)} — removed onLongClick/shapes")
        fixed_any = True
if not fixed_any:
    print("SKIP screen onLongClick/shapes removal — nothing to remove")

# ============================================================
# FIX 4 — Remove custom IconButton imports (they shadow material3 IconButton
#          which already has onLongClick). The diff showed these files had:
#            -import com.sachit.music.ui.component.IconButton
#          and now use material3 IconButton (via wildcard import).
#          Remove the custom import so material3's is used.
# ============================================================
iconbutton_files = [
    "app/src/main/kotlin/com/sachit/music/ui/screens/settings/StorageSettings.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/settings/UpdaterSettings.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/settings/integrations/DiscordSettings.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/settings/integrations/IntegrationScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/settings/integrations/LastFMSettings.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/settings/integrations/ListenTogetherSettings.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/ListenTogetherScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/HistoryScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/StatsScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/ChartsScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/artist/ArtistScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/playlist/AutoPlaylistScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/playlist/LocalPlaylistScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/playlist/OnlinePlaylistScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/playlist/TopPlaylistScreen.kt",
    "app/src/main/kotlin/com/sachit/music/ui/screens/playlist/CachePlaylistScreen.kt",
]
removed = 0
for p in iconbutton_files:
    if not os.path.exists(p):
        continue
    s = read(p)
    if "import com.sachit.music.ui.component.IconButton" in s:
        s = s.replace("import com.sachit.music.ui.component.IconButton\n", "")
        write(p, s)
        removed += 1
        print(f"OK {os.path.basename(p)} — removed custom IconButton import")
if removed == 0:
    print("SKIP IconButton import removal — none found")

# ============================================================
# FIX 5 — NavigationBuilder.kt: check BackupAndRestoreScreen signature
#          The error says "No parameter with name onNavigateUp found" etc.
#          This means the component signature changed. Let's inspect and fix.
# ============================================================
p = "app/src/main/kotlin/com/sachit/music/ui/screens/NavigationBuilder.kt"
s = read(p)
if "BackupAndRestoreScreen" in s:
    bp = "app/src/main/kotlin/com/sachit/music/ui/screens/settings/BackupAndRestore.kt"
    if os.path.exists(bp):
        bs = read(bp)
        m = re.search(r"@Composable\s*\n\s*fun\s+BackupAndRestoreScreen\s*\((.*?)\)", bs, re.DOTALL)
        if m:
            sig = m.group(1)
            print(f"INFO NavigationBuilder — BackupAndRestoreScreen signature: {sig[:100]}")
            # Does it have onNavigateUp? If not, NavigationBuilder passes it wrongly.
            if "onNavigateUp" not in sig:
                print("WARN NavigationBuilder — BackupAndRestoreScreen lacks onNavigateUp param")
        else:
            print("WARN NavigationBuilder — could not parse BackupAndRestoreScreen signature")
    else:
        print("WARN NavigationBuilder — BackupAndRestore.kt not found")
else:
    print("SKIP NavigationBuilder.kt — no BackupAndRestoreScreen reference")

# ============================================================
# FIX 6 — BackupRestoreViewModel.kt: ensure ArtistNameAliases import
# ============================================================
p = "app/src/main/kotlin/com/sachit/music/viewmodels/BackupRestoreViewModel.kt"
if os.path.exists(p):
    s = read(p)
    if ("serialize" in s or "deserialize" in s or "restore" in s) and \
       "import com.sachit.music.utils.ArtistNameAliases" not in s:
        # Add import before first top-level declaration
        idx = -1
        for marker in ["\n\nclass ", "\n\nfun ", "\n\nobject ", "\n\ninterface "]:
            j = s.find(marker)
            if j > 0 and (idx == -1 or j < idx):
                idx = j
        if idx > 0:
            s = s[:idx] + "import com.sachit.music.utils.ArtistNameAliases\n" + s[idx:]
            write(p, s)
            print("OK BackupRestoreViewModel.kt — added ArtistNameAliases import")
        else:
            print("WARN BackupRestoreViewModel.kt — could not find insertion point for import")
    else:
        print("SKIP BackupRestoreViewModel.kt — import present or no ArtistNameAliases usage")

# ============================================================
# FIX 7 — SettingsScreen.kt: investigate ColorToken/ShapeToken/TypographyToken
#          These errors are puzzling. Let me check if the Material3SettingsItem
#          icon parameter type changed. If it's now something other than Painter?,
#          we need to fix the callers.
# ============================================================
p = "app/src/main/kotlin/com/sachit/music/ui/screens/settings/SettingsScreen.kt"
if os.path.exists(p):
    s = read(p)
    # Check what icon parameter type Material3SettingsItem expects NOW
    mg_p = "app/src/main/kotlin/com/sachit/music/ui/component/Material3SettingsGroup.kt"
    if os.path.exists(mg_p):
        mg = read(mg_p)
        m = re.search(r"data class Material3SettingsItem\([^)]*\)", mg, re.DOTALL)
        if m:
            print(f"INFO SettingsScreen — Material3SettingsItem: {m.group(0)[:120]}")
            if "icon: Painter?" in m.group(0):
                print("INFO SettingsScreen — icon is Painter? — should accept painterResource()")
            else:
                print(f"WARN SettingsScreen — icon type changed to something else!")
    if "ColorToken" in s or "ShapeToken" in s or "TypographyToken" in s:
        print("INFO SettingsScreen — tokens found in file")
    else:
        print("INFO SettingsScreen — tokens NOT in file (error is puzzling)")

print("\n=== ALL FIXES DONE ===")
