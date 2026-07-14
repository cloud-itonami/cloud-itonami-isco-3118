# Governance

`cloud-itonami-isco-2161` is an OSS open-occupation blueprint. Governance covers
both code and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- the Advisor cannot directly issue stamped designs or certify code compliance.
- Architecture Governor remains independent of the advisor.
- hard policy violations cannot be overridden by human approval.
- every commit, hold and approval path is auditable.
- real client/project data stays outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit, support and data-flow
review.

Certified operators can lose certification for:

- bypassing policy checks
- mishandling client/project data
- misrepresenting certification status
- failing to respond to security incidents
- hiding material changes to customer-facing operation
