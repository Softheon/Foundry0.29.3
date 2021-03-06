(ns metabase.db.metadata-queries
  "Predefined MBQL queries for getting metadata about an external database."
  (:require [clojure.tools.logging :as log]
            [metabase
             [query-processor :as qp]
             [util :as u]]
            [metabase.models.table :refer [Table]]
            [metabase.query-processor.interface :as qpi]
            [metabase.query-processor.middleware.expand :as ql]
            [metabase.util.schema :as su]
            [schema.core :as s]
            [metabase.mssqltoucan.db :as db]))

(defn- qp-query [db-id query]
  {:pre [(integer? db-id)]}
  (-> (binding [qpi/*disable-qp-logging* true]
        (qp/process-query
          {:type     :query
           :database db-id
           :query    query}))
      :data
      :rows))

(defn- field-query [{table-id :table_id} query]
  {:pre [(integer? table-id)]}
  (qp-query (db/select-one-field :db_id Table, :id table-id)
            ;; this seeming useless `merge` statement IS in fact doing something important. `ql/query` is a threading
            ;; macro for building queries. Do not remove
            (ql/query (merge query)
                      (ql/source-table table-id))))

(defn table-row-count
  "Fetch the row count of TABLE via the query processor."
  [table]
  {:pre  [(map? table)]
   :post [(integer? %)]}
  (let [results (qp-query (:db_id table) (ql/query (ql/source-table (u/get-id table))
                                                   (ql/aggregation (ql/count))))]
    (try (-> results first first long)
         (catch Throwable e
           (log/error "Error fetching table row count. Query returned:\n"
                      (u/pprint-to-str results))
           (throw e)))))

(def ^:private ^Integer absolute-max-distinct-values-limit
  "The absolute maximum number of results to return for a `field-distinct-values` query. Normally Fields with 100 or
  less values (at the time of this writing) get marked as `auto-list` Fields, meaning we save all their distinct
  values in a FieldValues object, which powers a list widget in the FE when using the Field for filtering in the QB.
  Admins can however manually mark any Field as `list`, which is effectively ordering Metabase to keep FieldValues for
  the Field regardless of its cardinality.

  Of course, if a User does something crazy, like mark a million-arity Field as List, we don't want Metabase to
  explode trying to make their dreams a reality; we need some sort of hard limit to prevent catastrophes. So this
  limit is effectively a safety to prevent Users from nuking their own instance for Fields that really shouldn't be
  List Fields at all. For these very-high-cardinality Fields, we're effectively capping the number of
  FieldValues that get could saved.

  This number should be a balance of:

  * Not being too low, which would definitly result in GitHub issues along the lines of 'My 500-distinct-value Field
    that I marked as List is not showing all values in the List Widget'
  * Not being too high, which would result in Metabase running out of memory dealing with too many values"
  (int 5000))

(s/defn field-distinct-values
  "Return the distinct values of FIELD.
   This is used to create a `FieldValues` object for `:type/Category` Fields."
  ([field]
   (field-distinct-values field absolute-max-distinct-values-limit))
  ([field, max-results :- su/IntGreaterThanZero]
   (mapv first (field-query field (-> {}
                                      (ql/breakout (ql/field-id (u/get-id field)))
                                      (ql/limit max-results))))))

(defn field-distinct-count
  "Return the distinct count of FIELD."
  [field & [limit]]
  (-> (field-query field (-> {}
                             (ql/aggregation (ql/distinct (ql/field-id (u/get-id field))))
                             (ql/limit limit)))
      first first int))

(defn field-count
  "Return the count of FIELD."
  [field]
  (-> (field-query field (ql/aggregation {} (ql/count (ql/field-id (u/get-id field)))))
      first first int))

(defn db-id
  "Return the database ID of a given entity."
  [x]
  (or (:db_id x)
      (:database_id x)
      (db/select-one-field :db_id 'Table :id (:table_id x))))
