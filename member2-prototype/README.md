# Member 2 — DOVE Prototype

## Purpose

Develop and test the evidence comparison rules independently before
integrating them into the shared Spring Boot application.

## Current features

- Spring Boot comparison API for four-character serial suffixes.
- Automated backend tests for Match, Contradiction, Unknown and normalization.
- In-memory verification history recording report IDs, evidence version
  numbers, comparison-rule version, timestamp and serial comparison result.
- Connected submission limits and evidence history: blocked submissions
  create no version, and identical retries return the original version.
- In-memory claimant/item submission limits: one initial submission
  plus two staff-requested supplements, with duplicate-request handling.
- In-memory evidence history with an immutable baseline and new correction versions.
- Compare four-character serial suffixes:
  MATCH, UNKNOWN or CONTRADICTION.
- Count useful matched clue groups once.
- Summarize evidence as WEAK, MODERATE, STRONG,
  REVIEW_REQUIRED or ON_HOLD.
- Exclude public or non-useful matches from the strength count.
- Exclude a clue group from the match count if it has a contradiction.
- Give critical identifier contradictions priority.

## Requirements

Java JDK 21.

## Run all checks

Run these commands from the main Findback folder:

    javac -d member2-prototype/build member2-prototype/*.java
    java -cp member2-prototype/build DoveComparison
    java -cp member2-prototype/build EvidenceStrength
    java -cp member2-prototype/build DistinctClueCounter
    java -cp member2-prototype/build DoveAssessment
    java -cp member2-prototype/build EvidenceHistory
    java -cp member2-prototype/build SubmissionLimit
    java -cp member2-prototype/build EvidenceSubmissionService
    java -cp member2-prototype/build VerificationHistory
Expected passing checks: 12 + 8 + 6 + 6 + 6 + 12 + 9 + 6 = 65.

## Prototype assumptions

- Serial suffixes use exactly four ASCII letters or digits.
- Letter case and surrounding spaces are ignored for these suffixes.
- Group IDs identify the same physical clue consistently.
- Assessment examples use invented comparison results.
- Useful/private and critical-identifier flags are supplied by test code.
  Production rules and authorized review must control these flags.

The team must agree on category-specific evidence rules before integration.

## Not implemented yet

- Private evidence forms and APIs.
- Database storage and protected evidence handling.
- Database-backed evidence history, report/author links and correction permissions.
- Persistent submission counters, real staff authorization and saving
- Evidence together with its submission count in one database transaction.
- Login, case permissions and staff-only result access.
- Audit events and integration with claims and handover.
- Persistent verification attempts and validation that evidence versions
  belong to the specified reports and claim.

## Important boundaries

STRONG does not approve ownership or authorize collection.
ON_HOLD is currently a calculated result; it does not update a real claim.
This prototype does not yet demonstrate evidence privacy or access control.
## Backend prototype

The standalone Spring Boot project is in `member2-backend/member2-backend`.
Run it from that folder with:

    .\mvnw.cmd spring-boot:run

The local comparison endpoint accepts invented values at
`POST /api/member2/dove/compare`.

Example JSON request:

    {"ownerSuffix":"A82F","finderSuffix":"A82F"}

The prototype returns `MATCH`, `CONTRADICTION` or `UNKNOWN`.
It has no authentication or case-level authorization. Do not send real
private evidence to it.

Run backend tests from that folder with:

    .\mvnw.cmd test