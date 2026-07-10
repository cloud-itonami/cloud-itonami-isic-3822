# Contributing

`cloud-itonami-isic-3822` accepts contributions to the OSS actor, governor
tests, documentation, examples and open business blueprint.

## Development

```bash
clojure -M:dev:test
clojure -M:lint
```

Keep changes small and include tests for governor, audit, store or
disclosure behavior.

## Rules

- Do not commit real manifest data, real facility-permit identifiers, or
  customer contract documents.
- Keep production manifest-receive/treatment-execute/disclosure operations
  behind HazWasteGovernor.
- Treat every new waste class or treatment method as high-risk: add tests
  for chain-of-custody-gate, treatment-method-authorization-gate,
  source-provenance-gate, confidence floor and audit logging.
- Never fabricate a source-catalog entry to expand apparent regulatory-regime
  coverage.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which governor invariant is affected
- how it was tested
- whether operator or certification docs need updates
