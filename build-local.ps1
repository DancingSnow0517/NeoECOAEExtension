param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArguments
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$javaCandidates = @(
    $env:NEOECOAE_JAVA17_HOME,
    'C:\Program Files\Microsoft\jdk-17.0.10.7-hotspot',
    $env:JAVA_HOME
) | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe')) }

$selectedJava = $null
foreach ($candidate in $javaCandidates) {
    $releaseFile = Join-Path $candidate 'release'
    if ((Test-Path -LiteralPath $releaseFile) -and
        ((Get-Content -LiteralPath $releaseFile -Raw) -match 'JAVA_VERSION="17[\."]')) {
        $selectedJava = $candidate
        break
    }
}
if (-not $selectedJava) {
    throw 'Set NEOECOAE_JAVA17_HOME to an installed Java 17 JDK.'
}

if (-not $GradleArguments) { $GradleArguments = @('build') }
$env:JAVA_HOME = $selectedJava
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$savedErrorPreference = $ErrorActionPreference
try {
    # Windows PowerShell wraps javac/Gradle stderr notes as NativeCommandError.
    # Gradle's exit code, rather than the output stream used by a note, decides success.
    $ErrorActionPreference = 'Continue'
    & (Join-Path $projectRoot 'gradlew.bat') "-Dorg.gradle.java.home=$selectedJava" @GradleArguments 2>&1 |
        ForEach-Object { $_.ToString() }
    $gradleExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $savedErrorPreference
}
exit $gradleExitCode
