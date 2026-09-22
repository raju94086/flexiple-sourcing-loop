# The Sourcing Refinement Loop

A recruiter types one sentence. The app turns it into objective filters and a fit rubric, runs
the search against the 48-profile talent map, ranks what comes back, and then keeps adjusting
the filters and the rubric from the recruiter's feedback until they freeze the search.

- **Frontend:** React 18 + Vite
- **Backend:** core Java 17 on the JDK's built-in `HttpServer`, one dependency (Gson)
- **LLM:** Google Gemini, called server-side only, with structured output

---

## Setup

You need JDK 17+, Maven and Node 18+.

**1. Set your Gemini API key.** The backend reads it from the environment variable
**`GEMINI_API_KEY`**. Get a free one at https://aistudio.google.com/apikey.

```powershell
# Windows PowerShell
$env:GEMINI_API_KEY = "your-key-here"
```

```bash
# macOS / Linux
export GEMINI_API_KEY="your-key-here"
```

**2. Start the backend** (from the same terminal, so it inherits the key):

```bash
cd backend
mvn compile exec:java
```

It listens on `http://localhost:8080` and prints a warning if the key is missing.

**3. Start the frontend** in a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open the URL Vite prints — http://localhost:5173 unless that port is taken, in which case it moves to
5174 and says so. Vite proxies `/api` to the backend, so the browser never sees the key.

### Environment variables

| Variable | Required | Default | What it does |
| --- | --- | --- | --- |
| `GEMINI_API_KEY` | **yes** | – | Your Gemini API key. Never committed; read from the environment only. |
| `GEMINI_MODEL` | no | `gemini-flash-lite-latest` | Swap the model without touching code. |
| `PORT` | no | `8080` | Backend port. Change `frontend/vite.config.js` too if you move it. |

> **If you get a 404 about the model.** Google retires these names fast — `gemini-2.0-flash` and
> `gemini-2.5-flash` were both already dead against a fresh key while this was being built. List what
> your key can actually use and set `GEMINI_MODEL` to one of them:
>
> ```
> https://generativelanguage.googleapis.com/v1beta/models?key=YOUR_KEY
> ```
>
> The app's own 404 message says exactly this, so you do not have to come back here for it.

> **If you get a 429.** The free tier meters requests *per day, per model*, and some models are as
> low as 20/day. One search is 2 calls and one refinement round is another 2, so a tight model runs
> out quickly. The default is a `-lite` alias for exactly this reason; if you exhaust it, point
> `GEMINI_MODEL` at another model and you get a fresh daily bucket.



**One session object is the whole API.** Every endpoint returns the complete `SearchSession` —
filters, rubric, results, counts, round number and chat log. The frontend redraws from it wholesale,
so the panels can never drift out of sync with the results they produced.

**Feedback accumulates, it does not replace.** The session keeps a `feedback_history` list and
replays *all* of it into every refine prompt. Round 3 cannot silently undo what the recruiter asked
for in round 1 — the single most common way a loop like this loses a recruiter's trust.

**The LLM returns whole objects, not diffs.** The refine call returns complete filters and a
complete rubric, plus a separate `changes` list describing what it edited. The app renders that list
in the chat with before-and-after values, so "what changed and why" is visible rather than implied.
Asking for a diff and applying it would have been cheaper and much easier to get subtly wrong.

**Explanations are anchored to real fields.** The scoring prompt requires every `reason` to quote
concrete values from that record — a real skill, a real company, the actual `years_experience`
number — and returns `evidence` chips lifted straight from the profile plus honest `concerns`.

---


## Decisions

**What I prioritised**

- *The loop being visibly responsive to feedback.* The change log in the chat, with real before/after
  values, is the thing that makes a recruiter believe the app heard them. It got the most prompt
  effort and the most UI effort.
- *Filters and rubric always visible and directly editable.* They sit in a sticky left rail and every
  field is editable in place. Hand edits skip the LLM entirely and re-run as written — if the
  recruiter typed it, they meant it.
- *Two ways to give feedback.* Per-card ✓ / ✕ compose a sentence like "2 and 4 are right, 1 is not."
  into the composer; typing overrides it. Clicking is faster; typing is more precise.
- *Honest result cards.* Score, a reason quoting real fields, evidence chips, and concerns. A card
  that only ever flatters the candidate is a card a recruiter stops reading.
- *Failure paths as first-class states*, per the table above.

**What I cut, and why**

- *No database, no persistence.* The brief is one session. Sessions live in a `ConcurrentHashMap`
  for the life of the process. A restart loses them, which is the correct trade for this scope.
- *No streaming.* A designed thinking state that names the actual steps buys more trust than
  token-by-token output, for a fraction of the complexity.
- *No embeddings or semantic skill matching.* Skill matching is case-insensitive substring both ways,
  so "RDS" finds "AWS RDS" and vice versa. On 48 records that is enough, and it is explainable.
- *Scoring is capped at 30 profiles per round.* Scoring is the slow, expensive call, and a search
  still matching 40 people needs better filters rather than a bigger prompt.
- *No auth, no roles, no tests.* Out of scope, and the time went into the loop and the UI instead.

**Trade-offs worth naming**

- `required_skills` filters on **all** of them, so the parse prompt is explicitly told to keep that
  list to two or three and push everything else into `preferred_skills`, which is never filtered on
  and is rewarded by the rubric instead. Over-strict hard filters silently delete good candidates,
  and that is the failure mode a recruiter never sees and cannot debug.
- `company_types` matches **current or past** companies, because "has worked at startups" is a
  statement about someone's history, not their current employer.
- The refine prompt is told to prefer moving a bar in the rubric over adding a hard filter. Filters
  delete people from the search; the rubric only re-ranks them, which is recoverable.

---

## Layout

```
backend/
  pom.xml
  src/main/resources/profiles.json      the 48-profile talent map
  src/main/java/com/flexiple/sourcing/
    Main.java             HTTP server, routes, error-to-JSON mapping
    SourcingService.java  the loop: parse, filter, score, refine, freeze
    GeminiClient.java     the only place that talks to Gemini; retries, timeouts, validation
    Prompts.java          every prompt and every response schema
    ProfileStore.java     loads profiles.json, applies the objective filters
    LlmException.java     LLM failures, already phrased for a recruiter
    model/                Profile, SearchFilters, Rubric, ScoredProfile, SearchSession, ...
frontend/
  src/App.jsx             state machine: idle / searching / results / refining / frozen / error
  src/api.js              fetch wrapper, turns error bodies into ApiError
  src/components/         SearchScreen, Thinking, FiltersPanel, RubricPanel,
                          ProfileCard, ChatPanel, FrozenSummary
  src/styles.css          hand-written, no UI framework
```
