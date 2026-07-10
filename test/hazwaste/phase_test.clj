(ns hazwaste.phase-test
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [hazwaste.store :as store]
            [hazwaste.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :treatment-operator})
(def officer  {:actor-id "co-1" :actor-role :compliance-officer})

(def clean-receive
  {:op :manifest/receive :subject "man-300" :manifest-id "man-300" :generator-id "gen-3"
   :transporter-id "trans-3" :facility-id "fac-1" :waste-code "D001" :quantity-kg 300
   :cross-border? false
   :chain [{:step :generator :signed-by "gen-3" :at "2026-07-05"}
           {:step :transporter :signed-by "trans-3" :at "2026-07-06"}
           {:step :facility :signed-by "fac-1" :at "2026-07-07"}]
   :source {:class :rcra-manifest-system :ref "epa-manifest:man-300"}})

(def clean-treatment
  {:op :treatment/execute :subject "man-100" :manifest-id "man-100" :method :incineration
   :completed-at "2026-07-09" :residue-disposition :none
   :source {:class :rcra-manifest-system :ref "epa-manifest:man-100-treat"}})

(def clean-disclosure {:op :disclosure/query :subject "man-100" :manifest-id "man-100"})
(def correction-req {:op :correction/request :subject "man-100" :disputed-field :quantity-kg :claim 480})

(defn- run [phase req ctx]
  (let [s (store/seed-db) actor (op/build s)]
    [s (g/run* actor {:request req :context (assoc ctx :phase phase)} {:thread-id (str "ph-" phase "-" (:op req))})]))

(deftest phase0-holds-all-writes
  (let [[s res] (run 0 clean-receive operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (= :phase-disabled (-> (store/ledger s) first :phase-reason)))))

(deftest phase0-allows-governed-reads
  (let [[_ res] (run 0 clean-disclosure {:actor-id "sub-1" :actor-role :subscriber :tenant "tenant-basic"})]
    (is (= :commit (get-in res [:state :disposition])))))

(deftest phase1-forces-approval-on-clean-receive
  (let [[_ res] (run 1 clean-receive operator)]
    (is (= :interrupted (:status res)))
    (is (= :phase-approval (-> res :state :audit last :reason)))))

(deftest phase2-enables-treatment-under-approval
  (let [[_ res] (run 2 clean-treatment operator)]
    (is (= :interrupted (:status res)))
    (is (= :phase-approval (-> res :state :audit last :reason)))))

(deftest phase3-auto-commits-clean-receive
  (let [[s res] (run 3 clean-receive operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= :received (:status (store/shipment s "man-300"))))))

(deftest governor-hold-beats-phase
  (testing "a hard governor violation (broken chain) holds even in the most permissive phase"
    (let [[_ res] (run 3 (assoc clean-receive :broken-chain? true :subject "man-301" :manifest-id "man-301") operator)]
      (is (= :hold (get-in res [:state :disposition]))))))

(deftest correction-request-never-auto-commits-at-any-phase
  (doseq [ph [0 1 2 3]]
    (let [[_ res] (run ph correction-req officer)]
      (is (not= :commit (get-in res [:state :disposition])) (str "phase " ph " must not auto-commit a correction")))))
