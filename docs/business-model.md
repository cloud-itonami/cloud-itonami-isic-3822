# Open Business Blueprint: cloud-itonami-isic-3822

This repository publishes an OSS business model for operating a hazardous
waste treatment/disposal compliance system (Clean Harbors / Veolia class)
on itonami.cloud.

## Classification

- Repository name: `cloud-itonami-isic-3822`
- Primary classification: ISIC Rev.4 3822
- Activity: manifest chain-of-custody tracking, treatment-authorization
  enforcement, and governed disclosure for hazardous-waste treatment
  facilities

## Customer

- hazardous-waste treatment/disposal facility operators
- regulators (EPA-equivalent agencies) needing governed, audit-ready
  manifest oversight
- generators/transporters needing a compliant chain-of-custody record

## Problem

Hazardous-waste compliance software vendors hold manifest and permit data
in closed systems. Facilities need a system that structurally prevents
treating an unauthorized waste-code/method combination or accepting a
shipment with a broken chain-of-custody — not just a UI warning that can
be dismissed.

## Offer

- manifest chain-of-custody intake and validation
- facility-permit-scoped treatment-authorization enforcement
- cross-border (Basel Convention) movement oversight, always human-reviewed
- governed, tier-scoped disclosure to regulators/clients
- manifest correction/dispute channel, always human-reviewed
- immutable audit ledger

## Revenue

- per-facility licensed deployment
- tiered subscriptions: `:tier/basic` (status lookup) → `:tier/regulator`
  (full chain/treatment-record access)
- compliance package: audit export, dispute-handling SLA, security review

## Non-Negotiables

- Do not commit real manifest data or real facility-permit credentials.
- Do not bypass the HazWasteGovernor for production operations.
- Do not fabricate a source-catalog entry.

## Marketplace Metadata

```edn
{:itonami.blueprint/id "cloud-itonami-isic-3822"
 :itonami.blueprint/name "Hazardous Waste Treatment/Disposal Compliance Actor"
 :itonami.blueprint/isic-rev4 "3822"
 :itonami.blueprint/domain :environmental/hazardous-waste
 :itonami.blueprint/license "AGPL-3.0-or-later"
 :itonami.blueprint/repo "https://github.com/cloud-itonami/cloud-itonami-isic-3822"
 :itonami.blueprint/status :public-oss}
```
