# Travel Memory Scripts

Run these scripts from PowerShell.

```powershell
# Start MySQL only, port 3306
.\scripts\start-mysql.ps1

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
logs/mysql.log
logs/mysql-error.log
```
