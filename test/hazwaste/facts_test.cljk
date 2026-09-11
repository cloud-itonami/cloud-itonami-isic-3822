(ns hazwaste.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [hazwaste.facts :as facts]))

(deftest catalog-entries-are-well-formed
  (doseq [{:keys [id name jurisdiction class access]} facts/catalog]
    (testing (str id)
      (is (keyword? id))
      (is (string? name))
      (is (keyword? jurisdiction))
      (is (keyword? class))
      (is (keyword? access)))))

(deftest allowed-source-classes-matches-catalog
  (is (= (into #{} (map :class facts/catalog)) facts/allowed-source-classes)))

(deftest class-allowed?-rejects-unlisted-classes
  (is (facts/class-allowed? :rcra-manifest-system))
  (is (facts/class-allowed? :eu-waste-shipment-regulation))
  (is (facts/class-allowed? :basel-convention-movement-document))
  (is (not (facts/class-allowed? :inference)))
  (is (not (facts/class-allowed? nil))))

(deftest coverage-is-honest-not-aspirational
  (let [c (facts/coverage)]
    (is (= (count facts/catalog) (:source-count c)))
    (is (<= (:source-count c) 20))
    (is (contains? (:jurisdictions c) :usa))))
