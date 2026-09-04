# Genera dist\tagmap-auth.exe (Windows) para repartir a los usuarios.
# Requisitos: venv del recolector activado, vendor clonado en vendor\GoogleFindMyTools.
#   cd C:\CLAUDE\XIAOMI_TAG\collector ; .\.venv\Scripts\Activate.ps1 ; .\build_tagmap_auth.ps1
pip install -q pyinstaller
pip install -q -r vendor-requirements.lock
Remove-Item -Recurse -Force build, dist -ErrorAction SilentlyContinue
pyinstaller --noconfirm --onefile --console --name tagmap-auth `
  --add-data "vendor\GoogleFindMyTools;GoogleFindMyTools" `
  --collect-all undetected_chromedriver `
  --collect-all selenium `
  --collect-all gpsoauth `
  --collect-all cryptography `
  --collect-all Cryptodome `
  --collect-all google.protobuf `
  --collect-all http_ece `
  --hidden-import ecdsa --hidden-import pytz --hidden-import aiohttp --hidden-import httpx `
  tagmap_auth\__main__.py
if (Test-Path dist\tagmap-auth.exe) {
  Write-Host "`nOK: dist\tagmap-auth.exe ($([math]::Round((Get-Item dist\tagmap-auth.exe).Length/1MB)) MB)."
  Write-Host "Probalo: .\dist\tagmap-auth.exe --status"
}
