$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$pythonCommands = @(
    @("py", "-3.11"),
    @("py", "-3.10"),
    @("py", "-3")
)

$created = $false
foreach ($pythonCommand in $pythonCommands) {
    try {
        & $pythonCommand[0] $pythonCommand[1] -m venv env
        $created = $true
        break
    } catch {
        Write-Host "Could not create venv with $($pythonCommand -join ' '), trying next option..."
    }
}

if (-not $created) {
    throw "Could not create a Python virtual environment. Install Python 3.10 or 3.11 and try again."
}

.\env\Scripts\python.exe -m pip install --upgrade pip setuptools wheel
.\env\Scripts\python.exe -m pip install -r requirements.txt

Write-Host "AIModel Python environment is ready: $scriptDir\env"
