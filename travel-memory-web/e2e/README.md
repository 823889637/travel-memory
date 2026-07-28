# Map mobile regression

The map regression suite exercises existing data without creating, updating, or deleting it. After login it reads the current user's Trip list and Timelines, then independently selects suitable Trips for:

- a route with at least two timed, located Memories
- grouped Memories at the same coordinate
- a long-distance route break with a readable default continuous segment
- a located Memory with multiple photos
- a date containing a single replayable station

Only credentials are required. `E2E_TRIP_ID` is optional: when provided, that Trip is preferred for every scenario it can satisfy, while missing scenarios are still discovered from the user's other Trips.

Required local services:

- frontend at `http://127.0.0.1:5173`
- backend and MySQL used by that frontend
- at least one owned Trip containing two Memories with valid record time and coordinates

PowerShell:

```powershell
$env:E2E_USERNAME = '<test username>'
$env:E2E_PASSWORD = '<test password>'
npm run test:e2e:map
```

Optional preferred Trip:

```powershell
$env:E2E_TRIP_ID = '<preferred trip id>'
```

To run the same suite against a deployed build, also set:

```powershell
$env:E2E_BASE_URL = 'https://example.com'
```

The suite verifies:

- initial route view without an automatically opened Memory card
- long-distance trips opening on a readable continuous segment before the full overview
- grouped-coordinate Marker selection when matching data exists
- gallery viewport and bottom thumbnail safety when a located multi-photo Memory exists
- single-station date replay rules when matching data exists
- complete route playback controls
- mobile horizontal overflow

Capability-specific tests are reported as skipped when the current account has no matching data. The core route test fails if no replayable Trip can be discovered.

Credentials and private URLs must remain environment variables and must not be committed.
