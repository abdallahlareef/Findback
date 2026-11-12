# Member 2 — Private Evidence Design

Status: Proposed prototype design, pending team review.

## First supported category

Earbuds.

## Evidence form

Both claimant and finder receive the same generic questions.
Questions must never reveal the other person's answers.

| Field | Input | Comparison |
|---|---|---|
| Serial suffix | Last 4 letters/digits, or unknown/unavailable | Automatic when both values are valid and comparable |
| Private identifying mark | Optional description | Manual staff review |
| Distinctive accessory | Optional description | Manual staff review |

Explain the four-character serial format on the form.
Missing information is allowed and produces Unknown.
Invalid serial input should show a validation message.
Do not collect passwords, unlock PINs or full payment-card numbers.
Do not add photo or document uploads in Version 1.0.

## Comparison rules

For the prototype, serial suffixes ignore surrounding spaces and letter case.
The team must confirm that this is appropriate for supported items.

Different free-text descriptions must not automatically mean Contradiction.
Staff assess narrative evidence.

Only useful, distinct private clues contribute to evidence strength.
Several details about one sticker count as one clue.
Public colour, brand and model do not increase private evidence strength.

## Evidence versions

Each stored version needs:

- A unique evidence version ID.
- The report ID it belongs to.
- The submitting actor ID, taken from the authenticated session.
- A version number and submission timestamp.
- Provenance: claimant, finder or authorized staff correction.
- Protected evidence values.
- A reason for supplements or corrections.

The baseline remains unchanged.
Corrections and supplements create new versions.
The server assigns version numbers and timestamps.

## Claimant submission limits

Use authenticated claimant account ID plus found-item ID.
Allow one initial submission and two staff-requested supplements.
Each claimant-submitted version counts once, including Unknown results.
Recreating a claim or report must not reset the counter.
Finder/staff corrections and recomparisons do not consume this counter.

A repeated request with identical content returns its original result.
Reusing its request ID with changed content is rejected.
Evidence saving and counter updates must succeed or fail together.

## Visibility

A submitting user may view their own evidence.
Neither party may view the opposing evidence, even after closure.
Only authorized staff with case permission may see comparison details.
Ordinary users receive neutral status messages without comparison counts.
Private evidence must not appear in public search, notifications or logs.

## Connections to other members

Member 1:
- Authenticated identity and report ownership.
- Report IDs and types.
- Baseline saved before candidate-specific information is shown.

Member 3:
- Claim links and case permissions.
- Authorized staff requests for supplements.
- Review, hold and reconsideration workflows.

Member 4:
- Audit events containing identifiers and outcomes, not raw evidence.
- Credential revocation when material evidence changes after approval.

## Decisions to agree before integration

- Shared ID types and API request/response formats.
- How a lost-report baseline is linked to the initial claimant/item submission.
- Who may review each case and request supplements.
- Which category-specific identifiers are critical.
- How material changes suspend collection and trigger fresh review.