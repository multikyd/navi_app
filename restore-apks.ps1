param(
    [ValidateSet('all', 'tmap', 'naver-map')][string]$App = 'all',
    [string]$OutputDirectory = (Join-Path $PSScriptRoot 'dist'),
    [switch]$VerifyOnly
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'manifest.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($manifest.schema_version -ne 1) { throw 'Unsupported package manifest.' }
$repoRoot = [IO.Path]::GetFullPath($PSScriptRoot).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
function Get-PackagePath([string]$relative) {
    if ($relative -notmatch '^packages/[A-Za-z0-9._-]+$') { throw 'Invalid package path.' }
    $full = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot $relative))
    if (-not $full.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Package escaped repository.' }
    return $full
}
function Assert-File([string]$path, [long]$length, [string]$hash) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing file: $path" }
    if ((Get-Item -LiteralPath $path).Length -ne $length) { throw "File size mismatch: $path" }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash.ToLowerInvariant()) { throw "SHA256 mismatch: $path" }
}
$selected = @($manifest.apps | Where-Object { $App -eq 'all' -or $_.id -eq $App })
if ($selected.Count -eq 0) { throw 'No matching application.' }
foreach ($item in $selected) {
    Write-Host ("Verifying {0}..." -f $item.id)
    if ($item.apk.filename -notmatch '^[A-Za-z0-9._-]+\.apk$') { throw 'Invalid APK filename.' }
    foreach ($part in $item.archive.parts) { Assert-File (Get-PackagePath $part.path) ([long]$part.bytes) $part.sha256 }
    if ($VerifyOnly) { Write-Host ("OK: {0} archive files" -f $item.id); continue }
    $outputRoot = [IO.Path]::GetFullPath($OutputDirectory)
    if (-not (Test-Path -LiteralPath $outputRoot)) { [IO.Directory]::CreateDirectory($outputRoot) | Out-Null }
    $destination = Join-Path $outputRoot $item.apk.filename
    if (Test-Path -LiteralPath $destination) {
        Assert-File $destination ([long]$item.apk.bytes) $item.apk.sha256
        Write-Host ("Already restored: {0}" -f $destination)
        continue
    }
    $archiveTemp = $null
    $apkTemp = Join-Path $outputRoot ('.' + $item.id + '-' + [Guid]::NewGuid().ToString('N') + '.partial')
    $zip = $null
    try {
        if (@($item.archive.parts).Count -eq 1) {
            $archivePath = Get-PackagePath $item.archive.parts[0].path
        } else {
            $archiveTemp = Join-Path $outputRoot ('.' + $item.id + '-' + [Guid]::NewGuid().ToString('N') + '.zip.partial')
            $target = [IO.File]::Open($archiveTemp, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
            try {
                foreach ($part in $item.archive.parts) {
                    $source = [IO.File]::OpenRead((Get-PackagePath $part.path))
                    try { $source.CopyTo($target, 1048576) } finally { $source.Dispose() }
                }
            } finally { $target.Dispose() }
            $archivePath = $archiveTemp
        }
        Assert-File $archivePath ([long]$item.archive.bytes) $item.archive.sha256
        $stream = [IO.File]::OpenRead($archivePath)
        try {
            $zip = New-Object IO.Compression.ZipArchive($stream, [IO.Compression.ZipArchiveMode]::Read, $false)
            if ($zip.Entries.Count -ne 1 -or $zip.Entries[0].FullName -ne $item.apk.filename) { throw 'Unexpected ZIP content.' }
            $entry = $zip.Entries[0]
            if ($entry.Length -ne [long]$item.apk.bytes) { throw 'Unexpected APK length.' }
            $input = $entry.Open()
            try {
                $target = [IO.File]::Open($apkTemp, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write)
                try { $input.CopyTo($target, 1048576) } finally { $target.Dispose() }
            } finally { $input.Dispose() }
        } finally { if ($null -ne $zip) { $zip.Dispose() }; $stream.Dispose() }
        Assert-File $apkTemp ([long]$item.apk.bytes) $item.apk.sha256
        Move-Item -LiteralPath $apkTemp -Destination $destination
        Write-Host ("Restored: {0}" -f $destination)
    } finally {
        if (Test-Path -LiteralPath $apkTemp) { Remove-Item -LiteralPath $apkTemp }
        if ($archiveTemp -and (Test-Path -LiteralPath $archiveTemp)) { Remove-Item -LiteralPath $archiveTemp }
    }
}
Write-Host 'Done.'
