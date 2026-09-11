(ns hazwaste.report
  "Disclosure rendering as a GOVERNED read — column set is whatever the
  HazWasteGovernor's licensed-disclosure gate approved for the caller's
  contract tier."
  (:require [hazwaste.store :as store]))

(defn render-shipment [db manifest-id columns]
  (let [ship (store/shipment db manifest-id)
        treat (store/treatment-record db manifest-id)
        cell (fn [col]
               (case col
                 :manifest-id manifest-id
                 :facility-id (:facility-id ship)
                 :waste-code (:waste-code ship)
                 :status (:status ship)
                 :as-of (:at (last (:chain ship)))
                 :chain (:chain ship)
                 :treatment-record treat
                 :raw-source (:source ship)
                 nil))]
    (into {} (map (juxt identity cell)) columns)))
