# Contributing

Thank you for helping improve FitPlan RAG.

## Development checks

1. Create `.env` from `.env.example` and never commit the resulting file.
2. Run backend tests with `mvn test`.
3. Run `pnpm install --frozen-lockfile` and `pnpm build` in `frontend/`.
4. Keep changes focused and explain user-visible behavior in the pull request.

## Health and safety changes

Changes to red-flag rules, risk classification, medical boundaries, or health
claims must include tests and authoritative sources. Avoid presenting the
application as a substitute for professional care.

## Corpus contributions

Do not submit scraped or translated material based only on the fact that it is
publicly accessible. Every corpus file must include a canonical source URL,
publisher, retrieval date, explicit reusable license, attribution requirements,
and `rights_status: approved`. Maintainers must independently verify reuse and
translation rights before merging it.

By submitting a contribution, you agree that it may be distributed under the
project's applicable code or data terms.
