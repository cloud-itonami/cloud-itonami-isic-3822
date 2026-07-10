# Operator Guide

## 1. Fork and Run

```bash
git clone https://github.com/cloud-itonami/cloud-itonami-isic-3822
cd cloud-itonami-isic-3822
clojure -M:dev:test
clojure -M:dev:run
```

## 2. Production Checklist

- replace demo shipments/permits with real, source-cited manifest data
  (extend `hazwaste.facts/catalog` honestly — never fabricate a regulatory
  regime)
- configure Datomic Local, kotoba-server or an equivalent durable SSoT
- configure the LLM adapter through environment variables or secret manager
- define subscriber contract tenants/tiers and RBAC rules
- run `clojure -M:dev:test` and `clojure -M:lint`
- verify audit-ledger export
- document backup/restore and incident response
- get written legal review for the jurisdictions you serve (RCRA/EU Waste
  Shipment Regulation/Basel Convention obligations vary by jurisdiction)

## 3. Operator Responsibilities

- lawful basis and facility-permit accuracy for each waste-code/method
  combination served
- secure infrastructure and tenant isolation
- honest source-catalog maintenance
- human review workflow for cross-border and correction-request operations
- data-retention policy and security updates

The OSS project provides software and an operating blueprint. It does not
make an operator compliant by itself.
