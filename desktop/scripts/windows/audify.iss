#define AppName "Audify"
#define AppPublisher "Audify"
#define AppExeName "audify.exe"
#define AppVersion GetEnv("AUDIFY_VERSION")
#define SourceExe GetEnv("AUDIFY_EXE")
#define OutputDir GetEnv("AUDIFY_DIST")
; x64compatible for the x64 build, arm64 for the ARM one
#define Arch GetEnv("AUDIFY_ARCH")
; Audify-Setup for the x64 build, Audify-Setup-arm64 for the ARM one
#define SetupName GetEnv("AUDIFY_SETUP")

[Setup]
AppId={{8D65C17E-79E8-46D7-9A37-42E85E73F738}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\{#AppExeName}
OutputDir={#OutputDir}
OutputBaseFilename={#SetupName}
SetupIconFile=..\..\assets\windows\audify.ico
Compression=lzma2
SolidCompression=yes
ArchitecturesAllowed={#Arch}
ArchitecturesInstallIn64BitMode={#Arch}
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=commandline dialog
WizardStyle=modern
ChangesAssociations=yes

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; GroupDescription: "Additional shortcuts:"

[Files]
Source: "{#SourceExe}"; DestDir: "{app}"; DestName: "{#AppExeName}"; Flags: ignoreversion
Source: "..\..\COPYING"; DestDir: "{app}"; DestName: "LICENSE"; Flags: ignoreversion
Source: "..\..\THIRD-PARTY.md"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\{#AppExeName}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon; Check: not SilentUpgrade

; Lists Audify in "Open With" and in Settings > Apps > Default apps for the file types
; it plays, without automatically becoming the default handler for any of them.
; MultiSelectModel=Player is the key Explorer honors to invoke the app once
; with every selected file passed as its own argument, instead of once per file.
[Registry]
Root: HKA; Subkey: "Software\Audify"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Audify\Capabilities"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Audify\Capabilities"; ValueType: string; ValueName: "ApplicationName"; ValueData: "{#AppName}"; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities"; ValueType: string; ValueName: "ApplicationDescription"; ValueData: "A native, familiar music streaming client"; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities"; ValueType: string; ValueName: "ApplicationIcon"; ValueData: "{app}\{#AppExeName},0"; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\RegisteredApplications"; ValueType: string; ValueName: "{#AppName}"; ValueData: "Software\Audify\Capabilities"; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}"; ValueType: string; ValueName: "FriendlyAppName"; ValueData: "{#AppName}"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
; .mp3
Root: HKA; Subkey: "Software\Classes\.mp3"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.mp3\OpenWithProgids"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.mp3\OpenWithProgids"; ValueType: string; ValueName: "Audify.mp3"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Audify.mp3"; ValueType: string; ValueName: ""; ValueData: "MP3 Audio"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Audify.mp3"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Audify.mp3\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\Audify.mp3\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\SupportedTypes"; ValueType: string; ValueName: ".mp3"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities\FileAssociations"; ValueType: string; ValueName: ".mp3"; ValueData: "Audify.mp3"; Flags: uninsdeletevalue
; .flac
Root: HKA; Subkey: "Software\Classes\.flac"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.flac\OpenWithProgids"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.flac\OpenWithProgids"; ValueType: string; ValueName: "Audify.flac"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Audify.flac"; ValueType: string; ValueName: ""; ValueData: "FLAC Audio"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Audify.flac"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Audify.flac\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\Audify.flac\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\SupportedTypes"; ValueType: string; ValueName: ".flac"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities\FileAssociations"; ValueType: string; ValueName: ".flac"; ValueData: "Audify.flac"; Flags: uninsdeletevalue
; .m4a
Root: HKA; Subkey: "Software\Classes\.m4a"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.m4a\OpenWithProgids"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.m4a\OpenWithProgids"; ValueType: string; ValueName: "Audify.m4a"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Audify.m4a"; ValueType: string; ValueName: ""; ValueData: "MPEG-4 Audio"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Audify.m4a"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Audify.m4a\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\Audify.m4a\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\SupportedTypes"; ValueType: string; ValueName: ".m4a"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities\FileAssociations"; ValueType: string; ValueName: ".m4a"; ValueData: "Audify.m4a"; Flags: uninsdeletevalue
; .mp4 (no default apps entry, since most .mp4 files are videos)
Root: HKA; Subkey: "Software\Classes\.mp4"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.mp4\OpenWithProgids"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.mp4\OpenWithProgids"; ValueType: string; ValueName: "Audify.mp4"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Audify.mp4"; ValueType: string; ValueName: ""; ValueData: "MPEG-4 Audio"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Audify.mp4"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Audify.mp4\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\Audify.mp4\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\SupportedTypes"; ValueType: string; ValueName: ".mp4"; ValueData: ""; Flags: uninsdeletevalue
; .aac
Root: HKA; Subkey: "Software\Classes\.aac"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.aac\OpenWithProgids"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.aac\OpenWithProgids"; ValueType: string; ValueName: "Audify.aac"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Audify.aac"; ValueType: string; ValueName: ""; ValueData: "AAC Audio"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Audify.aac"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Audify.aac\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\Audify.aac\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\SupportedTypes"; ValueType: string; ValueName: ".aac"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities\FileAssociations"; ValueType: string; ValueName: ".aac"; ValueData: "Audify.aac"; Flags: uninsdeletevalue
; .ogg
Root: HKA; Subkey: "Software\Classes\.ogg"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.ogg\OpenWithProgids"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.ogg\OpenWithProgids"; ValueType: string; ValueName: "Audify.ogg"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Audify.ogg"; ValueType: string; ValueName: ""; ValueData: "Ogg Audio"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Audify.ogg"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Audify.ogg\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\Audify.ogg\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\SupportedTypes"; ValueType: string; ValueName: ".ogg"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities\FileAssociations"; ValueType: string; ValueName: ".ogg"; ValueData: "Audify.ogg"; Flags: uninsdeletevalue
; .oga
Root: HKA; Subkey: "Software\Classes\.oga"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.oga\OpenWithProgids"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.oga\OpenWithProgids"; ValueType: string; ValueName: "Audify.oga"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Audify.oga"; ValueType: string; ValueName: ""; ValueData: "Ogg Audio"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Audify.oga"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Audify.oga\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\Audify.oga\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\SupportedTypes"; ValueType: string; ValueName: ".oga"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities\FileAssociations"; ValueType: string; ValueName: ".oga"; ValueData: "Audify.oga"; Flags: uninsdeletevalue
; .opus
Root: HKA; Subkey: "Software\Classes\.opus"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.opus\OpenWithProgids"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.opus\OpenWithProgids"; ValueType: string; ValueName: "Audify.opus"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Audify.opus"; ValueType: string; ValueName: ""; ValueData: "Opus Audio"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Audify.opus"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Audify.opus\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\Audify.opus\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\SupportedTypes"; ValueType: string; ValueName: ".opus"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities\FileAssociations"; ValueType: string; ValueName: ".opus"; ValueData: "Audify.opus"; Flags: uninsdeletevalue
; .wav
Root: HKA; Subkey: "Software\Classes\.wav"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.wav\OpenWithProgids"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.wav\OpenWithProgids"; ValueType: string; ValueName: "Audify.wav"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Audify.wav"; ValueType: string; ValueName: ""; ValueData: "WAVE Audio"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Audify.wav"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Audify.wav\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\Audify.wav\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\SupportedTypes"; ValueType: string; ValueName: ".wav"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities\FileAssociations"; ValueType: string; ValueName: ".wav"; ValueData: "Audify.wav"; Flags: uninsdeletevalue
; .aiff
Root: HKA; Subkey: "Software\Classes\.aiff"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.aiff\OpenWithProgids"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.aiff\OpenWithProgids"; ValueType: string; ValueName: "Audify.aiff"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Audify.aiff"; ValueType: string; ValueName: ""; ValueData: "AIFF Audio"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Audify.aiff"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Audify.aiff\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\Audify.aiff\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\SupportedTypes"; ValueType: string; ValueName: ".aiff"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities\FileAssociations"; ValueType: string; ValueName: ".aiff"; ValueData: "Audify.aiff"; Flags: uninsdeletevalue
; .aif
Root: HKA; Subkey: "Software\Classes\.aif"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.aif\OpenWithProgids"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.aif\OpenWithProgids"; ValueType: string; ValueName: "Audify.aif"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Audify.aif"; ValueType: string; ValueName: ""; ValueData: "AIFF Audio"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Audify.aif"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Audify.aif\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\Audify.aif\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\SupportedTypes"; ValueType: string; ValueName: ".aif"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities\FileAssociations"; ValueType: string; ValueName: ".aif"; ValueData: "Audify.aif"; Flags: uninsdeletevalue
; .aifc
Root: HKA; Subkey: "Software\Classes\.aifc"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.aifc\OpenWithProgids"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.aifc\OpenWithProgids"; ValueType: string; ValueName: "Audify.aifc"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Audify.aifc"; ValueType: string; ValueName: ""; ValueData: "AIFF-C Audio"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Audify.aifc"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Audify.aifc\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\Audify.aifc\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\SupportedTypes"; ValueType: string; ValueName: ".aifc"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities\FileAssociations"; ValueType: string; ValueName: ".aifc"; ValueData: "Audify.aifc"; Flags: uninsdeletevalue
; .webm
Root: HKA; Subkey: "Software\Classes\.webm"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.webm\OpenWithProgids"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.webm\OpenWithProgids"; ValueType: string; ValueName: "Audify.webm"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Audify.webm"; ValueType: string; ValueName: ""; ValueData: "WebM Audio"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Audify.webm"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Audify.webm\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\Audify.webm\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\SupportedTypes"; ValueType: string; ValueName: ".webm"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities\FileAssociations"; ValueType: string; ValueName: ".webm"; ValueData: "Audify.webm"; Flags: uninsdeletevalue
; .mka
Root: HKA; Subkey: "Software\Classes\.mka"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.mka\OpenWithProgids"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.mka\OpenWithProgids"; ValueType: string; ValueName: "Audify.mka"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Audify.mka"; ValueType: string; ValueName: ""; ValueData: "Matroska Audio"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Audify.mka"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Audify.mka\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\Audify.mka\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\SupportedTypes"; ValueType: string; ValueName: ".mka"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities\FileAssociations"; ValueType: string; ValueName: ".mka"; ValueData: "Audify.mka"; Flags: uninsdeletevalue
; .wv
Root: HKA; Subkey: "Software\Classes\.wv"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.wv\OpenWithProgids"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.wv\OpenWithProgids"; ValueType: string; ValueName: "Audify.wv"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Audify.wv"; ValueType: string; ValueName: ""; ValueData: "WavPack Audio"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Audify.wv"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Audify.wv\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\Audify.wv\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\SupportedTypes"; ValueType: string; ValueName: ".wv"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities\FileAssociations"; ValueType: string; ValueName: ".wv"; ValueData: "Audify.wv"; Flags: uninsdeletevalue
; .ape
Root: HKA; Subkey: "Software\Classes\.ape"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.ape\OpenWithProgids"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.ape\OpenWithProgids"; ValueType: string; ValueName: "Audify.ape"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\Audify.ape"; ValueType: string; ValueName: ""; ValueData: "Monkey's Audio"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Audify.ape"; ValueType: string; ValueName: "MultiSelectModel"; ValueData: "Player"
Root: HKA; Subkey: "Software\Classes\Audify.ape\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\Audify.ape\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\Applications\{#AppExeName}\SupportedTypes"; ValueType: string; ValueName: ".ape"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Audify\Capabilities\FileAssociations"; ValueType: string; ValueName: ".ape"; ValueData: "Audify.ape"; Flags: uninsdeletevalue
; audify, which no other app claims, so Audify owns the scheme outright
Root: HKA; Subkey: "Software\Classes\audify"; ValueType: string; ValueName: ""; ValueData: "URL:Audify Link"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\audify"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""
Root: HKA; Subkey: "Software\Classes\audify\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\audify\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
; spotify
Root: HKA; Subkey: "Software\Audify\Capabilities\UrlAssociations"; ValueType: string; ValueName: "spotify"; ValueData: "AudifySpotify"; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\AudifySpotify"; ValueType: string; ValueName: ""; ValueData: "Spotify Link"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\AudifySpotify"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""
Root: HKA; Subkey: "Software\Classes\AudifySpotify\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\AudifySpotify\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
; deezer
Root: HKA; Subkey: "Software\Audify\Capabilities\UrlAssociations"; ValueType: string; ValueName: "deezer"; ValueData: "AudifyDeezer"; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\AudifyDeezer"; ValueType: string; ValueName: ""; ValueData: "Deezer Link"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\AudifyDeezer"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""
Root: HKA; Subkey: "Software\Classes\AudifyDeezer\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\AudifyDeezer\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""
; tidal
Root: HKA; Subkey: "Software\Audify\Capabilities\UrlAssociations"; ValueType: string; ValueName: "tidal"; ValueData: "AudifyTidal"; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\AudifyTidal"; ValueType: string; ValueName: ""; ValueData: "TIDAL Link"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\AudifyTidal"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""
Root: HKA; Subkey: "Software\Classes\AudifyTidal\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExeName},0"
Root: HKA; Subkey: "Software\Classes\AudifyTidal\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExeName}"" ""%1"""

; Setup runs elevated, and a [Run] entry inherits that unless it says otherwise: postinstall
; entries default to runasoriginaluser, the relaunch after a silent update does not, and an
; elevated Audify is out of reach for tools like FancyZones that manage windows unelevated.
[Run]
Filename: "{app}\{#AppExeName}"; Description: "Launch {#AppName}"; Flags: nowait postinstall skipifsilent
Filename: "{app}\{#AppExeName}"; Flags: nowait runasoriginaluser; Check: RelaunchRequested

[Code]
function RelaunchRequested: Boolean;
begin
  Result := ExpandConstant('{param:relaunch|0}') = '1';
end;

// A silent run over an existing install is an update from the app or a package manager. It
// leaves the desktop alone, so a shortcut the user deleted or replaced stays that way.
function SilentUpgrade: Boolean;
begin
  Result := WizardSilent and (WizardForm.PrevAppDir <> '');
end;
