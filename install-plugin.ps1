param(
    [string]$Zip = (Join-Path $PSScriptRoot "build\distributions\xls-viewer-0.4.0.zip"),
    [string]$IdeConfigName = "IntelliJIdea2026.3"
)

$ErrorActionPreference = "Stop"

$pluginsRoot = Join-Path $env:APPDATA "JetBrains\$IdeConfigName\plugins"
if (-not (Test-Path (Split-Path $pluginsRoot -Parent))) {
    Write-Host "IDE config not found: $(Split-Path $pluginsRoot -Parent)" -ForegroundColor Red
    Write-Host "Use -IdeConfigName IntelliJIdea2026.2 or IntelliJIdea2026.3." -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path $Zip)) {
    Write-Host "Plugin archive not found: $Zip" -ForegroundColor Red
    Write-Host "Run 'gradlew buildPlugin' first." -ForegroundColor Yellow
    exit 1
}

$destination = Join-Path $pluginsRoot "xls-viewer"
$lockedJar = Get-ChildItem $destination -Recurse -Filter "*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($lockedJar) {
    try {
        $stream = [IO.File]::Open($lockedJar.FullName, 'Open', 'ReadWrite', 'None')
        $stream.Close()
    } catch {
        Write-Host "The target IDE is running (plugin files are locked). Close it and run this script again." -ForegroundColor Yellow
        exit 1
    }
}

$staging = Join-Path $env:TEMP ("xlsviewer-install-" + [Guid]::NewGuid().ToString("N"))

Expand-Archive -Path $Zip -DestinationPath $staging -Force
if (Test-Path $destination) { Remove-Item $destination -Recurse -Force }
New-Item -ItemType Directory -Path $destination -Force | Out-Null
Copy-Item (Join-Path $staging "xls-viewer\*") $destination -Recurse -Force
Remove-Item $staging -Recurse -Force

Write-Host "XLS Viewer installed to $destination" -ForegroundColor Green
Write-Host "Start IntelliJ IDEA to load the plugin."
