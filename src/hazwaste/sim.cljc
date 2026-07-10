(ns hazwaste.sim
  "Demo runner: push seven representative operations through one
  OperationActor.

    op1  完全な chain-of-custody を持つ受入(出典あり)          → commit
    op2  chain-of-custody が不完全(facility 未署名)            → chain-of-custody-gate REJECT → hold
    op3  未許可の waste-code/method 組み合わせでの処理実行      → treatment-method-authorization-gate REJECT → hold
    op4  失効した許可での処理実行                              → treatment-method-authorization-gate REJECT → hold
    op5  開示クエリが tier/basic 契約なのに chain/raw-source を要求 → licensed-disclosure REJECT → hold
    op6  越境移動(cross-border)のマニフェスト受入              → 人間承認へ escalate → approve → commit
    op7  マニフェスト訂正申立て(どの phase でも常に人間レビュー) → escalate → approve → commit

  Run: clojure -M:dev:run"
  (:require [langgraph.graph :as g]
            [hazwaste.store :as store]
            [hazwaste.operation :as op]
            [hazwaste.facts :as facts]
            [hazwaste.report :as report]))

(defn- line [& xs] (println (apply str xs)))

(defn- run-op! [actor thread-id request context approve?]
  (let [res (g/run* actor {:request request :context context} {:thread-id thread-id})]
    (if (= :interrupted (:status res))
      (do (line "   ⏸  人間レビュー待ち (reason: " (-> res :state :audit last :reason) ")")
          (let [res2 (g/run* actor {:approval {:status (if approve? :approved :rejected) :by "compliance-1"}}
                             {:thread-id thread-id :resume? true})]
            (line "   ▶  " (if approve? "承認 → " "却下 → ") "disposition = " (get-in res2 [:state :disposition]))
            res2))
      (do (line "   → disposition = " (get-in res [:state :disposition])
                "  (confidence " (get-in res [:state :verdict :confidence]) ")")
          res))))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)
        operator {:actor-id "op-1" :actor-role :treatment-operator :phase 3}
        officer  {:actor-id "co-1" :actor-role :compliance-officer :phase 3}]

    (line "── R0 出典カバレッジ ──")
    (line (pr-str (facts/coverage)))
    (line "\n── OperationActor (HazWasteTreatment-LLM sealed; HazWasteGovernor active) ──")

    (line "\nop1  完全な chain-of-custody を持つ受入")
    (run-op! actor "op1"
             {:op :manifest/receive :subject "man-300" :manifest-id "man-300" :generator-id "gen-3"
              :transporter-id "trans-3" :facility-id "fac-1" :waste-code "D001" :quantity-kg 300
              :cross-border? false
              :chain [{:step :generator :signed-by "gen-3" :at "2026-07-05"}
                      {:step :transporter :signed-by "trans-3" :at "2026-07-06"}
                      {:step :facility :signed-by "fac-1" :at "2026-07-07"}]
              :source {:class :rcra-manifest-system :ref "epa-manifest:man-300"}}
             operator true)

    (line "\nop2  chain-of-custody が不完全(facility 未署名)")
    (run-op! actor "op2"
             {:op :manifest/receive :subject "man-301" :manifest-id "man-301" :generator-id "gen-4"
              :transporter-id "trans-4" :facility-id "fac-1" :waste-code "D001" :quantity-kg 150
              :cross-border? false
              :chain [{:step :generator :signed-by "gen-4" :at "2026-07-05"}
                      {:step :transporter :signed-by "trans-4" :at "2026-07-06"}]
              :source {:class :rcra-manifest-system :ref "epa-manifest:man-301"}
              :broken-chain? true}
             operator true)

    (line "\nop3  未許可の waste-code/method 組み合わせでの処理実行(man-100 は D001, perm-1 は D001+incineration のみ許可)")
    (run-op! actor "op3"
             {:op :treatment/execute :subject "man-100" :manifest-id "man-100" :method :landfill
              :completed-at "2026-07-08" :residue-disposition :none
              :source {:class :rcra-manifest-system :ref "epa-manifest:man-100-treat"}}
             operator true)

    (line "\nop4  失効した許可(perm-2, D008)での処理実行")
    (run-op! actor "op4"
             {:op :treatment/execute :subject "man-200" :manifest-id "man-200" :method :landfill
              :completed-at "2026-07-08" :residue-disposition :none
              :source {:class :basel-convention-movement-document :ref "basel:man-200-treat"}}
             operator true)

    (line "\nop5  開示クエリ(tier/basic 契約なのに chain/raw-source まで要求)")
    (run-op! actor "op5"
             {:op :disclosure/query :subject "man-100" :manifest-id "man-100" :greedy? true}
             {:actor-id "sub-1" :actor-role :subscriber :tenant "tenant-basic"} true)

    (line "\nop6  越境移動(cross-border)マニフェストの受入(出典・chain は正常でも人間承認)")
    (run-op! actor "op6"
             {:op :manifest/receive :subject "man-302" :manifest-id "man-302" :generator-id "gen-5"
              :transporter-id "trans-5" :facility-id "fac-1" :waste-code "D001" :quantity-kg 400
              :cross-border? true
              :chain [{:step :generator :signed-by "gen-5" :at "2026-07-05"}
                      {:step :transporter :signed-by "trans-5" :at "2026-07-06"}
                      {:step :facility :signed-by "fac-1" :at "2026-07-07"}]
              :source {:class :basel-convention-movement-document :ref "basel:man-302"}}
             operator true)

    (line "\nop7  マニフェスト訂正申立て(どの phase でも常に人間レビュー)")
    (run-op! actor "op7"
             {:op :correction/request :subject "man-100" :disputed-field :quantity-kg :claim 480}
             officer true)

    (line "\n── 開示(governor が承認した tier/basic 列のみ) ──")
    (line (pr-str (report/render-shipment db "man-100" [:manifest-id :facility-id :waste-code :status])))

    (line "\n── 監査台帳 ──")
    (doseq [f (store/ledger db)] (line "  " (store/ledger-line f)))
    (line "\ndone.")))
