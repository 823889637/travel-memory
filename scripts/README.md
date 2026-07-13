# Travel Memory Scripts

Run these scripts from PowerShell.

```powershell
# Start MySQL only, port 3306
.\scripts\start-mysql.ps1

# Start backend in the current PowerShell, port 8080.
# The script reads database and application settings from the root .env file.
.\scripts\run-local-backend.ps1

# Restart frontend only, port 5173.
.\scripts\restart-frontend.ps1
```

For local registration, add `APP_REGISTRATION_ENABLED=true` and a private
`APP_REGISTRATION_INVITE_CODE` to the root `.env`, then restart the backend
with `run-local-backend.ps1`. Do not commit `.env`.

Logs are written to:

```text
logs/frontend.log
logs/frontend-error.log
logs/mysql.log
logs/mysql-error.log
```
