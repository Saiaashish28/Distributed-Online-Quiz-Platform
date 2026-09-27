# Starts the backend for local development on Windows PowerShell.
# Picks a JDK 21+ automatically when JAVA_HOME points to an older JDK.
#   .\run-dev.ps1
#   .\run-dev.ps1 -AdminEmail admin@example.edu -AdminPassword 'choose-a-strong-password'
param(
    [string]$AdminEmail,
    [string]$AdminPassword,
    [string]$InviteCode
)

function Get-JavaMajor([string]$javaHome) {
    $exe = Join-Path $javaHome 'bin\java.exe'
    if (-not (Test-Path $exe)) { return 0 }
    $line = (& cmd /c "`"$exe`" -version 2>&1" | Select-Object -First 1)
    if ($line -match 'version "(\d+)') { return [int]$Matches[1] }
    return 0
}

if (-not $env:JAVA_HOME -or (Get-JavaMajor $env:JAVA_HOME) -lt 21) {
    $candidates = Get-ChildItem -Directory -ErrorAction SilentlyContinue `
        'C:\Program Files\Java\jdk*', 'C:\Program Files\Eclipse Adoptium\jdk*', 'C:\Program Files\Microsoft\jdk*' |
        Where-Object { (Get-JavaMajor $_.FullName) -ge 21 } |
        Sort-Object { Get-JavaMajor $_.FullName } -Descending
    if (-not $candidates) {
        Write-Error "QuizSphere needs JDK 21 or newer. Install one (e.g. Eclipse Temurin 21) and set JAVA_HOME."
        exit 1
    }
    $env:JAVA_HOME = $candidates[0].FullName
    Write-Host "Using JDK at $env:JAVA_HOME (JAVA_HOME pointed to an older JDK)"
}

# A stable local secret keeps you logged in across restarts. Use a real random secret anywhere shared.
if (-not $env:JWT_SECRET_KEY) { $env:JWT_SECRET_KEY = 'local-dev-only-secret-change-me' }
if ($AdminEmail) { $env:BOOTSTRAP_ADMIN_EMAIL = $AdminEmail }
if ($AdminPassword) { $env:BOOTSTRAP_ADMIN_PASSWORD = $AdminPassword }
if ($InviteCode) { $env:ADMIN_INVITE_CODE = $InviteCode }

& "$PSScriptRoot\mvnw.cmd" spring-boot:run
