import os
import re

BASE = r"C:\Users\HP\Desktop\New folder\Audify"

files_to_fix = [
    # suspend function calls that need coroutineScope.launch wrapping
    (r"app/src/main/kotlin/com/sachit/music/ui/menu/YouTubeSongMenu.kt", [
        (r"                                            syncUtils\.saveEpisode\(song\.id, shouldBeSaved, setVideoId\)",
         r"                                            coroutineScope.launch {\n                                                syncUtils.saveEpisode(song.id, shouldBeSaved, setVideoId)\n                                            }"),
        (r"                                        syncUtils\.saveEpisode\(song\.id, false, song\.setVideoId\)",
         r"                                        coroutineScope.launch {\n                                            syncUtils.saveEpisode(song.id, false, song.setVideoId)\n                                        }"),
        (r"                                        syncUtils\.saveEpisode\(song\.id, true, null\)",
         r"                                        coroutineScope.launch {\n                                            syncUtils.saveEpisode(song.id, true, null)\n                                        }"),
        (r"                                        syncUtils\.likeSong\(s\)",
         r"                                        coroutineScope.launch {\n                                            syncUtils.likeSong(s)\n                                        }"),
        (r"                                    downloadUtil\.download\(song\)",
         r"                                    coroutineScope.launch {\n                                        downloadUtil.download(song)\n                                    }"),
    ]),
    (r"app/src/main/kotlin/com/sachit/music/ui/menu/PlayerMenu.kt", [
        (r"                                        downloadUtil\.download\(mediaMetadata\)",
         r"                                        coroutineScope.launch {\n                                            downloadUtil.download(mediaMetadata)\n                                        }"),
    ]),
    (r"app/src/main/kotlin/com/sachit/music/ui/menu/QueueMenu.kt", [
        (r"                                    downloadUtil\.download\(mediaMetadata\)",
         r"                                    coroutineScope.launch {\n                                        downloadUtil.download(mediaMetadata)\n                                    }"),
    ]),
    (r"app/src/main/kotlin/com/sachit/music/ui/menu/YouTubeSelectionSongMenu.kt", [
        (r"                                    songSelection\.forEach \{ downloadUtil\.download\(it\) \}",
         r"                                    songSelection.forEach { coroutineScope.launch { downloadUtil.download(it) } }"),
        (r"                                        syncUtils\.likeSong\(songEntity\)",
         r"                                        coroutineScope.launch {\n                                            syncUtils.likeSong(songEntity)\n                                        }"),
    ]),
    (r"app/src/main/kotlin/com/sachit/music/ui/menu/SelectionSongsMenu.kt", [
        (r"                                            songSelection\.forEach \{ downloadUtil\.download\(it\) \}",
         r"                                            songSelection.forEach { coroutineScope.launch { downloadUtil.download(it) } }"),
        (r"                                                syncUtils\.likeSong\(s\)",
         r"                                                coroutineScope.launch {\n                                                    syncUtils.likeSong(s)\n                                                }"),
    ]),
    (r"app/src/main/kotlin/com/sachit/music/ui/screens/playlist/LocalPlaylistScreen.kt", [
        (r"                                                syncUtils\.scheduleRemoveFromPlaylist\(",
         r"                                                coroutineScope.launch {\n                                                    syncUtils.scheduleRemoveFromPlaylist("),
        (r"                                                \) \{[^}]*\n                                                \}$",
         r"                                                )\n                                                }\n                                                }"),  # close the launch
        (r"                                            downloadUtil\.download\(song\.song\)",
         r"                                            coroutineScope.launch {\n                                                downloadUtil.download(song.song)\n                                            }"),
    ]),
]

for rel_path, replacements in files_to_fix:
    path = os.path.join(BASE, rel_path)
    if not os.path.exists(path):
        print(f"SKIP: {rel_path} not found")
        continue
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    original = content
    for pattern, repl in replacements:
        content = re.sub(pattern, repl, content)
    if content != original:
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"FIXED: {rel_path}")
    else:
        print(f"NO CHANGE: {rel_path}")
