(ns hazwaste.policy-contract-test
  "The governor contract as executable tests. Single invariant under test:
  HazWasteTreatment-LLM never receives/executes/discloses/resolves a record
  the HazWasteGovernor would reject, and every decision leaves exactly one
  ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [hazwaste.store :as store]
            [hazwaste.operation :as op]))

(defn- fresh [] (let [db (store/seed-db)] [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :treatment-operator :phase 3})
(def officer  {:actor-id "co-1" :actor-role :compliance-officer :phase 3})

(defn- exec-op [actor tid request context] (g/run* actor {:request request :context context} {:thread-id tid}))

(deftest authorized-receive-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :manifest/receive :subject "man-300" :manifest-id "man-300" :generator-id "g"
                   :transporter-id "t" :facility-id "fac-1" :waste-code "D001" :quantity-kg 300
                   :cross-border? false
                   :chain [{:step :generator :signed-by "g" :at "d"} {:step :transporter :signed-by "t" :at "d"}
                           {:step :facility :signed-by "fac-1" :at "d"}]
                   :source {:class :rcra-manifest-system :ref "demo"}}
                  operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= :received (:status (store/shipment db "man-300"))))
    (is (= 1 (count (store/ledger db))))))

(deftest unauthorized-role-is-held
  (let [[db actor] (fresh)
        res (exec-op actor "t2"
                  {:op :manifest/receive :subject "man-300" :manifest-id "man-300" :generator-id "g"
                   :transporter-id "t" :facility-id "fac-1" :waste-code "D001" :quantity-kg 300
                   :cross-border? false
                   :chain [{:step :generator :signed-by "g" :at "d"} {:step :transporter :signed-by "t" :at "d"}
                           {:step :facility :signed-by "fac-1" :at "d"}]
                   :source {:class :rcra-manifest-system :ref "demo"}}
                  {:actor-id "sub-1" :actor-role :subscriber})]
    (is (= :hold (get-in res [:state :disposition])))
    (is (nil? (store/shipment db "man-300")))
    (is (= [:rbac] (-> (store/ledger db) first :basis)))))

(deftest broken-chain-is-held
  (let [[db actor] (fresh)
        res (exec-op actor "t3"
                  {:op :manifest/receive :subject "man-301" :manifest-id "man-301" :generator-id "g"
                   :transporter-id "t" :facility-id "fac-1" :waste-code "D001" :quantity-kg 100
                   :cross-border? false
                   :chain [{:step :generator :signed-by "g" :at "d"} {:step :transporter :signed-by "t" :at "d"}]
                   :source {:class :rcra-manifest-system :ref "demo"} :broken-chain? true}
                  operator)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:manifest-chain-of-custody-gate} (-> (store/ledger db) first :basis)))
    (is (nil? (store/shipment db "man-301")))))

(deftest unauthorized-treatment-method-is-held
  (testing "man-100 is D001, perm-1 authorizes D001 only for incineration/stabilization, not landfill"
    (let [[db actor] (fresh)
          res (exec-op actor "t4"
                    {:op :treatment/execute :subject "man-100" :manifest-id "man-100" :method :landfill
                     :completed-at "d" :residue-disposition :none :source {:class :rcra-manifest-system :ref "demo"}}
                    operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:treatment-method-authorization-gate} (-> (store/ledger db) first :basis)))
      (is (nil? (store/treatment-record db "man-100"))))))

(deftest lapsed-permit-treatment-is-held
  (testing "man-200 is D008, only perm-2 covers D008 (landfill) and it's inactive"
    (let [[db actor] (fresh)
          res (exec-op actor "t5"
                    {:op :treatment/execute :subject "man-200" :manifest-id "man-200" :method :landfill
                     :completed-at "d" :residue-disposition :none
                     :source {:class :basel-convention-movement-document :ref "demo"}}
                    operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:treatment-method-authorization-gate} (-> (store/ledger db) first :basis))))))

(deftest over-disclosure-beyond-tier-is-held
  (let [[db actor] (fresh)
        res (exec-op actor "t6"
                  {:op :disclosure/query :subject "man-100" :manifest-id "man-100" :greedy? true}
                  {:actor-id "sub-1" :actor-role :subscriber :tenant "tenant-basic"})]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:licensed-disclosure} (-> (store/ledger db) first :basis)))))

(deftest cross-border-receive-escalates-then-human-decides
  (let [[db actor] (fresh)
        r1 (exec-op actor "t7"
                 {:op :manifest/receive :subject "man-302" :manifest-id "man-302" :generator-id "g"
                  :transporter-id "t" :facility-id "fac-1" :waste-code "D001" :quantity-kg 400
                  :cross-border? true
                  :chain [{:step :generator :signed-by "g" :at "d"} {:step :transporter :signed-by "t" :at "d"}
                          {:step :facility :signed-by "fac-1" :at "d"}]
                  :source {:class :basel-convention-movement-document :ref "demo"}}
                 operator)]
    (is (= :interrupted (:status r1)))
    (is (= :cross-border (-> r1 :state :audit last :reason)))
    (let [r2 (g/run* actor {:approval {:status :approved :by "compliance-1"}} {:thread-id "t7" :resume? true})]
      (is (= :commit (get-in r2 [:state :disposition])))
      (is (= :received (:status (store/shipment db "man-302")))))))

(deftest correction-request-always-escalates-regardless-of-confidence
  (let [[db actor] (fresh)
        r1 (exec-op actor "t8"
                 {:op :correction/request :subject "man-100" :disputed-field :quantity-kg :claim 480}
                 officer)]
    (is (= :interrupted (:status r1)))
    (is (= :dispute-request (-> r1 :state :audit last :reason)))
    (let [r2 (g/run* actor {:approval {:status :approved :by "compliance-1"}} {:thread-id "t8" :resume? true})]
      (is (= :commit (get-in r2 [:state :disposition])))
      (is (= 480 (:quantity-kg (store/shipment db "man-100")))))))

(deftest every-decision-leaves-one-ledger-fact
  (let [[db actor] (fresh)]
    (exec-op actor "a" {:op :manifest/receive :subject "man-300" :manifest-id "man-300" :generator-id "g"
                        :transporter-id "t" :facility-id "fac-1" :waste-code "D001" :quantity-kg 100
                        :cross-border? false
                        :chain [{:step :generator :signed-by "g" :at "d"} {:step :transporter :signed-by "t" :at "d"}
                                {:step :facility :signed-by "fac-1" :at "d"}]
                        :source {:class :rcra-manifest-system :ref "demo"}}
             operator)
    (exec-op actor "b" {:op :manifest/receive :subject "man-301" :manifest-id "man-301" :generator-id "g"
                        :transporter-id "t" :facility-id "fac-1" :waste-code "D001" :quantity-kg 100
                        :cross-border? false :chain [] :source nil :broken-chain? true}
             operator)
    (is (= 2 (count (store/ledger db))))))
