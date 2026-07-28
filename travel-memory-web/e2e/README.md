# Map mobile regression

The map regression suite exercises the current mobile behavior without creating or deleting data.

Required local services:

- frontend at `http://127.0.0.1:5173`
- backend and MySQL used by that frontend
- a test Trip containing multiple located Memories, at least one grouped coordinate, one single-station date, and one Memory with multiple photos

PowerShell:

```powershell
$env:E2E_USERNAME = '<test username>'
$env:E2E_PASSWORD = '<test password>'
$env:E2E_TRIP_ID = '<test trip id>'
npm run test:e2e:map
```

To run the same suite against a deployed build, also set:

```powershell
$env:E2E_BASE_URL = 'https://example.com'
```

The suite verifies:

- initial full-route view without an automatically opened Memory card
- grouped-coordinate Marker selection
- gallery viewport and bottom thumbnail safety
- single-station date replay rules
- complete route playback controls
- mobile horizontal overflow

Credentials and private URLs must remain environment variables and must not be committed.
