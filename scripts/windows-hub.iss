[Setup]
AppId=JVN.EngineHub
AppName=JVN Engine Hub
AppPublisher=JVN
AppVersion={#HubVersion}
VersionInfoVersion={#HubVersion}.0
DefaultDirName={localappdata}\Programs\JVN Engine Hub
PrivilegesRequired=lowest
ArchitecturesAllowed={#TargetArch}
ArchitecturesInstallIn64BitMode={#TargetArch}
DisableProgramGroupPage=yes
SetupIconFile={#HubIcon}
UninstallDisplayIcon={app}\JVN Engine Hub.exe
OutputDir={#OutputDirPath}
OutputBaseFilename={#InstallerName}
WizardStyle=modern
Compression=lzma2/fast
SolidCompression=yes

[Files]
Source: "{#ImageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\JVN Engine Hub"; Filename: "{app}\JVN Engine Hub.exe"
