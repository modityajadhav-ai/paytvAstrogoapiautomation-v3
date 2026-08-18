# Boxset Binge-Watch — Continue Watching (CW) & Watch Again (WA) Test Suite

**Feature:** Dual boxsets containing movies and TV shows with shared content (M1, T1) and unique content per boxset.  
**Completion threshold:** 97% playback **OR** `hasCompletedPlayback = true`.  
**Automation class:** `BoxsetBingeWatchScenarios` (`vrgo.cw.boxset.binge.*` properties).  
**Reference (movies-only single boxset):** `ContinueWatchScenarios` (`vrgo.cw.boxset.movies.*`).

## Test Data

| Label | Type | Boxset 1 | Boxset 2 | Content ID |
|-------|------|----------|----------|------------|
| Boxset 1 | Boxset | ✓ | | `3e640adc-8477-2dac-a794-746eb2a7bd2a` |
| Boxset 2 | Boxset | | ✓ | `4314b30f-d797-0e54-0a00-d47b681298a0` |
| M1 | Shared Movie | ✓ | ✓ | `2d7a7dca-572b-8e77-fab6-adb13e51a35c` |
| T1 | Shared TV Show | ✓ | ✓ | `42124121-c44a-cd2e-85f5-9627903bea7e` |
| M2 | Unique Movie | ✓ | | `1459e937-75d7-dea5-6517-15d8bd75f62a` |
| T2 | Unique TV Show | ✓ | | `b89ead0b-46fc-6ce8-3585-efcee8cf1dd3` |
| M3 | Unique Movie | | ✓ | `cb41fe98-d3f6-3274-143b-8550bd9d9659` |
| T3 | Unique TV Show | | ✓ | `4aeb49b5-304d-542a-1128-78288f49753c` |

---

## 1. Movie Scenarios — Shared M1

| Test Case ID | Module | Feature | Scenario | Preconditions | Test Data | Playback % | hasCompletedPlayback | Steps | Expected CW | Expected WA | API Validation | UI Validation | Priority | Test Type |
|--------------|--------|---------|----------|---------------|-----------|------------|----------------------|-------|---------------|---------------|----------------|---------------|----------|-----------|
| BB-CW-M01 | CW/WA | Boxset Binge | Shared movie M1 partially watched | Empty CW & WA; user entitled to both boxsets | M1 | 50% | false | 1. POST subscriber-continue-watch M1 partial 2. GET CW 3. GET WA | **CW:** M1 only. **Not:** M2,M3,T1,T2,T3 | **WA:** empty | CW API: M1 present, progress>0; WA API empty; no duplicate IDs | M1 card on CW rail only; no sibling cards | P1 | Positive |
| BB-CW-M02 | CW/WA | Boxset Binge | Shared movie M1 completed — dual boxset expansion | Empty CW & WA | M1 | 100% | true | 1. POST M1 completed 2. GET CW 3. GET WA | **CW:** M2,M3,T1,T2,T3 (T1 once). **Not:** M1 | **WA:** M1 | CW: M1 absent; siblings at 0%; T1 count=1; WA has M1 | M1 moves to WA; siblings appear on CW in backend order | P1 | Positive |
| BB-CW-M03 | CW/WA | Boxset Binge | Shared movie M1 replay after completion | M1 in WA; siblings in CW from M1 completion | M1 | 10% | false | 1. Resume/replay M1 partial 2. GET CW/WA | **CW:** M1 + existing siblings (per product rule) | **WA:** M1 retained or removed per replay rule | Verify replay does not duplicate M1; progress updates | M1 reappears on CW if partial replay allowed | P2 | Regression |
| BB-CW-M04 | CW/WA | Boxset Binge | Remove M1 playback history | M1 completed | M1 | — | — | 1. DELETE M1 CW/WA history 2. GET CW/WA | **CW:** siblings only if M1 removal triggers re-expand | **WA:** M1 removed | DELETE APIs succeed; lists consistent | M1 card removed from WA | P2 | Negative |

---

## 2. Movie Scenarios — Unique M2 (Boxset 1) & M3 (Boxset 2)

| Test Case ID | Module | Feature | Scenario | Preconditions | Test Data | Playback % | hasCompletedPlayback | Steps | Expected CW | Expected WA | API Validation | UI Validation | Priority | Test Type |
|--------------|--------|---------|----------|---------------|-----------|------------|----------------------|-------|---------------|---------------|----------------|---------------|----------|-----------|
| BB-CW-M05 | CW/WA | Boxset Binge | Unique movie M2 partially watched | Empty lists | M2 | 40% | false | POST M2 partial; GET CW/WA | **CW:** M2 only. **Not:** M1,M3,T1,T2,T3 | **WA:** empty | M2 in CW; boxset2 content absent | M2 on CW only | P1 | Positive |
| BB-CW-M06 | CW/WA | Boxset Binge | Unique movie M2 completed — boxset1 expansion only | Empty lists | M2 | 100% | true | POST M2 complete; GET CW/WA | **CW:** M1,T1,T2 at 0%. **Not:** M2,M3,T3 | **WA:** M2 | Boxset2 unique M3/T3 must NOT appear | Only boxset1 siblings on CW | P1 | Positive |
| BB-CW-M07 | CW/WA | Boxset Binge | Unique movie M3 partially watched | Empty lists | M3 | 45% | false | POST M3 partial; GET CW/WA | **CW:** M3 only | **WA:** empty | M3 only; no boxset1 leakage | M3 on CW only | P1 | Positive |
| BB-CW-M08 | CW/WA | Boxset Binge | Unique movie M3 completed — boxset2 expansion only | Empty lists | M3 | 100% | true | POST M3 complete; GET CW/WA | **CW:** M1,T1,T3 at 0%. **Not:** M3,M2,T2 | **WA:** M3 | Boxset1 unique M2/T2 must NOT appear | Only boxset2 siblings on CW | P1 | Positive |

---

## 3. TV Show Scenarios — Shared T1

| Test Case ID | Module | Feature | Scenario | Preconditions | Test Data | Playback % | hasCompletedPlayback | Steps | Expected CW | Expected WA | API Validation | UI Validation | Priority | Test Type |
|--------------|--------|---------|----------|---------------|-----------|------------|----------------------|-------|---------------|---------------|----------------|---------------|----------|-----------|
| BB-CW-T01 | CW/WA | Boxset Binge | Shared TV T1 partially watched | Empty lists | T1 episode | 30% | false | POST T1 partial; GET CW/WA | **CW:** T1 only. **Not:** M1,M2,M3,T2,T3 | **WA:** empty | T1 episode in CW; no siblings | T1 episode on CW | P1 | Positive |
| BB-CW-T02 | CW/WA | Boxset Binge | Shared TV T1 completed — dual boxset expansion | Empty lists | T1 episode | 100% | true | POST T1 complete; GET CW/WA | **CW:** M1,M2,M3,T2,T3 at 0%. **Not:** T1 episode | **WA:** T1 series editorial id | WA lists series not episode; CW has movies+siblings | T1 series on WA; siblings on CW | P1 | Positive |
| BB-CW-T03 | CW/WA | Boxset Binge | Resume T1 after completion | T1 in WA | T1 | 5% | false | Replay T1; GET CW/WA | Per replay business rule | T1 series handling | Episode vs series IDs correct | Correct rail transition | P2 | Regression |
| BB-CW-T04 | CW/WA | Boxset Binge | Replay completed T1 | T1 in WA | T1 | 100% | true | Replay complete; GET CW/WA | No duplicate T1; siblings stable | T1 remains once in WA | No duplicate series rows | No duplicate WA cards | P2 | Regression |

---

## 4. TV Show Scenarios — Unique T2 & T3

| Test Case ID | Module | Feature | Scenario | Preconditions | Test Data | Playback % | hasCompletedPlayback | Steps | Expected CW | Expected WA | API Validation | UI Validation | Priority | Test Type |
|--------------|--------|---------|----------|---------------|-----------|------------|----------------------|-------|---------------|---------------|----------------|---------------|----------|-----------|
| BB-CW-T05 | CW/WA | Boxset Binge | Unique TV T2 partially watched | Empty lists | T2 | 35% | false | POST T2 partial | **CW:** T2 only | **WA:** empty | No boxset2 content | T2 on CW | P1 | Positive |
| BB-CW-T06 | CW/WA | Boxset Binge | Unique TV T2 completed — boxset1 only | Empty lists | T2 | 100% | true | POST T2 complete | **CW:** M1,M2,T1 at 0%. **Not:** T2,M3,T3 | **WA:** T2 series | Boxset2 content absent | Boxset1 siblings only | P1 | Positive |
| BB-CW-T07 | CW/WA | Boxset Binge | Unique TV T3 partially watched | Empty lists | T3 | 40% | false | POST T3 partial | **CW:** T3 only | **WA:** empty | No boxset1 content | T3 on CW | P1 | Positive |
| BB-CW-T08 | CW/WA | Boxset Binge | Unique TV T3 completed — boxset2 only | Empty lists | T3 | 100% | true | POST T3 complete | **CW:** M1,M3,T1 at 0%. **Not:** T3,M2,T2 | **WA:** T3 series | Boxset1 unique absent | Boxset2 siblings only | P1 | Positive |

---

## 5. Boundary Values (97% Threshold)

| Test Case ID | Module | Feature | Scenario | Preconditions | Test Data | Playback % | hasCompletedPlayback | Steps | Expected CW | Expected WA | API Validation | UI Validation | Priority | Test Type |
|--------------|--------|---------|----------|---------------|-----------|------------|----------------------|-------|---------------|---------------|----------------|---------------|----------|-----------|
| BB-BND-01 | CW/WA | Boxset Binge | M1 at 96.99% — below threshold | Empty lists | M1 | 96.99% | false | POST at 96.99%; GET CW/WA | **CW:** M1. **Not:** siblings | **WA:** empty | progress<97%; hasCompletedPlayback=false | M1 stays on CW | P1 | Boundary |
| BB-BND-02 | CW/WA | Boxset Binge | M1 at 97.00% — at threshold | Empty lists | M1 | 97.00% | false | POST at 97.00% without flag | **CW:** empty siblings promoted OR M1 per API rule | **WA:** M1 if treated complete | Verify 97.00% triggers completion | Transition at threshold | P1 | Boundary |
| BB-BND-03 | CW/WA | Boxset Binge | M1 at 97.01% — above threshold | Empty lists | M1 | 97.01% | false | POST at 97.01% | **CW:** M2,M3,T1,T2,T3. **Not:** M1 | **WA:** M1 | progress≥97% | Completion expansion | P1 | Boundary |
| BB-BND-04 | CW/WA | Boxset Binge | T1 at 96.99% | Empty lists | T1 | 96.99% | false | POST T1 96.99% | **CW:** T1 only | **WA:** empty | Below threshold | T1 on CW | P1 | Boundary |
| BB-BND-05 | CW/WA | Boxset Binge | T1 at 97.00% | Empty lists | T1 | 97.00% | false | POST T1 97.00% | Per 97% rule — siblings promoted | T1 series in WA if complete | Threshold behavior | Rail transition | P1 | Boundary |
| BB-BND-06 | CW/WA | Boxset Binge | T1 at 97.01% | Empty lists | T1 | 97.01% | false | POST T1 97.01% | **CW:** M1,M2,M3,T2,T3 | **WA:** T1 series | Above threshold complete | Expansion | P1 | Boundary |

---

## 6. Conflicting Playback (Requirement Clarification)

| Test Case ID | Module | Feature | Scenario | Preconditions | Test Data | Playback % | hasCompletedPlayback | Steps | Expected CW | Expected WA | API Validation | UI Validation | Priority | Test Type |
|--------------|--------|---------|----------|---------------|-----------|------------|----------------------|-------|---------------|---------------|----------------|---------------|----------|-----------|
| BB-CLF-01 | CW/WA | Boxset Binge | M1: 96.99% + hasCompletedPlayback=true | Empty lists | M1 | 96.99% | **true** | POST with conflict | **Clarify:** WA if flag wins OR CW if % wins | Opposite rail per winning rule | Document actual API precedence | UI matches winning rule | P1 | Edge |
| BB-CLF-02 | CW/WA | Boxset Binge | M1: 97% + hasCompletedPlayback=false | Empty lists | M1 | 97.00% | **false** | POST with conflict | **Clarify:** complete if % wins | Per winning rule | Log hasCompletedPlayback vs % | UI consistency | P1 | Edge |
| BB-CLF-03 | CW/WA | Boxset Binge | M1: 97.01% + hasCompletedPlayback=false | Empty lists | M1 | 97.01% | **false** | POST with conflict | Should complete via % | M1 in WA if % wins | API fields cross-check | Rail transition | P1 | Edge |
| BB-CLF-04 | CW/WA | Boxset Binge | T1: 96.99% + hasCompletedPlayback=true | Empty lists | T1 | 96.99% | **true** | POST conflict | Clarify precedence | Clarify precedence | Both APIs | Both rails | P2 | Edge |

---

## 7. Boxset Expansion & Merge Logic

| Test Case ID | Module | Feature | Scenario | Preconditions | Test Data | Playback % | hasCompletedPlayback | Steps | Expected CW | Expected WA | API Validation | UI Validation | Priority | Test Type |
|--------------|--------|---------|----------|---------------|-----------|------------|----------------------|-------|---------------|---------------|----------------|---------------|----------|-----------|
| BB-EXP-01 | CW/WA | Boxset Binge | Shared M1 expands both boxsets | Empty lists | M1 | 100% | true | Complete M1 | **CW:** M2,M3,T1,T2,T3 merged | **WA:** M1 | All 5 siblings; T1 once | 5 unique CW cards | P1 | Positive |
| BB-EXP-02 | CW/WA | Boxset Binge | Shared T1 expands both boxsets | Empty lists | T1 | 100% | true | Complete T1 | **CW:** M1,M2,M3,T2,T3 | **WA:** T1 series | Dual boxset merge | No duplicate T1 | P1 | Positive |
| BB-EXP-03 | CW/WA | Boxset Binge | M2 expands boxset1 only | Empty lists | M2 | 100% | true | Complete M2 | **CW:** M1,T1,T2 | **WA:** M2 | M3,T3 absent | 3 CW cards | P1 | Positive |
| BB-EXP-04 | CW/WA | Boxset Binge | M3 expands boxset2 only | Empty lists | M3 | 100% | true | Complete M3 | **CW:** M1,T1,T3 | **WA:** M3 | M2,T2 absent | 3 CW cards | P1 | Positive |
| BB-MRG-01 | CW/WA | Boxset Binge | Merge with existing CW content | M2 already in CW (partial) | M1 | 100% | true | Complete M1 | **CW:** retains M2 + promoted siblings deduped | **WA:** M1 | M2 not duplicated | Existing M2 preserved | P1 | Regression |
| BB-MRG-02 | CW/WA | Boxset Binge | Merge with existing WA content | M3 already in WA | M1 | 100% | true | Complete M1 | **CW:** new siblings; **WA:** M1 + existing M3 | Both rails | No WA duplicates | Both rails correct | P2 | Regression |

---

## 8. Deduplication

| Test Case ID | Module | Feature | Scenario | Preconditions | Test Data | Playback % | hasCompletedPlayback | Steps | Expected CW | Expected WA | API Validation | UI Validation | Priority | Test Type |
|--------------|--------|---------|----------|---------------|-----------|------------|----------------------|-------|---------------|---------------|----------------|---------------|----------|-----------|
| BB-DED-01 | CW/WA | Boxset Binge | Shared M1 — T1 appears once | Empty lists | M1 | 100% | true | Complete M1; count T1 in CW | **CW:** T1 ×1 | **WA:** M1 | T1 ID count=1 in response | Single T1 card | P1 | Positive |
| BB-DED-02 | CW/WA | Boxset Binge | Shared T1 — M1 appears once | Empty lists | T1 | 100% | true | Complete T1 | **CW:** M1 ×1 | **WA:** T1 | M1 ID count=1 | Single M1 card | P1 | Positive |
| BB-DED-03 | CW/WA | Boxset Binge | Multiple POST updates — no duplicates | M1 partial in CW | M1 | 60%→80% | false | Two partial POSTs | **CW:** M1 ×1 | **WA:** empty | ID uniqueness | Single card | P2 | Regression |
| BB-DED-04 | CW/WA | Boxset Binge | Complete M1 then M3 — shared M1 once | M1 complete first | M3 | 100% | true | Complete M3 after M1 | **CW:** no duplicate M1; T1 once | **WA:** M1,M3 | Merge dedup | No duplicate shared items | P2 | Regression |

---

## 9. Ordering Rules

| Test Case ID | Module | Feature | Scenario | Preconditions | Test Data | Playback % | hasCompletedPlayback | Steps | Expected CW | Expected WA | API Validation | UI Validation | Priority | Test Type |
|--------------|--------|---------|----------|---------------|-----------|------------|----------------------|-------|---------------|---------------|----------------|---------------|----------|-----------|
| BB-ORD-01 | CW/WA | Boxset Binge | Backend ingestion order preserved on expansion | Empty lists | M1 | 100% | true | Complete M1; capture CW order | **CW:** siblings in backend ingestion order | **WA:** M1 | Compare order to boxset binge API | UI matches API order | P1 | Positive |
| BB-ORD-02 | CW/WA | Boxset Binge | Order stable after CW→WA transition | M1 in CW partial | M1 | 100% | true | Complete M1; compare order before/after | Order of unaffected items unchanged | M1 to WA | Order array stable | No reorder glitch | P2 | Regression |
| BB-ORD-03 | CW/WA | Boxset Binge | New siblings follow backend order | M2 in CW | M2 | 100% | true | Complete M2 | New items inserted per ingestion time | **WA:** M2 | Not manually sortable | UI follows backend | P2 | Positive |
| BB-ORD-04 | CW/WA | Boxset Binge | Dual boxset merge preserves backend order | Empty lists | M1 | 100% | true | Complete M1 | Merged list order = backend order across both boxsets | **WA:** M1 | No reorder on merge | Consistent UI order | P2 | Positive |

---

## 10. Continue Watching Validation

| Test Case ID | Module | Feature | Scenario | Preconditions | Test Data | Playback % | hasCompletedPlayback | Steps | Expected CW | Expected WA | API Validation | UI Validation | Priority | Test Type |
|--------------|--------|---------|----------|---------------|-----------|------------|----------------------|-------|---------------|---------------|----------------|---------------|----------|-----------|
| BB-CWV-01 | CW | Boxset Binge | Correct content appears after partial watch | Empty | M1 | 50% | false | GET CW | **CW:** M1 | **WA:** empty | Content IDs match | Correct card | P1 | Positive |
| BB-CWV-02 | CW | Boxset Binge | Completed content removed from CW | M1 partial | M1 | 100% | true | GET CW | **Not:** M1 | **WA:** M1 | M1 absent from CW API | M1 off CW rail | P1 | Positive |
| BB-CWV-03 | CW | Boxset Binge | Existing CW preserved on expansion | M2 partial in CW | M1 | 100% | true | GET CW | **CW:** M2 + siblings | **WA:** M1 | M2 still present | M2 card retained | P1 | Regression |
| BB-CWV-04 | CW | Boxset Binge | No duplicate cards in CW | Empty | M1 | 100% | true | GET CW | Unique IDs only | **WA:** M1 | Duplicate ID scan | No duplicate UI cards | P1 | Positive |
| BB-CWV-05 | CW | Boxset Binge | No series/boxset editorial in CW list | Empty | M1 | 100% | true | GET CW | Only movie/TV episode rows | **WA:** M1 | No boxset1/2 IDs in CW | No boxset cards on CW | P2 | Negative |

---

## 11. Watch Again Validation

| Test Case ID | Module | Feature | Scenario | Preconditions | Test Data | Playback % | hasCompletedPlayback | Steps | Expected CW | Expected WA | API Validation | UI Validation | Priority | Test Type |
|--------------|--------|---------|----------|---------------|-----------|------------|----------------------|-------|---------------|---------------|----------------|---------------|----------|-----------|
| BB-WAV-01 | WA | Boxset Binge | Completed movie in WA | Empty | M1 | 100% | true | GET WA | **CW:** siblings | **WA:** M1 | M1 in WA response | M1 on WA rail | P1 | Positive |
| BB-WAV-02 | WA | Boxset Binge | Completed TV in WA (series row) | Empty | T1 | 100% | true | GET WA | **CW:** siblings | **WA:** T1 series id | Series not episode in WA | Series card on WA | P1 | Positive |
| BB-WAV-03 | WA | Boxset Binge | Existing WA preserved | M3 in WA | M1 | 100% | true | GET WA | **CW:** siblings | **WA:** M1,M3 | Both movies in WA | Both cards visible | P2 | Regression |
| BB-WAV-04 | WA | Boxset Binge | No duplicates in WA | Empty | M1,M2 sequential complete | 100% | true | GET WA | **CW:** per rules | **WA:** M1,M2 once each | Unique WA IDs | No duplicate WA cards | P2 | Positive |

---

## 12. Regression Scenarios

| Test Case ID | Module | Feature | Scenario | Preconditions | Test Data | Playback % | hasCompletedPlayback | Steps | Expected CW | Expected WA | API Validation | UI Validation | Priority | Test Type |
|--------------|--------|---------|----------|---------------|-----------|------------|----------------------|-------|---------------|---------------|----------------|---------------|----------|-----------|
| BB-REG-01 | CW/WA | Boxset Binge | Multiple completed contents | Empty | M1 then M2 complete | 100% | true | Complete both | **CW:** latest expansion merge | **WA:** M1,M2 | Both in WA; CW deduped | Both rails correct | P1 | Regression |
| BB-REG-02 | CW/WA | Boxset Binge | Multiple in-progress contents | Empty | M1 partial + T2 partial | <97% | false | POST both | **CW:** M1,T2 | **WA:** empty | No cross-promotion | Two CW cards | P1 | Regression |
| BB-REG-03 | CW/WA | Boxset Binge | Shared M1 already in CW — then complete | M1 partial | M1 | 100% | true | Complete M1 | Expansion from existing state | **WA:** M1 | State transition | Smooth rail update | P2 | Regression |
| BB-REG-04 | CW/WA | Boxset Binge | Shared T1 already in CW — then complete | T1 partial | T1 | 100% | true | Complete T1 | Siblings promoted | **WA:** T1 series | TV series handling | WA shows series | P2 | Regression |
| BB-REG-05 | CW/WA | Boxset Binge | Refresh application | M1 complete state | M1 | — | — | Kill/relaunch app; GET CW/WA | Same as pre-refresh | Same as pre-refresh | Persistence | Rails unchanged | P1 | Regression |
| BB-REG-06 | CW/WA | Boxset Binge | Logout/Login | M1 complete state | M1 | — | — | Logout; login same user | CW/WA restored | CW/WA restored | Subscriber-scoped | UI restored | P1 | Regression |
| BB-REG-07 | CW/WA | Boxset Binge | Multiple playback updates | M1 partial | M1 | 30%→60%→90% | false | Sequential POSTs | **CW:** M1 only until complete | **WA:** empty until complete | Progress increases | Progress bar updates | P2 | Regression |
| BB-REG-08 | CW/WA | Boxset Binge | Replaying completed M1 | M1 in WA | M1 | 10% | false | Replay | Per product replay rules | **WA:** per rules | Replay API | Replay UX | P2 | Regression |

---

## 13. API-Specific Validation Matrix

| Test Case ID | Module | Feature | Scenario | Preconditions | Test Data | Playback % | hasCompletedPlayback | Steps | Expected CW | Expected WA | API Validation | UI Validation | Priority | Test Type |
|--------------|--------|---------|----------|---------------|-----------|------------|----------------------|-------|---------------|---------------|----------------|---------------|----------|-----------|
| BB-API-01 | API | Boxset Binge | GET continue-watch list | Any state | All | — | — | GET `/continue-watch/continue` | Per scenario | Per scenario | status=200; content IDs; ordering; no duplicates | N/A | P1 | Positive |
| BB-API-02 | API | Boxset Binge | GET watch-again list | Completed content | M1/T1 | 100% | true | GET `/watch-again` | Per scenario | Completed items | status=200; movie vs series ids | N/A | P1 | Positive |
| BB-API-03 | API | Boxset Binge | GET boxset CW content | M1 in CW | Boxset1 | — | — | GET `/continue-watch/content/boxset/{id}` | watchedBoxsetContent shape | N/A | boxsetId; progress fields | N/A | P2 | Positive |
| BB-API-04 | API | Boxset Binge | GET boxset binge (content-detail) | Entitled | Boxset1/2 | — | — | GET `/boxset/{id}/binge` | N/A | N/A | 200; child order matches CW expansion order | N/A | P2 | Positive |
| BB-API-05 | API | Boxset Binge | POST subscriber-continue-watch | Empty | M1 | varies | varies | POST with/without flag | Per rules | Per rules | 200; hasCompletedPlayback honored | N/A | P1 | Positive |
| BB-API-06 | API | Boxset Binge | POST cw/v3/progress | After watch | M1 | 50% | false | POST progress | M1 progress>0 | N/A | Progress matches playback | N/A | P2 | Positive |
| BB-API-07 | API | Boxset Binge | Missing content check | M1 complete | M1 | 100% | true | GET CW | No unexpected IDs | N/A | Only expected siblings | N/A | P1 | Negative |
| BB-API-08 | API | Boxset Binge | Unexpected content check | M2 partial | M2 | 50% | false | GET CW | No M3,T3 | N/A | Boxset2 ids absent | N/A | P1 | Negative |

---

## 14. UI Validation Summary

| Test Case ID | Module | Feature | Scenario | Preconditions | Test Data | Playback % | hasCompletedPlayback | Steps | Expected CW | Expected WA | API Validation | UI Validation | Priority | Test Type |
|--------------|--------|---------|----------|---------------|-----------|------------|----------------------|-------|---------------|---------------|----------------|---------------|----------|-----------|
| BB-UI-01 | UI | Boxset Binge | Correct cards on CW rail | M1 partial | M1 | 50% | false | Open home CW | **CW:** M1 | empty | — | M1 poster/title visible | P1 | Positive |
| BB-UI-02 | UI | Boxset Binge | Correct cards on WA rail | M1 complete | M1 | 100% | true | Open WA rail | siblings on CW | **WA:** M1 | — | M1 on WA only | P1 | Positive |
| BB-UI-03 | UI | Boxset Binge | Correct ordering on CW | M1 complete | M1 | 100% | true | Visual order check | Backend order | — | — | Matches content-detail binge order | P1 | Positive |
| BB-UI-04 | UI | Boxset Binge | No duplicate cards | M1 complete | M1 | 100% | true | Count T1 cards | T1 ×1 | M1 in WA | — | Single T1 tile | P1 | Positive |
| BB-UI-05 | UI | Boxset Binge | Rail transition CW→WA | M1 partial→complete | M1 | 100% | true | Complete playback | M1 leaves CW | M1 enters WA | — | Smooth animation/state | P2 | Positive |

---

## Automation Mapping

| Automated in `BoxsetBingeWatchScenarios` | Manual / Future |
|------------------------------------------|-----------------|
| BB-CW-M01, BB-CW-M02, BB-CW-M05, BB-CW-M06 | BB-CW-M03, BB-CW-M04 |
| BB-CW-T01, BB-CW-T02 | BB-CW-T03, BB-CW-T04 |
| BB-EXP-01, BB-EXP-02, BB-EXP-03 | BB-EXP-04 (add M3 scenario) |
| BB-DED-01 | BB-DED-02–04 |
| BB-API-04, BB-API-03 | Boundary & conflict cases (BB-BND-*, BB-CLF-*) |
| BB-WAV-01, BB-WAV-02 (partial) | UI cases (BB-UI-*), logout/refresh (BB-REG-05/06) |

**Run automation:**
```bash
mvn test -Ptest "-Dtest=BoxsetBingeWatchScenarios"
```

**Configure test env:** `src/test/resources/environments/test.properties` → `vrgo.cw.boxset.binge.*`
