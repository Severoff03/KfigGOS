# Made by Mothman
param([switch]$Bootstrap, [switch]$Validate, [string]$Version)
$ErrorActionPreference = 'Stop'
$launcher = Join-Path $PSScriptRoot 'Build Releases.exe'
if ($Bootstrap) {
    if (Test-Path -LiteralPath $launcher) { throw 'Build Releases.exe already exists.' }
    $source = @'
// Made by Mothman
using System;
using System.Diagnostics;
using System.IO;
class ReleaseLauncher {
    static int Main(string[] args) {
        string root = AppDomain.CurrentDomain.BaseDirectory;
        string options = "";
        for (int i = 0; i < args.Length; i++) {
            if (args[i] == "--validate") options += " -Validate";
            else if (args[i] == "--version" && i + 1 < args.Length) {
                Version version;
                if (!Version.TryParse(args[++i], out version) || version.Build < 0 || version.Revision >= 0)
                    return 2;
                options += " -Version " + version.ToString();
            } else return 2;
        }
        var start = new ProcessStartInfo("powershell.exe", "-NoProfile -ExecutionPolicy Bypass -File \""
            + Path.Combine(root, "Build-Releases.ps1") + "\"" + options);
        start.UseShellExecute = false;
        start.EnvironmentVariables["KFIGGOS_RELEASE_LAUNCHER"] = "1";
        using (var process = Process.Start(start)) { process.WaitForExit(); return process.ExitCode; }
    }
}
'@
    $temporarySource = Join-Path $PSScriptRoot 'Builder\ReleaseLauncher.cs'
    New-Item -ItemType Directory -Path (Split-Path $temporarySource) -Force | Out-Null
    try {
        [IO.File]::WriteAllText($temporarySource, $source)
        $compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
        & $compiler /nologo /target:exe "/out:$launcher" $temporarySource
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the release launcher.' }
    } finally {
        Remove-Item -LiteralPath $temporarySource -ErrorAction SilentlyContinue
    }
    exit 0
}
if ($env:KFIGGOS_RELEASE_LAUNCHER -ne '1') {
    throw 'Use Build Releases.exe. To create the launcher once, run this script with -Bootstrap.'
}
try {
    & (Join-Path $PSScriptRoot 'Builder\release_builder.ps1') -Validate:$Validate -Version $Version
} catch {
    Write-Error $_
    exit 1
}
