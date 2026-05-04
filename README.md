# AI Study Planner
CS-UY 3913 Applied Java Programming — Semester Project

---

## What is this?

A console-based Java app that helps you manage your study schedule. You tell it what
you need to study, when the deadline is, how many hours it'll take, and how many days
a week you can study — and it builds a day-by-day or week-by-week schedule for you.

As you log progress, the plan automatically **redistributes the remaining hours** across
future days so the schedule always reflects what you still have to do.

There's also an optional AI feature (powered by Anthropic's API) that gives you a
study roadmap and pacing tips for any item. The app works fully without it.

---

## How to run it

You need Java 17 and Maven installed.

Run directly (no JAR build needed):
```
cd "C:\Users\AA\Desktop\NYU\26 spring\java\final project\StudyPlanner"
mvn exec:java
```

Or build a JAR first, then run it:
```
mvn package -q
java -jar target/study-planner-1.0-SNAPSHOT.jar
```

To enable AI features, set your Anthropic API key before running:
```
set ANTHROPIC_API_KEY=sk-ant-...
mvn exec:java
```

To run the tests:
```
mvn test
```

---

## What the menu does

```
──────── Study Planner ────────
1.  Add study item
2.  View all items
3.  Generate plan
4.  Record progress
5.  Add note to study item
6.  Edit daily schedule
──────── AI Features ───────────
7.  AI: Study roadmap (for selected item)
8.  AI: Optimize plan (for selected item)
──────── Manage Items ──────────
9.  Delete item
10. Update item
11. View current plan
12. Export to CSV
0.  Exit
```

**Option 1 — Add study item**
Enter a title, deadline (YYYY-MM-DD), total hours, and days/week. You'll also be
asked to choose a schedule mode (even split or heavier days — see below) and
optionally provide a folder of study materials for per-session file/page suggestions.

**Option 3 — Generate plan**
Select an item and choose Daily or Weekly strategy. The plan is printed immediately.
Today's session is marked with `← TODAY`.

**Option 4 — Record progress**
Log hours for a session. After saving, the plan is automatically regenerated starting
from tomorrow so the remaining hours are spread evenly across future days — you never
have to manually adjust the schedule.

**Option 5 — Add note**
Attach free-text notes or comments to any study item. Notes appear in View all items
and View current plan.

**Option 6 — Edit daily schedule**
Change the schedule mode (even split or heavier days) for any existing item without
re-creating it.

**Options 7 & 8 — AI**
Both prompt you to select a specific item first. Option 7 generates a basics-to-advanced
study roadmap for that item's subject. Option 8 analyzes that item's plan and suggests
optimizations.

**Option 11 — View current plan**
Shows the full plan, any notes, and a material coverage suggestion (if a folder was set).

**Option 12 — Export to CSV**
Writes a `.csv` file with one ITEM row per study goal and one SESSION row per planned
date. Enter a folder path or a full file path.

---

## Schedule modes

When you add or edit an item you choose how hours are distributed:

**Even split** — hours are divided equally across all study days. This is the default.

**Heavier days** — you pick specific days of the week (e.g. Sat + Sun) that get a
multiplier (default 1.5×) more hours than regular days. The planner guarantees those
days appear in the schedule every week. You can change the mode any time with option 6.

---

## How the scheduling works

**Daily strategy** — spreads remaining hours across individual days from today to the
deadline, respecting your days/week setting. Within each 7-day window it fills the
first N days. When heavier days are configured those DOWs always appear and receive
`multiplier × base` hours; remaining daysPerWeek slots are filled by other days.

**Weekly strategy** — one block per calendar week with your total weekly target. Good
for when you want flexibility in when exactly you study within a week.

Both strategies warn you if the deadline is too tight. They don't refuse to make a
plan — they just flag it as infeasible.

After you log progress (option 4), the plan is automatically re-generated starting
from **tomorrow** so the remaining hours are redistributed. You never see stale
scheduled hours — what's on screen is always what you still need to do.

---

## Material folder

When adding or updating an item you can provide a folder path containing your study
materials (slides, PDFs, etc.). The app scans the folder with `Files.list()`, counts
the files, and estimates total pages (50 KB ≈ 1 page). When you view the plan it
prints a per-session suggestion:

```
Material suggestion: ~3.0 file(s)/session, ~31 page(s)/session (15 files, ~157 total pages)
```

---

## Persistence

Your data is automatically saved to `study_planner_data.json` in the project folder
after every change (add, delete, update, record progress). It is loaded back on
startup, so nothing is lost between sessions.

The JSON is written manually using `Files.writeString` (no third-party library). Notes,
heavier-days config, material folder info, and progress are all persisted. Generated
plans are not saved — regenerating them from the stored item data is instant.

---

## How the code is organized

```
src/main/java/studyplanner/
├── Main.java               entry point — menu loop
├── AppController.java      static facade — wires all services together
├── CommandRouter.java      routes menu choices, owns the Scanner
├── StudyItem.java          domain model — one study goal
├── StudySession.java       domain model — one scheduled block
├── StudyPlan.java          result of plan generation (list of sessions)
├── PlanningStrategies.java PlanningStrategy interface + Daily + Weekly
├── PlannerManager.java     in-memory store, stream queries
├── AIStudyAssistant.java   Anthropic API wrapper (CompletableFuture)
├── PersistenceService.java NIO JSON save/load
├── ExportService.java      CSV export
├── InputValidator.java     static validation utility
└── exceptions/
    └── StudyPlannerException.java
```

---

## Design decisions

**Strategy pattern for planning**
`PlannerManager.generatePlan()` takes a `PlanningStrategy` — it doesn't know or care
whether you picked Daily or Weekly. Adding a new strategy (e.g. Pomodoro blocks)
requires zero changes outside the new class.

**Encapsulated progress**
`StudyItem.hoursCompleted` can only be changed through `addProgress()`, which validates
input and caps the total at `totalHours`. Direct field access would make it easy to set
it to an invalid value.

**Facade pattern**
`AppController` is the only class the menu ever calls. It delegates to `PlannerManager`,
`AIStudyAssistant`, `PersistenceService`, and `ExportService`. The menu knows nothing
about how services work internally — this makes it easy to swap implementations.

**AI isolation**
`AIStudyAssistant` is the only class that makes network calls. Unit tests never touch
it. The rest of the app calls it and gets a `CompletableFuture<String>` back. If the
API key is missing it returns a fallback message — nothing crashes.

**Manual JSON**
The Anthropic response format is consistent enough for simple string parsing. The
persistence JSON is also hand-built (no Jackson/Gson). This keeps `pom.xml` at one
dependency (JUnit).

**LinkedHashMap for item storage**
Preserves insertion order so the menu list always shows items in the order you added
them, not a random HashMap order.

---

## Testing

48 unit tests across 6 test classes, all using JUnit 5:

| Class | Tests | What it covers |
|---|---|---|
| `StudyItemTest` | 6 | Creation, progress update, cap at totalHours, negative guard, unique IDs |
| `DailyPlanningStrategyTest` | 6 | Session generation, completed item, hour totals, deadline boundary, days/week limit |
| `WeeklyPlanningStrategyTest` | 5 | Multi-week plans, completed item, even distribution, one-day deadline |
| `PlannerManagerTest` | 12 | Add/retrieve/remove, progress, plan generation, stream queries, null guard |
| `PersistenceServiceTest` | 6 | Round-trip save/load, empty list, multi-item, special chars, progress field |
| `InputValidatorTest` | 13 | Every validator: titles, hours, deadlines, date formats, days/week, progress |

No tests touch `AIStudyAssistant` or make network calls.

---

## Libraries used

- **JUnit Jupiter 5.10.0** — unit tests
- **Anthropic Messages API** — AI features via `java.net.http.HttpClient` (Java 11+ built-in)

No other third-party libraries. `pom.xml` has one dependency.

---

## Academic integrity statement

All code in this project was written by me individually. The Anthropic API and JUnit
library are cited above. No other external code was copied or used.
