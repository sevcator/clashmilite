param(
    [ValidateSet('windows', 'linux', 'darwin', 'android')]
    [string]$TargetOS = 'windows',
    [ValidateSet('amd64', 'arm64', 'arm')]
    [string]$TargetArch = 'amd64'
)

$ErrorActionPreference = 'Stop'
$sourceRevision = 'ab405bad5beeeac8b003bb01f60f134f6df54471'
$gitCommand = Get-Command git -ErrorAction SilentlyContinue
$git = if ($gitCommand) { $gitCommand.Source } elseif (Test-Path 'C:\Program Files\Git\cmd\git.exe') { 'C:\Program Files\Git\cmd\git.exe' } else { throw 'Git is required' }
$sourceDir = Join-Path $PSScriptRoot '_work/mihomo'
$patchFile = Join-Path $PSScriptRoot 'patches/mihomo-reality-client-version.patch'
$outputDir = Join-Path (Split-Path $PSScriptRoot -Parent) 'dist'

if (-not (Test-Path -LiteralPath $sourceDir)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $sourceDir -Parent) | Out-Null
    & $git clone --depth 1 --branch v1.19.31 https://github.com/MetaCubeX/mihomo.git $sourceDir
    if ($LASTEXITCODE -ne 0) { throw 'Unable to fetch Mihomo source' }
}

$actualRevision = (& $git -C $sourceDir rev-parse HEAD).Trim()
if ($actualRevision -ne $sourceRevision) {
    throw "Unexpected Mihomo revision: $actualRevision"
}

if (-not (Select-String -Path (Join-Path $sourceDir 'component/tls/reality.go') -Pattern 'RealityClientVersion' -Quiet)) {
    & $git -C $sourceDir apply --check $patchFile
    if ($LASTEXITCODE -ne 0) { throw 'Core patch does not apply cleanly' }
    & $git -C $sourceDir apply $patchFile
    if ($LASTEXITCODE -ne 0) { throw 'Unable to apply core patch' }
}

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$suffix = if ($TargetOS -eq 'windows') { '.exe' } else { '' }
$outputFile = Join-Path $outputDir "mihomo-lite-$TargetOS-$TargetArch$suffix"
$env:GOOS = $TargetOS
$env:GOARCH = $TargetArch
$env:CGO_ENABLED = '0'
Push-Location $sourceDir
try {
    & go build -tags with_gvisor -trimpath -ldflags '-s -w -X github.com/metacubex/mihomo/constant.Version=1.19.31-lite' -o $outputFile .
    if ($LASTEXITCODE -ne 0) { throw "Core build failed for $TargetOS/$TargetArch" }
} finally {
    Pop-Location
}
Write-Output $outputFile
