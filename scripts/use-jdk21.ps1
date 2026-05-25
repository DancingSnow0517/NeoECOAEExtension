param(
    [string]$JdkHome
)

$candidates = @()
if ($JdkHome) { $candidates += $JdkHome }
if ($env:JAVA_HOME_21_X64) { $candidates += $env:JAVA_HOME_21_X64 }

$roots = @(
    'C:\Program Files\Microsoft',
    'C:\Program Files\Eclipse Adoptium',
    'C:\Program Files\Zulu',
    'C:\Program Files\Java'
)

foreach ($root in $roots) {
    if (Test-Path $root) {
        $candidates += Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match '(^|[-_])21([._-]|$)|jdk-21|zulu-21' } |
            Sort-Object FullName -Descending |
            Select-Object -ExpandProperty FullName
    }
}

$resolved = $candidates |
    Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\java.exe')) } |
    Select-Object -First 1

if (-not $resolved) {
    Write-Error 'Unable to locate a JDK 21 installation. Pass -JdkHome or set JAVA_HOME_21_X64.'
    return
}

$env:JAVA_HOME = $resolved
$env:Path = "$resolved\bin;$env:Path"

Write-Host "JAVA_HOME set to $env:JAVA_HOME"
& java -version
Write-Host ''
Write-Host 'This script updates the current shell only. Dot-source it with:'
Write-Host '. .\scripts\use-jdk21.ps1'
