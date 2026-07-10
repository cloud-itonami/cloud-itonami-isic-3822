# cloud-itonami-isic-3822

Open Business Blueprint for **ISIC Rev.4 3822**: treatment and disposal of
hazardous waste — the Clean Harbors / Veolia hazardous-treatment-facility
class of business — published as an OSS business that any qualified
operator can fork, deploy, run, improve and sell.

Deliberately distinct from [`cloud-itonami-isic-3811`](https://github.com/cloud-itonami/cloud-itonami-isic-3811)
(non-hazardous waste collection): this actor tracks the **regulatory
chain-of-custody manifest and treatment-authorization state** for
hazardous shipments — generator → transporter → treatment facility, under
US EPA RCRA Subtitle C, the EU Waste Shipment Regulation, and the Basel
Convention for cross-border movements. It never arranges or executes the
physical transport itself.

> **Why an actor layer at all?** A HazWasteTreatment-LLM is great at
> normalizing manifest data and drafting treatment records — but it has
> **no notion of chain-of-custody completeness, facility permit scope, or
> transboundary-movement oversight**. Letting it commit a manifest as
> "received" with a broken custody chain, or record treatment against a
> waste-code/method combination the facility isn't permitted for, is a real
> regulatory failure mode. This project seals the LLM into a single node
> and wraps it with an independent **HazWasteGovernor**, a human **review
> workflow**, and an immutable **audit ledger**.

## Scope

This actor tracks manifest chain-of-custody and treatment-authorization
state only. It never routes physical transport, never handles payment, and
never asserts a fact without a real regulatory-provenance citation
(`src/hazwaste/facts.cljc`: RCRA manifest system, EU Waste Shipment
Regulation, Basel Convention).

## The core contract

**Single invariant**: HazWasteTreatment-LLM never receives a manifest,
executes treatment, discloses, or resolves a correction the
HazWasteGovernor would reject.

## Run

```bash
clojure -M:dev:test
clojure -M:dev:run
clojure -M:lint
```

## Non-Negotiables

- Do not commit real manifest data or real facility-permit credentials.
- Do not add a schema field for transport-routing, payment, or physical
  execution.
- Do not bypass the HazWasteGovernor for production receipt, treatment, or
  disclosure.
- Do not fabricate a source-catalog entry.

License: AGPL-3.0-or-later.
