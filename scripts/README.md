# Travel Memory Scripts

Run these scripts from PowerShell.

```powershell
# Restart backend only, port 8080
.\scripts\restart-backend.ps1

# Restart frontend only, port 5173
.\scripts\restart-frontend.ps1

# Restart both backend and frontend
.\scripts\restart-all.ps1
```

Logs are written to:

```text
logs/backend.log
logs/backend-error.log
logs/frontend.log
logs/frontend-error.log
```

These scripts do not start MySQL. Make sure MySQL is already running on `127.0.0.1:3306`.
