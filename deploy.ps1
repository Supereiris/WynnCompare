<#
.SYNOPSIS
    Builds WynnCompare and installs the jar into a Prism Launcher instance.

.EXAMPLE
    .\deploy.ps1            # build + copy into the "Wynncraft" instance
    .\deploy.ps1 -Launch    # ...and start the instance
    .\deploy.ps1 -Instance "Other" -SkipBuild
#>
param(
    [string]$Instance = "Wynncraft",
    [string]$PrismDir = "$env:APPDATA\PrismLauncher",
    [string]$PrismExe = "$env:LOCALAPPDATA\Programs\PrismLauncher\prismlauncher.exe",
    [switch]$SkipBuild,
    [switch]$Launch
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$modsDir = Join-Path $PrismDir "instances\$Instance\minecraft\mods"
if (-not (Test-Path $modsDir)) {
    throw "Mods folder not found: $modsDir (check -Instance / -PrismDir)"
}

if (-not $SkipBuild) {
    # Fall back to Prism's bundled JDK 21 when no JDK is configured
    if (-not $env:JAVA_HOME) {
        $prismJdk = Join-Path $PrismDir "java\java-runtime-delta"
        if (Test-Path (Join-Path $prismJdk "bin\javac.exe")) {
            $env:JAVA_HOME = $prismJdk
            Write-Host "Using Prism's JDK: $prismJdk"
        }
    }

    Write-Host "Building..."
    & .\gradlew.bat build --console=plain -q
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed (exit $LASTEXITCODE)" }
}

$jar = Get-ChildItem "build\libs\wynncompare-*.jar" |
    Where-Object { $_.Name -notlike "*-sources.jar" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $jar) { throw "No built jar found in build\libs" }

# Remove any previously installed WynnCompare jar (any version)
$old = Get-ChildItem $modsDir -Filter "wynncompare-*.jar"
try {
    $old | Remove-Item -Force
    Copy-Item $jar.FullName $modsDir -Force
} catch {
    throw "Could not update the mods folder. Is Minecraft still running? ($($_.Exception.Message))"
}

foreach ($o in $old) { if ($o.Name -ne $jar.Name) { Write-Host "Removed $($o.Name)" } }
Write-Host "Installed $($jar.Name) -> $modsDir" -ForegroundColor Green

if ($Launch) {
    if (-not (Test-Path $PrismExe)) { throw "Prism Launcher not found: $PrismExe (pass -PrismExe)" }
    Write-Host "Launching instance '$Instance'..."
    Start-Process $PrismExe -ArgumentList "--launch", "`"$Instance`""
}
