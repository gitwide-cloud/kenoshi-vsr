param([Parameter(Mandatory=$true)][string]$SdkRoot)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$resolvedSdk=(Resolve-Path -LiteralPath $SdkRoot).Path
$headers=Join-Path $resolvedSdk 'Include'
$runtime=Join-Path $resolvedSdk 'Lib\arm64-v8a\libndi.so'
if(!(Test-Path -LiteralPath (Join-Path $headers 'Processing.NDI.Lib.h')) -or !(Test-Path -LiteralPath $runtime)){throw 'Expected official Android SDK Include/ and Lib/arm64-v8a/libndi.so. Check the selected SDK root.'}
$headerTarget=Join-Path $project 'ndi-sdk\include'
$libTarget=Join-Path $project 'app\src\main\jniLibs\arm64-v8a'
$noticeTarget=Join-Path $project 'app\src\main\assets\licenses'
New-Item -ItemType Directory -Force $headerTarget,$libTarget,$noticeTarget | Out-Null
Copy-Item -Path (Join-Path $headers '*') -Destination $headerTarget -Recurse -Force
Copy-Item -LiteralPath $runtime -Destination $libTarget -Force
Get-ChildItem -LiteralPath (Split-Path $runtime) -Filter '*.txt' | Copy-Item -Destination $noticeTarget -Force
Get-ChildItem -LiteralPath $resolvedSdk -Filter '*License*.pdf' | Copy-Item -Destination $noticeTarget -Force
Write-Host 'Imported local SDK and supplied notices into Git-ignored paths. Its license remains separate. Build with -PwithNdi=true.'
