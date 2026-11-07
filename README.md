# LifeScript backend

LifeScript is a small language for describing a week: when you're available, how your energy changes during
the day, your fixed routines and appointments, and the tasks you need to get done. This backend compiles a
LifeScript plan, reports any mistakes with their exact position, and schedules the tasks into your free time.

## The language

A complete plan, using every section, property and kind of value:

```
# Comments start with # and run to the end of the line.
plan: my_week
period: 2026-11-09 to 2026-11-15            # 1 to 7 days, not starting in the past

settings:                                   # optional
    morning: 07:00-12:00                    # change a named period (defaults below)
    evening: 18:00-21:00
    break: 10m                              # rest kept free around everything scheduled; none if not set

availability:                               # required; a day that isn't listed is off
    monday: flexible                        # from the start of morning to the end of evening
    tuesday: 09:00-12:00, 14:00-18:00       # time ranges
    wednesday: morning, evening             # named periods
    thursday: afternoon
    friday: 09:00-17:00

energy profile:                             # optional; time it doesn't cover counts as moderate
    default:                                # every day
        morning: high
        afternoon: moderate
        19:00-21:00: low                    # a time range works too
    friday:                                 # replaces the default where it says something
        afternoon: low

routines:                                   # fixed and recurring; availability doesn't apply to them
    routine: morning_routine
        time: 06:30                         # start time; it lasts as long as its activities add up to
        repeats: daily
        activities:
            stretch: 15m
            shower: 20m
    routine: evening_walk
        time: 19:00
        repeats: weekdays                   # daily, weekdays, weekends, or days: monday, thursday
        activities:
            walk: 45m

tasks:                                      # required; the scheduler chooses when they happen
    task: research
        duration: 2h                        # 1h30m and 90m work too; at most 24h
        priority: high                      # critical, high, medium or low
        effort: high                        # high, moderate or low; matched against your energy
        deadline: 2026-11-11 17:00          # date and time
    task: write_report
        duration: 1h30m
        priority: critical
        effort: moderate
        deadline: 2026-11-13 12:00
        dependencies: research              # starts only after these tasks end
        note: "final draft for review"
        break: 30m                          # rest after this task, instead of the plan's break
    task: inbox_zero
        duration: 20m
        priority: low
        effort: low
        repeats: monday, wednesday, friday  # one copy on each of these days

events:                                     # one-off, at a fixed date and time
    event: dentist
        date: 2026-11-11
        time: 14:00-15:00
        note: "bring insurance card"
    event: team_offsite
        date: 2026-11-13
        time: afternoon                     # a named period works too
```

A period can't start in the past, so adjust the dates when trying this out.

| Element | Value | Required |
|---|---|---|
| `plan:` | a name | yes, first |
| `period:` | `YYYY-MM-DD to YYYY-MM-DD`, 1–7 days, from today on | yes, second |
| **`settings:`** | | no |
| `morning:` `afternoon:` `evening:` | a time range; in this order during the day. Defaults: 06:00–12:00, 12:00–18:00, 18:00–22:00 | no |
| `break:` | a duration, 0m–2h. Not set = no breaks | no |
| **`availability:`** | `<day>: flexible` \| named periods \| time ranges, comma-separated | yes |
| **`energy profile:`** | `default:` and/or `<day>:`, each with `<named period or time range>: high \| moderate \| low` | no |
| **`routine:`** in `routines:` | `time:` start time, `repeats:`, `activities:` (`<name>: <duration>` lines) | all three |
| **`task:`** in `tasks:` | `duration:`, `priority:`, `effort:` | these three |
| | `deadline:` date and time · `dependencies:` task names · `repeats:` · `note:` `"text"` · `break:` 0m–2h | no |
| **`event:`** in `events:` | `date:` within the period, `time:` a time range or named period | both |
| | `note:` `"text"` | no |

**General rules**
- Keywords can be written in any case (`Task:`, `MONDAY`, `High`, `1H30M`). Names are case-sensitive and can't be
  keywords; they may use letters of any language, digits and `_` (`research`, `házifeladat`, `deep_work_2`).
- Times are `HH:MM`; a range is `HH:MM-HH:MM` and can't cross midnight (end at `00:00` and continue the next
  day). Durations are `2h`, `45m` or `1h30m`. Dates are `YYYY-MM-DD`.
- A repeating task can't have a deadline or dependencies, and nothing can depend on it.
- Indentation doesn't matter to the compiler; 4 spaces per level is the convention.

## Running it

Requires JDK 17 or newer and Docker (Docker Desktop must be running). Maven doesn't need to be installed: the
wrapper downloads it. On Windows, use `mvnw.cmd` instead of `./mvnw`.

```
docker compose up -d     # start the database (PostgreSQL 17); its data survives restarts
./mvnw spring-boot:run   # start the API on http://localhost:8080; creates or updates the tables
./mvnw test              # run the tests; some start their own throwaway PostgreSQL, so Docker must run
docker compose stop      # stop the database (docker compose down -v would also DELETE its data)
```

The database keeps its data in the Docker volume `lifescript-data`. Its tables are created and changed only by
the numbered scripts in [`db/migration`](src/main/resources/db/migration): add a new script for every change,
and never edit one that has already run.

## API

### Compiling

Both endpoints take the same JSON body:

```json
{ "content": "plan: my_week\n...", "timeZone": "Europe/Budapest" }
```

`timeZone` is the browser's time zone (`Intl.DateTimeFormat().resolvedOptions().timeZone`). It decides what
"today" and "now" are for the user: a period can't start before today, and nothing is scheduled before now.
It's optional (the server's zone is used then); an unknown zone is a `400 Bad Request`.

| Endpoint | Does | `schedule` in the response |
|---|---|---|
| `POST /api/validate` | every check, no scheduling; meant for live feedback while typing (call it debounced) | always `null` |
| `POST /api/compile` | every check, then scheduling | the schedule, or `null` if there are errors |

Response:

```json
{
  "valid": false,
  "diagnostics": [
    { "stage": "VALIDATION", "severity": "ERROR",
      "message": "Task 'write_report' depends on 'reserch', which doesn't exist",
      "startLineNumber": 49, "startColumn": 9, "endLineNumber": 49, "endColumn": 79,
      "fixes": [] }
  ],
  "schedule": null
}
```

- **Diagnostics** use Monaco editor marker positions: 1-based, `endColumn` exclusive. A diagnostic that isn't
  tied to a place (e.g. "Plan is empty") has all positions 0. `stage` is `SYNTAX`, `VALIDATION` or `INTERNAL`
  (a bug in LifeScript, not in the plan).
- **Fixes:** a syntax error may come with quick fixes the editor can offer in one click. Each fix is a `title`
  and the `edits` to apply, in the same positions (an empty range inserts):
  ```json
  "message": "'hgh' is not a valid priority. Did you mean 'high'?",
  "fixes": [ { "title": "Change to 'high'",
               "edits": [ { "startLineNumber": 12, "startColumn": 11, "endLineNumber": 12, "endColumn": 14,
                            "text": "high" } ] } ]
  ```
  There are fixes for misspelled keywords, names with spaces or dashes, wrongly written durations, dates and
  times (`90` → `90m`, `1.5h` → `1h30m`, `2030/01/10` → `2030-01-10`, `9:00` → `09:00`), notes without quotes
  or without the closing quote, and a missing `:`. None is offered when the right text would be a guess
  (`17:5`, `10/01/2030`). `fixes` is always there, and empty when there's none.
- **Schedule:** `days` maps each date to its entries `{ type, name, start, end, note }`, where `type` is
  `ROUTINE`, `EVENT`, `TASK` or `BREAK` and times are `"HH:MM"`; an `end` of `"00:00"` means midnight at the end
  of the day. A `BREAK` entry is the rest right after a task (its `name` is the task's). The plan's
  `break:` in `settings:` applies around everything; a task's own `break:` replaces it after that task and,
  unlike the plan's, must fit in free time. Without either, there are no breaks. `unscheduled` lists tasks
  that didn't fit, with the reason. `warnings` lists placed tasks worth checking, e.g. one that ends shortly
  before its deadline.

### Accounts

Compiling (`/api/validate`, `/api/compile`) is open to everyone. Saved plans need an account: anyone can
register, and every plan belongs to the account that saved it.

| Endpoint | Body | Answer |
|---|---|---|
| `GET /api/auth/csrf` | | `{ headerName, token }`, and sets the `XSRF-TOKEN` cookie |
| `POST /api/auth/register` | `{ email, password }` | `201 Created`, `{ id, email }`, and you're logged in |
| `POST /api/auth/login` | `{ email, password }` | `{ id, email }` |
| `GET /api/auth/me` | | `{ id, email }`, or `401` when not logged in |
| `POST /api/auth/logout` | | `204 No Content` |

- **Login is a session cookie** (`JSESSIONID`, `HttpOnly`, `SameSite=Lax`): the browser sends it with every
  request, and JavaScript can't read it.
- **CSRF protection:** every `POST`, `PUT` and `DELETE` (except compiling) must send the `X-XSRF-TOKEN` header
  with the value of the `XSRF-TOKEN` cookie. A frontend calls `GET /api/auth/csrf` once at startup to get the
  cookie. Without the header, the request is `403 Forbidden`.
- **Emails** are stored lowercased, so `Demo@Example.com` and `demo@example.com` are one account. **Passwords**
  need 8 to 128 characters and are stored only as a BCrypt hash. A taken email is `409 Conflict`; a malformed
  email or a too short password is `400 Bad Request`.
- A wrong email and a wrong password both answer `401` "Wrong email or password", so nobody can find out
  which emails have accounts.

### Saved plans

All of these need login (`401` otherwise). A plan is saved as its LifeScript text; its schedule is computed again when needed. Plans with errors can be
saved too (`valid` says whether it compiled when saved). Every save is kept in the plan's history.

| Endpoint | Body | Answer |
|---|---|---|
| `GET /api/plans` | | `[{ id, name, valid, version, updatedAt }]`, most recently changed first |
| `GET /api/plans/{id}` | | `{ id, name, content, valid, version, createdAt, updatedAt }` |
| `POST /api/plans` | `{ name, content, timeZone }` | `201 Created`, the plan, and its address in `Location` |
| `PUT /api/plans/{id}` | `{ name, content, version, timeZone }` | the plan with its new `version` |
| `DELETE /api/plans/{id}` | | `204 No Content` |
| `GET /api/plans/{id}/history` | | `[{ version, name, content, valid, savedAt }]`, newest first |

**Saving uses optimistic locking.** `version` in a `PUT` is the version the client loaded. If the plan was
saved elsewhere since (another tab, another device), the save is refused with `409 Conflict` instead of
overwriting those changes; the client should reload the plan.

A plan that doesn't exist, or that belongs to another account, is `404 Not Found`. A blank or too long name (more than 200 characters) or a `PUT` without
`version` is `400 Bad Request`.

### Errors

Requests that fail answer with the standard ProblemDetail JSON:

```json
{ "status": 409, "title": "Conflict", "detail": "Plan 9d28… was changed since version 0", "instance": "/api/plans/9d28…" }
```

Mistakes in a plan's text are never errors like this: they come back as `diagnostics` with status `200`.

## Configuration

Set in [`application.properties`](src/main/resources/application.properties), each can be overridden with an
environment variable (Spring drops dashes and turns dots into `_`):

| Property | Environment variable | Default |
|---|---|---|
| `lifescript.cors.allowed-origins` | `LIFESCRIPT_CORS_ALLOWEDORIGINS` | `http://localhost:5173` |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/lifescript` |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | `lifescript` |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | `lifescript` (local only; a server needs a real secret) |

CORS is only needed when the frontend calls the API from another address. With a proxy in front (like the
Vite dev server's), both are on the same address and this setting doesn't matter.

## Architecture

```
CompilerController (api)        HTTP only: request in, JSON out
        │
PlanningService (planning)      runs the steps, passes results along; holds no per-request state
        │
        ├── LifeScriptCompiler (compiler)    text → validated Plan, or diagnostics
        │       parse (ANTLR) → SemanticValidator → PlanModelBuilder → PlanModelValidator
        │       each stage runs only if the previous one found no errors
        │
        └── Scheduler (scheduler)            Plan → Schedule
                GreedyScheduler today; the interface leaves room for other algorithms (e.g. CSP)

Spring Security (account)       before every request: session cookie → logged-in account, CSRF check,
        │                       401 for saved plans without login
        │
PlanController (api)            saved plans over HTTP; who is calling is the logged-in account
        │
PlanStorageService (storage)    save, open, list, delete, history; always for one owner; checks versions
        │                       uses PlanningService.validate to record whether a plan is valid
        └── Spring Data repositories → PostgreSQL (tables from Flyway scripts)

AuthController (api) → AccountService, LoginSessions (account)    register, log in, who am I; passwords as BCrypt hashes
```

| Package | Contents |
|---|---|
| `api` | the HTTP layer: error responses (`ApiExceptionHandler`), CORS, time zone parsing; one sub-package per area with its controller and request/response types: `api.compiling` (`/api/validate`, `/api/compile`), `api.auth` (`/api/auth/*`), `api.plans` (`/api/plans`) |
| `planning` | `PlanningService`, and `PlanningConfig`, which picks the compiler, scheduler and clock |
| `storage` | saved plans and their history (JPA entities, repositories), `PlanStorageService` |
| `account` | accounts (table `users`), `AccountService`, `LoginSessions` (password check, session on login), and `SecurityConfig`: CSRF, logout, which endpoints need login, passwords |
| `compiler` | the pipeline stages, plus `Literals`/`Keywords` for reading values and keywords |
| `grammar` | generated by ANTLR from `src/main/antlr4` (lexer and parser grammars) |
| `diagnostics` | `Diagnostic` and its position and stage types; `Fix` and `TextEdit` for quick fixes |
| `model` | the plan as read-only records: `Plan`, `Task`, `Routine`, `Event`, ... |
| `scheduler` | the `Scheduler` interface and the rules shared by algorithms (available time, energy match, deadlines) |
| `scheduler.greedy` | the greedy algorithm: places tasks one by one, earliest deadline first, at the best energy match |
