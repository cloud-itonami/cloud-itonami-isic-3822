# Hazardous Waste Actor Design — HazWasteTreatment-LLM as a contained intelligence node

## 1. なぜ actor 層が要るのか

マニフェストの正規化・処理記録の下書きは LLM で加速できるが、
**chain-of-custody の完全性判断と施設許可の妥当性判断は LLM に持たせられない**
— 出典なき受入確定/未許可の処理実行はそのまま実定法違反(EPA RCRA
Subtitle C)につながる。

## 2. OperationActor(`src/hazwaste/operation.cljc`)

`intake → advise(HazWasteTreatment-LLM) → govern(HazWasteGovernor) → decide
→ commit|hold|request-approval` の langgraph-clj StateGraph。1 run = 1 操作。

## 3. HazWasteGovernor(`src/hazwaste/policy.cljc`)

8チェック(HARD: rbac・manifest-chain-of-custody-gate・
treatment-method-authorization-gate・source-provenance-gate・
licensed-disclosure、SOFT: 確信度フロア・cross-border-gate・
dispute-request 無条件)。

## 4. SSoT(`src/hazwaste/store.cljc`)

`shipments`(manifestとchain)・`facility-permits`(waste-code×method許可)・
`treatment-records`・`contracts`。MemStore + DatomicStore、同一契約テストで
等価性保証。

## 5. Phase 0→3(`src/hazwaste/phase.cljc`)

`default-phase = 1`(セッション開始時点から保守的)。
`:correction/request` はどの phase の `:auto` にも入らない。

## 6. デモ(`clojure -M:dev:run`)

`src/hazwaste/sim.cljc` が7操作を通す(§sim.cljc docstring 参照)。
