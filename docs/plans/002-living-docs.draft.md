# 002 Living documentation

Status: approved (27 Nov 2026). It moves to `specs/002-living-docs.md` when implementation starts. It lives outside `specs/` until then,
because validate wants a test for every example in `specs/` (V1 adds support for draft specs).

## Goal

One public page on GitHub Pages, rebuilt from repo files on every merge to `main`. It shows:
- a timeline of design checkpoints, each marked implemented, specified or needs specification;
- the technical design: every decision (`D` row) from every spec, with its choice and the check behind it;
- the UI mockups, and next to each one the real screen from the phone once device checks save screenshots.

## Out of scope

Editing or commenting on the page, search, versioned docs, analytics or any tracker.

## Sources (the page is generated only from these)

| File | Holds | Edited by |
|---|---|---|
| `docs/timeline.yaml` | checkpoints: id, title, spec, what is decided, open questions | agents, when you ask; you dictate items as lists in chat |
| `specs/*.md` | status, goal, `D` rows | the specify loop |
| `docs/mockups/*.html` | interactive mockups (starting with `Stoa UI mocks.html`) | you, or agents when you ask |
| `build/reports/device/*.png` | real screenshots (after V6) | the device check |
| `app/permissions.yaml` | approved permissions, each with its library and the reason (D002-9) | you approve each entry |
| `docs/fonts/` | Roboto Flex and Spectral, with their OFL licences | added once |

A timeline item, for example:

```yaml
- id: model-download
  title: Download a model over Wi-Fi
  spec: null            # "003" once written; then the status comes from the spec file
  status: needs-spec    # only for items without a spec: needs-spec | later
  decided:
    - text: The app uses the network only to download models (27 Nov 2026).
      area: privacy
  open:
    - How do we prove nothing else in the app sends data once INTERNET is granted?
```

## Acceptance examples

| ID | Given | When | Then |
|---|---|---|---|
| S002-1 | a timeline item whose spec has `Status: implemented` | the page is built | the item shows implemented, whatever the YAML says |
| S002-2 | a timeline item with no spec | the page is built | it shows "needs specification" and lists its open questions |
| S002-3 | a timeline item naming a spec that does not exist, or YAML that does not parse | validate runs | validate fails and names the item or the line |
| S002-4 | a spec with `D` rows | the page is built | the technical design lists each row with question, choice and "verified by" |
| S002-5 | a mockup with a real screenshot for the same screen | the page is built | both show side by side; without a screenshot, the mockup shows alone |
| S002-6 | any build | the page is built | the output holds only the allowlisted sources, never `build/` internals, models or local files |
| S002-7 | a merge to `main` | CI finishes | the live Pages site shows the new content (live check) |
| S002-9 | the built page | it loads in a browser | it makes no request to any host other than the Pages site itself |
| S002-10 | `D` rows with area `privacy`, the approved permission list, and timeline items with privacy decisions | the page is built | a Privacy section lists them all, each linking to its spec or timeline item, plus every permission with the library that adds it and why it is accepted |
| S002-8 | the built page | someone uses it with a keyboard only, or in dark mode | every control can be reached and read |

## Device questions

This page never runs on the phone, so lifecycle, memory, threads and hardware do not apply. Data does apply:
everything in the allowlist becomes public (S002-6).

## Decisions

| ID | Area | Question | Options and trade-offs | Choice and why | Verified by |
|---|---|---|---|---|---|
| D002-1 | tooling | Timeline file format | YAML: readable, comments, matches the lists you type in chat; needs a parser. JSON: no parser, noisy to read. | **YAML** (you, 27 Nov): agents edit it when you ask, from lists you type. | S002-3 |
| D002-2 | tooling | Where a checkpoint's status comes from | YAML status for everything (can drift from the spec), or the spec's `Status:` line when a spec exists (one source of truth). | **The spec wins** (you, 27 Nov: recommendations). | S002-1 |
| D002-3 | tooling | What builds the page | A Kotlin `tools:docs` module (same language and tests as `tools:eval`, runs in validate, one YAML library), a Python or shell script (fewer lines, a second language), or a site generator such as MkDocs or Astro (themes, but a whole toolchain). | **Kotlin `tools:docs`** (you, 27 Nov). One stack to learn, and validate already runs Gradle. | S002-1 to S002-6 |
| D002-4 | tooling | Page technology | One static HTML file with plain JavaScript and no framework, mockups embedded as iframes; or a JS framework. | **Plain static HTML** (you, 27 Nov). The mockups are already plain HTML. | S002-8 |
| D002-5 | privacy | Fonts | The mockups load Roboto Flex and Spectral from Google Fonts, which sends every visitor's IP address to Google. Self-hosting the font files (both are OFL) avoids that. | **Self-host** (you, 27 Nov). The font files go in `docs/fonts/` with their OFL licence files, and the page makes no request outside GitHub Pages. | S002-9 |
| D002-6 | tooling | Deployment | GitHub Actions with `actions/deploy-pages` on push to `main` (official, no extra branch), or a `gh-pages` branch. | **Actions** (you, 27 Nov). | S002-7 |
| D002-7 | tooling | Mockups vs screenshots | Mockups only, or side by side once screenshots exist. | **Side by side when available** (my pick; you had no preference). | S002-5 |
| D002-8 | privacy | How the page finds privacy choices | A free-text tag in the question (easy to forget, nothing checks it); an `Area` column on every `D` row with a fixed list of values that validate checks (lifecycle, memory, threads, hardware, storage, data, privacy, tooling), the same list as the device questions; or a separate privacy file (a second place to keep in sync). | **The `Area` column** (you, 27 Nov). It also lets the page group all decisions by area, not only privacy. | S002-10 |
| D002-9 | privacy | Where the approved permission list lives | Inside the test (V2), so the page cannot show it; or in `app/permissions.yaml`, one entry per permission with the library that adds it and why it is accepted. The test and the page both read that file. | **`app/permissions.yaml`** (you, 27 Nov). The page then shows exactly what the test enforces. | S002-10 |

## First timeline content

From today's answers. Each item becomes its own spec later.

- **validation-setup**: in progress, `docs/plans/validation-setup.md` (V0 done).
- **001 ask-the-stoics**: implemented.
- **002 living-docs**: draft (this file).
- **model-download**: needs spec. Decided: network only for model downloads. Open: proving there is no other
  traffic; resuming a broken download; checksums; where files live; a full disk.
- **semantic-retrieval**: needs spec. Decided: an embedding model replaces or joins BM25. Open: which model and its
  size; RAM with Gemma loaded at the same time; index built on the phone or shipped prebuilt; what happens to the
  index when the embedding model changes; whether BM25 stays as a fallback or a hybrid.
- **model-swap**: needs spec. Decided: two models, swappable. Open: unload before load (peak RAM); what happens to
  an answer that is still streaming; a benchmark baseline per model.
- **chat-persistence**: needs spec. Open: what survives process death; save points; a half-streamed answer.
- **ui-from-mockups**: needs spec. Welcome, setup, library and settings screens.
- **voice**: later.
