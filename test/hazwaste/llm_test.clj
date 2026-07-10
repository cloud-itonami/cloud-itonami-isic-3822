(ns hazwaste.llm-test
  (:require [clojure.test :refer [deftest is testing]]
            [hazwaste.store :as store]
            [hazwaste.llm :as llm]))

(deftest receive-proposal-carries-source-and-cites
  (let [db (store/seed-db)
        p (llm/infer db {:op :manifest/receive :subject "man-300" :manifest-id "man-300"
                         :generator-id "g" :transporter-id "t" :facility-id "fac-1" :waste-code "D001"
                         :quantity-kg 100 :cross-border? false
                         :chain [{:step :generator :signed-by "g" :at "d"} {:step :transporter :signed-by "t" :at "d"}
                                 {:step :facility :signed-by "fac-1" :at "d"}]
                         :source {:class :rcra-manifest-system :ref "demo"}})]
    (is (= :manifest-upsert (:effect p)))
    (is (>= (:confidence p) 0.9))))

(deftest broken-chain-proposal-drops-facility-step
  (testing "the LLM layer does not filter -- that is the governor's job"
    (let [db (store/seed-db)
          p (llm/infer db {:op :manifest/receive :subject "man-301" :manifest-id "man-301"
                           :generator-id "g" :transporter-id "t" :facility-id "fac-1" :waste-code "D001"
                           :quantity-kg 100 :cross-border? false
                           :chain [{:step :generator :signed-by "g" :at "d"} {:step :transporter :signed-by "t" :at "d"}
                                   {:step :facility :signed-by "fac-1" :at "d"}]
                           :source {:class :rcra-manifest-system :ref "demo"}
                           :broken-chain? true})]
      (is (not (some #(= :facility (:step %)) (get-in p [:value :chain]))))
      (is (>= (:confidence p) 0.85)))))

(deftest treatment-proposal-carries-method
  (let [db (store/seed-db)
        p (llm/infer db {:op :treatment/execute :subject "man-100" :manifest-id "man-100" :method :incineration
                         :completed-at "d" :residue-disposition :none :source {:class :rcra-manifest-system :ref "demo"}})]
    (is (= :treatment-upsert (:effect p)))
    (is (= :incineration (get-in p [:value :method])))))

(deftest disclosure-proposal-greedy-adds-extra-columns
  (let [db (store/seed-db)
        clean (llm/infer db {:op :disclosure/query :subject "man-100" :manifest-id "man-100"})
        greedy (llm/infer db {:op :disclosure/query :subject "man-100" :manifest-id "man-100" :greedy? true})]
    (is (< (count (:columns clean)) (count (:columns greedy))))))

(deftest correction-proposal-never-marks-high-confidence
  (let [db (store/seed-db)
        p (llm/infer db {:op :correction/request :subject "man-100" :disputed-field :quantity-kg :claim 480})]
    (is (= :correction-apply (:effect p)))
    (is (< (:confidence p) 0.9))))
