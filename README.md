# AI Study Planner
CS-UY 3913 Applied Java Programming — Semester Project

---

## What is this?

This is a console-based Java app that helps you manage your study schedule. The idea
is simple: you tell it what you need to study, when the deadline is, how many hours
it'll take, and how many days a week you can study — and it builds a schedule for you.
You can also log your progress as you go, and it'll recalculate how much time you
have left.

There's also an optional AI feature (powered by Anthropic's API) that gives you
short pacing tips for each study item. It's completely optional though — the app
works fine without it.

---

## How to run it

You need Java 17 and Maven installed. If you followed the setup, Maven should already
be at `C:\ProgramData\chocolatey\lib\maven\apache-maven-3.9.15\bin\mvn`.

Build the jar first:
```
cd "C:\Users\AA\Desktop\NYU\26 spring\java\final project\StudyPlanner"
mvn package -q
```

Then run it:
```
java -jar target/study-planner-1.0-SNAPSHOT.jar
```

If you want the AI suggestions to work, set your Anthropic API key before running:
```
set ANTHROPIC_API_KEY=sk-ant-...
java -jar target/study-planner-1.0-SNAPSHOT.jar
```

If you don't set it, the app still works — the AI option just tells you the key
is missing instead of crashing.

To run just the tests:
```
mvn test
```

---

## What the menu does

When you run the app you get a simple numbered menu:

```
1. Add study item       — enter a title, deadline, total hours, and days/week
2. View all items       — shows everything you've added with current progress
3. Generate plan        — picks daily or weekly spread and prints the schedule
4. Record progress      — log hours you've completed; remaining hours update automatically
5. Get AI suggestion    — sends your item to Claude and prints a short pacing tip
6. Delete item          — removes an item and its plan
0. Exit
```

Everything is text-based, no GUI. You just type numbers and press Enter.

---

## How the scheduling works

When you generate a plan, you choose between two strategies:

**Daily** — takes your remaining hours and spreads them across your available study
days up until the deadline. It uses a simple 7-day window system starting from today,
and within each window it fills the first N days (where N is the days/week you said
you're available). So if you have 2 weeks and 3 days/week, you get 6 sessions with
your hours split evenly across them.

**Weekly** — instead of individual days, it gives you one block per week with your
total weekly target. Good for when you want flexibility in when exactly you study
within each week.

Both strategies warn you if the deadline is too tight (e.g. you have 40 hours left
but only 3 days). They don't refuse to make a plan — they just flag it as infeasible.

---

## How the code is organized

The project is split into four packages plus a Main class:

**model** — the data objects. `StudyItem` holds everything about a study goal
(title, deadline, hours, how many days/week you can study, and how many hours you've
done so far). `StudySession` is just one scheduled block: a date, the item title,
and how many hours are planned for that day.

**plan** — the scheduling logic. `PlanningStrategy` is an interface with one method:
`generateSessions(item, startDate)`. `DailyPlanningStrategy` and
`WeeklyPlanningStrategy` both implement it. `StudyPlan` is what you get back — a
list of sessions plus a flag for whether the plan is feasible. The reason there's an
interface here is so that `PlannerManager` doesn't care how sessions are generated —
you could add a third strategy (say, Pomodoro blocks) without touching anything else.

**service** — two classes. `PlannerManager` is the main coordinator: it stores your
study items in a `LinkedHashMap` (keeps insertion order), stores generated plans, and
handles adding/removing items and recording progress. `AIStudyAssistant` is the only
class that makes network calls — it talks to the Anthropic API, builds the request
JSON by hand, and parses the response. If the API key isn't set, it just returns a
message saying so. Nothing else in the app knows or cares whether AI is available.

**util** — just `InputValidator`, a utility class with static methods for validating
every kind of input: titles, hours, deadlines (must be YYYY-MM-DD and in the future),
days per week (1–7), and progress hours. All validation happens here, called from
`Main.java`. The model and service classes don't re-validate — they trust the input
is already clean by the time it reaches them.

**Main.java** — the entry point. It runs the menu loop, calls `InputValidator` before
doing anything with user input, and delegates everything else to `PlannerManager` or
`AIStudyAssistant`.

---

## Design decisions worth explaining

**Why the Strategy pattern for planning?**
I originally had all the scheduling logic inside `PlannerManager`, but it got messy
fast. Moving it into separate strategy classes means each one has one job and is easy
to test in isolation. The interface also makes it obvious how to add more strategies
later.

**Why is hoursCompleted private?**
`StudyItem.hoursCompleted` can only be changed through `addProgress()`, which checks
that you're not adding negative hours and caps the total at `totalHours`. If it were
public, something could accidentally set it to 500 or -3. The method enforces the
rule that completed hours always make sense relative to the total.

**Why is AI isolated in its own class?**
A few reasons. First, it makes the app testable — unit tests never call the network,
they just don't touch `AIStudyAssistant`. Second, it means the app runs fine with no
API key. Third, if I ever wanted to swap Anthropic for a different AI provider, I'd
only change one file. The rest of the app just calls `ai.getSuggestion(item)` and
gets a string back.

**Why not use a JSON library for the API calls?**
The Anthropic response format is consistent enough that I could parse out the text
content with a few string operations. Adding a library like `org.json` would work
fine but seemed like overkill for one API call. The manual parsing is a bit fragile
but there are comments explaining what it's looking for.

**Why LinkedHashMap for storing items?**
Regular HashMap doesn't preserve insertion order, which means the menu list could
show items in a random order every time. LinkedHashMap keeps them in the order you
added them, which feels more natural when you're looking at a list of your own
study goals.

---

## Testing

There are 38 unit tests across 5 test classes, all using JUnit 5:

`StudyItemTest` (6 tests) — checks that items are created correctly, that progress
updates work, that you can't go over the total hours, that negative progress throws
an exception, and that each item gets a unique ID.

`DailyPlanningStrategyTest` (6 tests) — checks that sessions get generated, that a
completed item produces no sessions, that the total planned hours roughly match the
remaining hours, that no session is scheduled after the deadline, and that the
days-per-week limit is actually respected.

`WeeklyPlanningStrategyTest` (5 tests) — checks multi-week plans, completed items,
even hour distribution across weeks, and the edge case of a one-day deadline.

`PlannerManagerTest` (8 tests) — checks adding and retrieving items, removing items,
recording progress, generating plans, the not-found exception, the null guard, and
that the item list grows correctly.

`InputValidatorTest` (13 tests) — covers every validator: empty/null titles, zero
and negative hours, unrealistically large hours, past deadlines, bad date formats,
days per week out of range, and zero progress hours.

None of the tests touch `AIStudyAssistant` or make any network calls. The AI feature
is tested manually.

---

## What's not included

To keep this manageable as a solo semester project, I left out a few things on purpose:

- **No file persistence** — everything resets when you close the app. Adding JSON
  save/load would be the most useful next step.
- **No login or user accounts** — it's single-user only.
- **No database** — items are stored in memory (a HashMap).
- **No calendar integration** — it generates dates but doesn't connect to Google
  Calendar or anything like that.

---

## Libraries used

- **JUnit Jupiter 5.10.0** — for unit tests
- **Anthropic Messages API** — for the AI suggestion feature (no Java SDK, just
  direct HTTP calls using `java.net.http.HttpClient` which is built into Java 11+)

No other third-party libraries. The pom.xml only has one dependency (JUnit).

---

## Academic integrity statement

All code in this project was written by me individually. The Anthropic API and JUnit
library are cited above. No other external code was copied or used.
