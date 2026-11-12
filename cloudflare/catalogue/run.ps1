param(
    [switch]$Upload
)

$ErrorActionPreference = 'Stop'
$exitCode = 0
$secret = $null
$secretPointer = [IntPtr]::Zero

try {
    $env:R2_ACCOUNT_ID = Read-Host 'Cloudflare Account ID'
    $env:R2_ACCESS_KEY_ID = Read-Host 'R2 Access Key ID (bucket-scoped Object Read & Write)'
    $secret = Read-Host 'R2 Secret Access Key (input is hidden)' -AsSecureString

    $secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    $env:R2_SECRET_ACCESS_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)

    $script = Join-Path $PSScriptRoot 'generate-manifest.mjs'
    if ($Upload) {
        & node $script --upload
    }
    else {
        & node $script
    }
    $exitCode = $LASTEXITCODE
}
catch {
    Write-Error $_
    $exitCode = 1
}
finally {
    Remove-Item Env:R2_ACCOUNT_ID -ErrorAction SilentlyContinue
    Remove-Item Env:R2_ACCESS_KEY_ID -ErrorAction SilentlyContinue
    Remove-Item Env:R2_SECRET_ACCESS_KEY -ErrorAction SilentlyContinue
    if ($secretPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer)
    }
    if ($null -ne $secret) {
        $secret.Dispose()
    }
}

if ($exitCode -ne 0) {
    exit $exitCode
}
