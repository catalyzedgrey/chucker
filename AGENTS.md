# AGENTS.md

Guidance for Claude Code, Codex, Cursor and anyone else working in this repository.

Chucker Compose is a fork of [Chucker](https://github.com/ChuckerTeam/chucker), an in-app HTTP
inspector for Android, with the UI rewritten in Jetpack Compose. It's published as
`io.github.catalyzedgrey:chucker-compose` and keeps upstream's Kotlin packages, so it's a drop-in
replacement.

## Modules

| Module            | What it is                                                                 |
|-------------------|----------------------------------------------------------------------------|
| `library`         | The real implementation: interceptor, WebSocket factory, Room storage, UI   |
| `library-no-op`   | Same public API, does nothing. Apps use it in release builds               |
| `sample`          | Demo app that sends HTTP, GraphQL and WebSocket traffic                     |
| `macrobenchmark`  | Startup, scroll and tab-swipe benchmarks plus the Baseline Profile generator |

## Building and checking

The build needs JDK 21. If your Gradle daemon runs on a newer JDK, detekt and lint can crash before
they analyze anything; point `JAVA_HOME` at a JDK 21.

```bash
./gradlew detekt ktlintCheck                # static analysis, also run by ./pre-commit
./gradlew :library:testDebugUnitTest        # unit and Robolectric tests
./gradlew :library:lintDebug :library-no-op:lintDebug
./gradlew :sample:assembleDebug :sample:assembleRelease
```

`./gradlew ktlintFormat` fixes formatting. Lint runs with `warningsAsErrors`.

## Rules for library changes

- **Keep `library` and `library-no-op` in sync.** Every public class, builder method and parameter
  added to `library` needs a matching no-op version, or apps fail to compile in release builds.
- **Explicit API mode is on.** Public declarations need explicit visibility. Keep anything that
  isn't meant for apps `internal`.
- **Translate every new string.** Resources use the `chucker_` prefix, and every string exists in
  all 9 locales under `library/src/main/res/values-*`. A missing translation fails lint.
- **Bump the database version for schema changes** in `ChuckerDatabase`. Migrations are
  destructive (recorded data is cleared on upgrade), which is fine for a debugging tool; say so in
  the changelog.
- **Never block the app.** Capture code runs on the app's network threads: hand work off to a
  coroutine, never touch the database there, and catch Chucker's own failures instead of throwing
  into the app.
- **Add a changelog entry** under `## Unreleased` in `CHANGELOG.md` for user-visible changes, and
  update `README.md` when the public API changes.

## Git workflow

- Branch from the latest `main`, one change per branch: `<type>/<short-description>`, for example
  `feat/websocket-capture` or `fix/payload-search-no-matches`.
- Commit messages and PR titles follow
  [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):
  `<type>(<scope>): <description>`, with types such as `feat`, `fix`, `refactor`, `perf`, `test`,
  `docs`, `build`, `ci` and `chore`. The scope is usually the module: `library`, `sample`.
- Everything reaches `main` through a pull request.

## Agent skills

Skills live in `.agents/skills/`, the agent-neutral location read by Codex, Cursor, Gemini, Copilot
and other agents. `.claude/skills` is a symlink to it because Claude Code only reads that path.
Add new skills to `.agents/skills/`.

Several of the official Android skills (`android-cli`, `android-profiler`, …) call the `android`
command-line tool, so install it once: see https://developer.android.com/tools/agents.

Update the committed Android skills with:

```bash
android skills update --all --agent=universal --project=.
```

On Windows, enable symlinks (`git config core.symlinks true`, with Developer Mode on) so that
`.claude/skills` checks out as a link.
