(ns metabase.models.pulse-revision
  (:require [metabase.util :as u]
            [puppetlabs.i18n.core :refer [tru]]
            [metabase.mssqltoucan
              [db :as db]
              [models :as models]]))

(models/defmodel PulseRevision :pulse_revision)

(defn- pre-insert [revision]
  (assoc revision :created_at (u/new-sql-timestamp)))

(u/strict-extend (class PulseRevision)
  models/IModel
  (merge models/IModelDefaults
    {:types           (constantly {:before  :json
                                   :after   :json
                                   :remark  :clob})
     :pre-insert pre-insert
     :pre-update (fn [& _] (throw (Exception. (str (tru "You cannot update a PulseRevisions!")))))}))

(defn latest-id
  "return the ID of the newest `PulseRevision`, or zero if non have been made yet.
  (this is used by the pulse graph update logic that checks for changes since the original graph was fetched)."
  []
  (or (db/select-one-id PulseRevision {:order-by [[:id :desc]]})
    0))