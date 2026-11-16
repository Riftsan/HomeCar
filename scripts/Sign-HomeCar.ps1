#Requires -Version 7.5
<#
.SYNOPSIS
Creates a private persistent signing identity, signs a CI APK, or verifies it.
.DESCRIPTION
The keystore lives outside the repository. Its random password is protected with
Windows DPAPI. Credentials are passed only in the child process environment.
Existing key material and output APKs are never overwritten.
#>
[CmdletBinding()]
param(
    [ValidateSet('Initialize','Sign','Verify')]
    [string] $Action = 'Verify',
    [string] $InputApk = '',
    [string] $OutputApk = '',
    [string] $ExpectedPackage = 'com.renanespinar.homecar.auto',
    [int] $ExpectedVersionCode = 272,
    [string] $ToolsDirectory = (Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'HomeCarBuild\tools\build-tools-36.0.0\android-16')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $IsWindows) { throw 'This helper requires Windows DPAPI.' }
$storage = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'HomeCarBuild\signing'
$keyStore = Join-Path $storage 'homecar-release.p12'
$passwordFile = Join-Path $storage 'password.dpapi'
$certificateFile = Join-Path $storage 'signing-certificate.der'
$keyAlias = 'homecar-release'
$java = (Get-Command java -ErrorAction Stop).Source
$keytool = Join-Path ([IO.Path]::GetDirectoryName($java)) 'keytool.exe'
$apkSigner = Join-Path $ToolsDirectory 'lib\apksigner.jar'
$aapt = Join-Path $ToolsDirectory 'aapt2.exe'

function Assert-PrivateAcl([string] $Path) {
    $userSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $allowed = @($userSid, 'S-1-5-18')
    foreach ($rule in (Get-Acl -LiteralPath $Path).GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier])) {
        if ($rule.AccessControlType -eq 'Allow' -and $rule.IdentityReference.Value -notin $allowed) {
            throw 'The signing directory grants access to another principal.'
        }
    }
}

function Write-NewFile([string] $Path, [byte[]] $Bytes) {
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($Bytes, 0, $Bytes.Length) } finally { $stream.Dispose() }
}

function Invoke-PrivateProcess([string] $Program, [string[]] $Arguments, [string] $SigningPassword = '') {
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = [Diagnostics.ProcessStartInfo]::new()
    $process.StartInfo.FileName = $Program
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.CreateNoWindow = $true
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.RedirectStandardError = $true
    foreach ($argument in $Arguments) { [void]$process.StartInfo.ArgumentList.Add($argument) }
    if ($SigningPassword) { $process.StartInfo.Environment['HOMECAR_SIGNING_PASSWORD'] = $SigningPassword }
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(120000)) {
            $process.Kill($true)
            throw 'The signing utility timed out.'
        }
        $output = $stdout.GetAwaiter().GetResult()
        $errorOutput = $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw "Signing utility exited with code $($process.ExitCode); diagnostic output suppressed." }
        return $output
    } finally {
        [void]$process.StartInfo.Environment.Remove('HOMECAR_SIGNING_PASSWORD')
        $process.Dispose()
    }
}

function Read-Password {
    Assert-PrivateAcl $storage
    Assert-PrivateAcl $keyStore
    Assert-PrivateAcl $passwordFile
    $bytes = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($passwordFile), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    try { return [Text.Encoding]::UTF8.GetString($bytes) }
    finally { [Security.Cryptography.CryptographicOperations]::ZeroMemory($bytes) }
}

if ($Action -eq 'Initialize') {
    if (-not (Test-Path -LiteralPath $storage)) {
        [void][IO.Directory]::CreateDirectory($storage)
        $acl = [Security.AccessControl.DirectorySecurity]::new()
        $acl.SetAccessRuleProtection($true, $false)
        foreach ($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
            $rule = [Security.AccessControl.FileSystemAccessRule]::new($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
            $acl.AddAccessRule($rule)
        }
        Set-Acl -LiteralPath $storage -AclObject $acl
    }
    Assert-PrivateAcl $storage
    foreach ($path in @($keyStore, $passwordFile, $certificateFile)) {
        if (Test-Path -LiteralPath $path) { throw 'Signing identity material already exists; it will not be overwritten.' }
    }
    $password = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    $passwordBytes = [Text.Encoding]::UTF8.GetBytes($password)
    try {
        Write-NewFile $passwordFile ([Security.Cryptography.ProtectedData]::Protect($passwordBytes, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser))
        $arguments = @('-genkeypair','-alias',$keyAlias,'-keyalg','RSA','-keysize','3072','-sigalg','SHA256withRSA','-validity','10000','-dname','CN=HomeCar Release','-storetype','PKCS12','-keystore',$keyStore,'-storepass:env','HOMECAR_SIGNING_PASSWORD','-keypass:env','HOMECAR_SIGNING_PASSWORD','-noprompt')
        [void](Invoke-PrivateProcess $keytool $arguments $password)
        [void](Invoke-PrivateProcess $keytool @('-exportcert','-alias',$keyAlias,'-keystore',$keyStore,'-storepass:env','HOMECAR_SIGNING_PASSWORD','-file',$certificateFile) $password)
        Assert-PrivateAcl $keyStore
        [pscustomobject]@{ Result='Persistent signing identity created'; Directory=$storage; CertificateSha256=(Get-FileHash -LiteralPath $certificateFile -Algorithm SHA256).Hash.ToLowerInvariant() }
    } finally {
        [Security.Cryptography.CryptographicOperations]::ZeroMemory($passwordBytes)
        $password = $null
    }
    exit
}

if (-not (Test-Path -LiteralPath $apkSigner -PathType Leaf) -or -not (Test-Path -LiteralPath $aapt -PathType Leaf)) { throw 'Verified Android Build Tools 36.0.0 are required.' }
if (-not $InputApk -or -not (Test-Path -LiteralPath $InputApk -PathType Leaf)) { throw 'Provide an existing input APK.' }
$InputApk = (Resolve-Path -LiteralPath $InputApk).Path
$verificationPath = $InputApk

if ($Action -eq 'Sign') {
    if (-not $OutputApk) { throw 'Provide a new output APK path.' }
    $OutputApk = [IO.Path]::GetFullPath($OutputApk)
    if (Test-Path -LiteralPath $OutputApk) { throw 'The output APK already exists; it will not be overwritten.' }
    if (-not (Test-Path -LiteralPath ([IO.Path]::GetDirectoryName($OutputApk)) -PathType Container)) { throw 'Create the output directory before signing.' }
    $password = Read-Password
    try {
        [void](Invoke-PrivateProcess $java @('-jar',$apkSigner,'sign','--ks',$keyStore,'--ks-key-alias',$keyAlias,'--ks-pass','env:HOMECAR_SIGNING_PASSWORD','--key-pass','env:HOMECAR_SIGNING_PASSWORD','--v1-signing-enabled','false','--v2-signing-enabled','true','--v3-signing-enabled','true','--v4-signing-enabled','false','--out',$OutputApk,$InputApk) $password)
    } finally { $password = $null }
    $verificationPath = $OutputApk
}

$signature = Invoke-PrivateProcess $java @('-jar',$apkSigner,'verify','--verbose','--print-certs',$verificationPath)
$metadata = Invoke-PrivateProcess $aapt @('dump','badging',$verificationPath)
$package = [regex]::Match($metadata, "package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'")
if (-not $package.Success -or $package.Groups[1].Value -ne $ExpectedPackage -or [int]$package.Groups[2].Value -ne $ExpectedVersionCode) { throw 'APK package or version does not match the expected fork build.' }
if ($metadata -match '(?m)^application-debuggable') { throw 'The final APK must not be debuggable.' }
$certificate = [regex]::Match($signature, 'Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)')
if (-not $certificate.Success) { throw 'Could not verify the APK signing certificate.' }
$expectedCertificate = (Get-FileHash -LiteralPath $certificateFile -Algorithm SHA256).Hash.ToLowerInvariant()
if ($certificate.Groups[1].Value.ToLowerInvariant() -ne $expectedCertificate) { throw 'The APK was not signed by the private HomeCar release identity.' }
[pscustomobject]@{
    Result='APK signature, package, version, and release mode verified'
    Path=$verificationPath
    Package=$package.Groups[1].Value
    VersionCode=[int]$package.Groups[2].Value
    VersionName=$package.Groups[3].Value
    CertificateSha256=$expectedCertificate
    ApkSha256=(Get-FileHash -LiteralPath $verificationPath -Algorithm SHA256).Hash.ToLowerInvariant()
}
