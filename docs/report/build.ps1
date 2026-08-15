# Builds the project documents to PDF.
#
# Requires Tectonic, a self-contained LaTeX engine that downloads only the
# packages a document actually uses:
#
#   winget install TectonicProject.Tectonic
#
# or download the binary from
#   https://github.com/tectonic-typesetting/tectonic/releases
# and either place it on PATH or pass its location:
#
#   .\build.ps1 -Tectonic C:\tools\tectonic\tectonic.exe

param(
    [string]$Tectonic = 'tectonic'
)

# Deliberately not 'Stop': Tectonic writes a harmless Fontconfig notice to
# standard error on Windows, which Windows PowerShell would otherwise treat as
# a terminating error. Success is determined from the exit code instead.
$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot

$documents = @(
    @{ Source = 'report.tex';                Output = 'HomeSense-Technical-Report.pdf' },
    @{ Source = 'demonstration-script.tex';  Output = 'HomeSense-Demonstration-Script.pdf' },
    @{ Source = 'defence-notes.tex';         Output = 'HomeSense-Defence-Notes.pdf' }
)

if (-not (Get-Command $Tectonic -ErrorAction SilentlyContinue)) {
    Write-Error "Tectonic was not found. Install it, or pass -Tectonic <path to tectonic.exe>."
    exit 1
}

foreach ($document in $documents) {
    Write-Host "Building $($document.Source)"
    & $Tectonic -X compile $document.Source
    if ($LASTEXITCODE -ne 0) {
        Write-Error "$($document.Source) failed to compile."
        exit 1
    }

    $built = [System.IO.Path]::ChangeExtension($document.Source, 'pdf')
    Move-Item $built (Join-Path '..' $document.Output) -Force
    Write-Host "  -> docs/$($document.Output)"
}

Write-Host ''
Write-Host 'All documents built.'
