# Security Policy

This project handles hazardous-waste manifest chain-of-custody and
treatment-authorization records. Treat vulnerabilities as potentially high
impact even when the demo data is synthetic — a falsified or bypassed
manifest/permit check has direct environmental and regulatory consequences.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential or facility-permit-key exposure
- HazWasteGovernor bypass (chain-of-custody-gate, treatment-method-
  authorization-gate, source-provenance-gate, licensed-disclosure)
- audit-ledger tampering
- over-disclosure beyond a subscriber contract's tier
- ingestion of a manifest through an undocumented path
- treatment execution against an unauthorized or lapsed facility permit

## Reporting

Use GitHub private vulnerability reporting when available for the
repository. If that is unavailable, contact the repository maintainers
through the cloud-itonami organization before publishing details.

Include: affected commit or version, reproduction steps, expected and
actual behavior, impact on manifest data, governor enforcement or audit
logging, suggested fix if known.

## Production Guidance

- Store secrets and facility-permit credentials outside Git.
- Run governor tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for treatment operators and service accounts.
- Alert on any chain-of-custody-gate or treatment-method-authorization-gate
  HOLD spike — it may indicate a compromised or malfunctioning intake path.
