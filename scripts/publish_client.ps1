# Run from the extracted Public-Ready package after gh auth login.
param([string]$Repository = "ShoreSMP/ShoreMod-Client")
$ErrorActionPreference = "Stop"
function Run-Checked([scriptblock]$Action) { & $Action; if ($LASTEXITCODE -ne 0) { throw "Command failed; publishing stopped." } }
Run-Checked { gh auth status }
$packageRoot = Split-Path -Parent $PSScriptRoot
$source = Join-Path $packageRoot "source/ShoreMod"
$jar = Join-Path $packageRoot "downloads/ShoreMod-1.2.3.jar"
$checksum = "$jar.sha256"
if (!(Test-Path -LiteralPath $source) -or !(Test-Path -LiteralPath $jar)) { throw "Run the helper from the extracted Public-Ready package." }
if (Test-Path -LiteralPath (Join-Path $source ".git")) { throw "Source already has Git history; use a fresh extraction." }
if (Test-Path -LiteralPath (Join-Path $source "plugin")) { throw "Private plugin found; publishing stopped." }
$expected = ((Get-Content -LiteralPath $checksum -Raw).Trim() -split "\s+")[0]
if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne $expected) { throw "JAR checksum mismatch." }
Run-Checked { git -C $source init -b main }
Run-Checked { git -C $source add --all }
Run-Checked { git -C $source commit -m "Publish ShoreMod 1.2.3 client source under MIT" }
# Creates a NEW public repository. Existing repositories are never overwritten.
Run-Checked { gh repo create $Repository --public --source $source --push --description "Open-source ShoreMod Fabric client" }
$notes = Join-Path $packageRoot "RELEASE.md"
Run-Checked { gh release create v1.2.3 $jar $checksum --repo $Repository --title "ShoreMod 1.2.3" --notes-file $notes }
$readme = Join-Path $source "README.md"
$download = "https://github.com/$Repository/releases/download/v1.2.3/ShoreMod-1.2.3.jar"
Add-Content -LiteralPath $readme -Value "`n## Latest release`n`n[Download ShoreMod 1.2.3]($download)`n"
Run-Checked { git -C $source add README.md }
Run-Checked { git -C $source commit -m "Link the public JAR download" }
Run-Checked { git -C $source push }
Write-Output "Published source: https://github.com/$Repository"
Write-Output "Download: $download"
