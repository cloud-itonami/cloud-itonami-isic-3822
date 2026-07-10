# Governance

`cloud-itonami-isic-3822` is an OSS open-business blueprint. Governance
covers both code and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- HazWasteTreatment-LLM cannot directly receive a manifest, execute
  treatment, disclose, or resolve a correction request.
- HazWasteGovernor remains independent of the advisor.
- hard governor violations (chain-of-custody-gate, treatment-method-
  authorization-gate, source-provenance-gate, licensed-disclosure) cannot
  be overridden by human approval.
- a correction/dispute request never auto-resolves, at any rollout phase.
- a cross-border shipment always reaches a human, regardless of confidence.
- every commit, hold and disclosure event is auditable.
- no schema field exists for order/payment/transport-routing — this actor
  tracks manifest and treatment state only.
- real manifest data and real facility-permit credentials stay outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, disclosure scope, public business model, operator
certification or license should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit, support and
data-flow review.

Certified operators can lose certification for:

- bypassing governor checks
- disclosing data to an uncontracted party
- receiving a manifest with an incomplete chain-of-custody
- executing treatment against an unauthorized or lapsed facility permit
- misrepresenting certification status
- failing to respond to security incidents or manifest disputes
