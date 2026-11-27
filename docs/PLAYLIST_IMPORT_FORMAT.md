<!-- Copyright (c) 2026 Sachit. All rights reserved. Project: AUDIFY -->
# Audify playlist interchange format

Audify accepts a deliberately small, versioned JSON interchange document. The
format is metadata-only: imports are resolved against Audify's playable catalogue
and never open a path or URI embedded in the document.

## Version 1

```json
{
  "format": "audify-playlist",
  "version": 1,
  "name": "Road Mix",
  "tracks": [
    {
      "title": "Track title",
      "artist": "Artist name",
      "album": "Album name",
      "durationMs": 183000,
      "providerId": "source-specific-id",
      "path": "../Music/track.flac"
    }
  ]
}
```

### Fields

- `format` is required and must equal `audify-playlist`.
- `version` is required. Version 1 is currently supported; unknown major versions
  are rejected rather than guessed.
- `name` is optional. If omitted, the user supplies a playlist name at import.
- `tracks` is required and preserves array order and repeated entries.
- Each track requires `title` for matching. `artist`, `album`, and `durationMs`
  are optional metadata used to reduce false matches.
- `providerId` and `path` are provenance only. They are never interpreted as an
  Audify video ID, filesystem path, content URI, or permission grant.

## Limits and safety

- Maximum source file: 5 MiB.
- Maximum tracks: 1,000.
- Maximum field length: 4,096 characters.
- Maximum JSON nesting depth: 16.
- M3U paths are retained only as untrusted text. The selected file's SAF grant
  does not imply access to sibling files, so relative paths are not dereferenced.
- Playlist creation requires an explicit preview confirmation. Unmatched and
  ambiguous entries are never silently selected.
