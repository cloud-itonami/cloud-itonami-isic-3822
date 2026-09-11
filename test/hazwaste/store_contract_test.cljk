(ns hazwaste.store-contract-test
  (:require [clojure.test :refer [deftest is testing]]
            [hazwaste.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "D001" (:waste-code (store/shipment s "man-100"))))
      (is (= 3 (count (:chain (store/shipment s "man-100")))))
      (is (= {:class :rcra-manifest-system :ref "epa-manifest:man-100"}
             (:source (store/shipment s "man-100"))))
      (is (true? (:active? (store/facility-permit s "perm-1"))))
      (is (false? (:active? (store/facility-permit s "perm-2"))))
      (is (= 2 (count (store/permits-of s "fac-1"))))
      (is (= 2 (count (store/all-shipments s)))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "manifest upsert"
        (store/commit-record! s {:effect :manifest-upsert
                                 :value {:manifest-id "man-999" :generator-id "g" :transporter-id "t"
                                         :facility-id "fac-1" :waste-code "D001" :quantity-kg 10
                                         :cross-border? false :chain [] :status :received
                                         :source {:class :rcra-manifest-system :ref "demo"}}})
        (is (= "D001" (:waste-code (store/shipment s "man-999")))))
      (testing "treatment upsert"
        (store/commit-record! s {:effect :treatment-upsert
                                 :value {:manifest-id "man-100" :method :incineration :completed-at "2026-07-09"
                                         :residue-disposition :none :source {:class :rcra-manifest-system :ref "demo"}}})
        (is (= :incineration (:method (store/treatment-record s "man-100")))))
      (testing "correction-apply patches the target shipment"
        (store/commit-record! s {:effect :correction-apply :value {:patch {:quantity-kg 480}} :path ["man-100"]})
        (is (= 480 (:quantity-kg (store/shipment s "man-100")))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (take-last 2 (store/ledger s)))))))))

(deftest contract-lookup
  (doseq [[label s] (backends)]
    (testing label
      (is (= :tier/regulator (:tier (store/contract s "tenant-regulator"))))
      (is (nil? (store/contract s "tenant-ghost"))))))
