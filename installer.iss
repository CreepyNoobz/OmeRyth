; ==============================================================================
; OmeRyth - Script Inno Setup pour création de l'installateur Windows autonome
; ==============================================================================

#define MyAppName "OmeRyth"
#define MyAppVersion "1.2"
#define MyAppPublisher "OmeRyth Studio"
#define MyAppURL "https://github.com/CreepyNoobz/OmeRyth"
#define MyAppExeName "OmeRyth.exe"

[Setup]
AppId={{5D68B661-3A24-4B7F-9B5A-C4E978901234}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
; Support de l'installation par utilisateur standard ou administrateur
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
OutputDir=dist
OutputBaseFilename=OmeRyth_Setup_v1.2
SetupIconFile=logo.ico
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ChangesAssociations=yes
DisableProgramGroupPage=yes

[Languages]
Name: "french"; MessagesFile: "compiler:Languages\French.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
; Exécutables et composants racine
Source: "OmeRyth.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "OmeRyth.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "NativeDialog.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "logo.ico"; DestDir: "{app}"; Flags: ignoreversion
Source: "associer_fichiers_rythmo.bat"; DestDir: "{app}"; Flags: ignoreversion

; Runtime Java (JRE 21 LTS portable complet)
Source: "jre\*"; DestDir: "{app}\jre"; Flags: ignoreversion recursesubdirs createallsubdirs

; Moteur d'export vidéo et d'analyse média FFmpeg
Source: "ffmpeg\*"; DestDir: "{app}\ffmpeg"; Flags: ignoreversion recursesubdirs createallsubdirs

; Moteur de lecture vidéo LibVLC
Source: "vlc\*"; DestDir: "{app}\vlc"; Flags: ignoreversion recursesubdirs createallsubdirs

; Bibliothèques Java
Source: "libs\*"; DestDir: "{app}\libs"; Flags: ignoreversion recursesubdirs createallsubdirs

; Scripts des moteurs d'intelligence artificielle (WhisperX & Demucs)
Source: "whisperx_engine\*"; DestDir: "{app}\whisperx_engine"; Excludes: "__pycache__\*"; Flags: ignoreversion recursesubdirs createallsubdirs

; Modèles de réseaux de neurones (Whisper Faster IA pré-téléchargés pour fonctionnement hors-ligne immédiat)
Source: "whisper\cache\*"; DestDir: "{app}\whisper\cache"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\logo.ico"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\logo.ico"; Tasks: desktopicon

[Registry]
; Association de fichiers .rythmo
Root: HKA; Subkey: "Software\Classes\.rythmo"; ValueType: string; ValueData: "OmeRyth.Project"; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\.rythmo"; ValueType: string; ValueName: "Content Type"; ValueData: "application/x-omeryth"; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\OmeRyth.Project"; ValueType: string; ValueData: "Projet Bande Rythmo OmeRyth"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\OmeRyth.Project\DefaultIcon"; ValueType: string; ValueData: """{app}\logo.ico"",0"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\OmeRyth.Project\shell\open\command"; ValueType: string; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Microsoft\Windows\CurrentVersion\Explorer\FileExts\.rythmo\OpenWithProgids"; ValueType: none; ValueName: "OmeRyth.Project"; Flags: uninsdeletevalue

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
