# Copies the pinned Gemma model into the debug app (files/models) so the stage never waits on a 3.7 GB
# download. Phone connected, Munshi (debug build) installed:
#   pwsh android/scripts/push-model.ps1           # E4B, for 8 GB phones
#   pwsh android/scripts/push-model.ps1 -Small    # E2B, for 4–6 GB phones
param([switch]$Small)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$app = 'com.opuscube.munshi'
if ($Small) {
  $name = 'gemma-4-E2B-it.litertlm'; $repo = 'gemma-4-E2B-it-litert-lm'; $rev = '1caa527c1e88b55f046fcd8d2b4265606680663d'
  $want = '181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c'
} else {
  $name = 'gemma-4-E4B-it.litertlm'; $repo = 'gemma-4-E4B-it-litert-lm'; $rev = 'e6a3892a8c22cf87ff99583996149cf3cb32452b'
  $want = '0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0'
}
function Adb { & $adb @args; if ($LASTEXITCODE -ne 0) { throw "adb $args failed ($LASTEXITCODE)" } }

$work = Join-Path $env:TEMP 'munshi-models'
New-Item -ItemType Directory -Force $work | Out-Null
$file = Join-Path $work $name
if (-not (Test-Path $file) -or (Get-FileHash $file -Algorithm SHA256).Hash.ToLower() -ne $want) {
  Write-Host "Downloading $name (resumable)"
  & curl.exe -L -C - --fail -o $file "https://huggingface.co/litert-community/$repo/resolve/$rev/$name"
  if ($LASTEXITCODE -ne 0) { throw "download failed ($LASTEXITCODE)" }
}
$got = (Get-FileHash $file -Algorithm SHA256).Hash.ToLower()
if ($got -ne $want) { throw "SHA-256 mismatch: $got" }
Write-Host "Pushing $name"
Adb push $file "/data/local/tmp/$name"
Adb shell "run-as $app mkdir -p files/models && cat /data/local/tmp/$name | run-as $app sh -c 'cat > files/models/$name.part && mv files/models/$name.part files/models/$name' && rm /data/local/tmp/$name"
Adb shell "run-as $app ls -l files/models"
