# ADR-0001: cloud-itonami-isic-3822 — HazWasteTreatment-LLM を封じ込めた知能ノードとする危険廃棄物処理/処分アクター設計

- Status: Accepted (2026-07-10)
- 関連: `cloud-itonami-isic-3811`(非危険廃棄物収集、直接の兄弟actor)、
  `cloud-itonami-isic-6311`(フリート標準パターンの手本)

## 課題

`cloud-itonami-isic-3811`(非危険廃棄物収集)の兄弟として、危険廃棄物の
**処理・処分**業態を新設する。3811 が「非危険物であることを前提に収集を
ディスパッチする」業態であるのに対し、3822 は**危険廃棄物そのものを合法的に
処理・処分する**業態であり、EPA RCRA Subtitle C マニフェスト制度・Basel
Convention 越境移動規制という、はるかに重い規制枠組みに服する。

## 決定

HazWasteTreatment-LLM を最下層ノードに封じ込め、chain-of-custody の完全性
判断と施設許可の妥当性判断を独立した HazWasteGovernor に委ねる。単一不変
条件:

> **HazWasteTreatment-LLM は、HazWasteGovernor が拒否するマニフェスト受入・
> 処理実行・開示・訂正確定を決して行わない。**

`manifest-chain-of-custody-gate`(generator→transporter→facility の署名済み
チェーンが不完全なら拒否)と `treatment-method-authorization-gate`(施設の
有効な許可が対象 waste-code×method を認可していなければ拒否)は、3811 には
存在しない domain-unique HARD チェック。`cross-border-gate` は Basel
Convention の越境移動監督義務を反映した SOFT(常時escalate)チェック。

## Consequences

- (+) 3811/3822 の2つで「非危険物収集」と「危険物処理」という法的責任構造の
  異なる2業態を明確に分離した。
- (+) `clojure -M:dev:test`/`clojure -M:lint` クリーン。

## References

- `90-docs/adr/2607111500-cloud-itonami-isic-6311-market-data-actor.md`
