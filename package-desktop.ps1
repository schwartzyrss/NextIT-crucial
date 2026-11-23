<#
.SYNOPSIS
    NextIT Desktop Packaging Automation Script
    Packages NextIT into a standalone native Windows desktop application with a bundled JRE.

.DESCRIPTION
    1. Compiles and packages dependencies into a self-contained runtime fat JAR.
    2. Invokes jpackage to bundle the application with a modular JRE, custom circular emblem icon,
       and native Windows launcher (NextIT.exe).
    3. Produces a portable standalone desktop package in 'target/dist/NextIT'.

.EXAMPLE
    .\package-desktop.ps1
#>

param(
    [switch]$SkipBuild,
    [switch]$CreateZip
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  NextIT: Standalone Desktop Application Packager" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Locate jpackage
$JPackagePath = $null
if (Get-Command jpackage -ErrorAction SilentlyContinue) {
    $JPackagePath = "jpackage"
} elseif ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\jpackage.exe")) {
    $JPackagePath = "$env:JAVA_HOME\bin\jpackage.exe"
} else {
    $jdkSearch = Get-ChildItem "C:\Program Files\Java" -Filter "jpackage.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jdkSearch) {
        $JPackagePath = $jdkSearch.FullName
    }
}

if (-not $JPackagePath) {
    Write-Error "Could not find 'jpackage.exe'. Please ensure JDK 21+ is installed and configured in PATH or JAVA_HOME."
    exit 1
}

Write-Host "[1/4] Found jpackage: $JPackagePath" -ForegroundColor Green

# 2. Build Fat JAR
if (-not $SkipBuild) {
    Write-Host "[2/4] Building runtime JAR with dependencies via Maven wrapper..." -ForegroundColor Yellow
    Push-Location $ProjectRoot
    try {
        & ".\mvnw.cmd" clean package -DskipTests
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Maven build failed with exit code $LASTEXITCODE."
            exit $LASTEXITCODE
        }
    } finally {
        Pop-Location
    }
} else {
    Write-Host "[2/4] Skipping Maven build step (-SkipBuild requested)..." -ForegroundColor DarkGray
}

$JarPath = Join-Path $ProjectRoot "target\nextit-1.0.0-all.jar"
if (-not (Test-Path $JarPath)) {
    Write-Error "Runtime JAR not found at: $JarPath"
    exit 1
}

$IconPath = Join-Path $ProjectRoot "src\main\resources\com\nextit\icons\nextit.ico"
$OutputDir = Join-Path $ProjectRoot "target\dist"

# 3. Prepare isolated jpackage input directory containing only the fat JAR
$InputStageDir = Join-Path $ProjectRoot "target\jpackage-input"
if (Test-Path $InputStageDir) {
    Remove-Item -Recurse -Force $InputStageDir -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Path $InputStageDir -Force | Out-Null
Copy-Item -Path $JarPath -Destination (Join-Path $InputStageDir "nextit-1.0.0-all.jar") -Force

# 4. Clean previous output directory
if (Test-Path $OutputDir) {
    Write-Host "[3/4] Cleaning previous output directory..." -ForegroundColor Yellow
    Remove-Item -Recurse -Force $OutputDir -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

# 5. Execute jpackage to create standalone app image
Write-Host "[4/4] Generating standalone native desktop application with bundled JRE..." -ForegroundColor Yellow

$jpackageArgs = @(
    "--name", "NextIT",
    "--app-version", "1.0.0",
    "--vendor", "NextIT Team",
    "--description", "NextIT: Career & Academic Hub - Standalone Desktop Client",
    "--input", $InputStageDir,
    "--main-jar", "nextit-1.0.0-all.jar",
    "--main-class", "com.nextit.AppLauncher",
    "--icon", $IconPath,
    "--dest", $OutputDir,
    "--type", "app-image",
    "--java-options", "--enable-native-access=ALL-UNNAMED",
    "--verbose"
)

& $JPackagePath @jpackageArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "jpackage failed with exit code $LASTEXITCODE."
    exit $LASTEXITCODE
}

$AppExe = Join-Path $OutputDir "NextIT\NextIT.exe"
Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "  SUCCESS! NextIT Desktop Application Packaged" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "Standalone Executable: $AppExe" -ForegroundColor White
Write-Host "Application Directory: $(Join-Path $OutputDir 'NextIT')" -ForegroundColor White
Write-Host ""
Write-Host "To launch NextIT:" -ForegroundColor Cyan
Write-Host "  Start-Process '$AppExe'" -ForegroundColor Yellow

if ($CreateZip) {
    $ZipPath = Join-Path $OutputDir "NextIT-1.0.0-windows-x64.zip"
    Write-Host "Compressing application package to $ZipPath..." -ForegroundColor Yellow
    Compress-Archive -Path (Join-Path $OutputDir "NextIT\*") -DestinationPath $ZipPath -Force
    Write-Host "ZIP Distribution Archive: $ZipPath" -ForegroundColor Green
}
