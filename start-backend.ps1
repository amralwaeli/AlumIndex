# Kill anything LISTENING on port 8081 first (ignore TIME_WAIT noise / PID 0)
$pids = Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique | Where-Object { $_ -gt 0 }
foreach ($p in $pids) {
    Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
    Write-Host "Cleared port 8081 (killed PID $p)" -ForegroundColor Yellow
}
if ($pids) { Start-Sleep -Seconds 2 }

$env:MAIL_FROM = "amralwaeli9@gmail.com"   # must be a Brevo-verified sender
$env:BREVO_API_KEY = ""                     # paste your Brevo API key here to send mail locally
$env:SPRING_PROFILES_ACTIVE = "local"

$jar = "$PSScriptRoot\backend\target\alumindex-backend-1.0.0-SNAPSHOT.jar"

Write-Host "Starting AlumIndex backend..." -ForegroundColor Cyan
java -jar $jar --spring.profiles.active=local
