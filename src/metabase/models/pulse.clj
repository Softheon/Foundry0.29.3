(ns metabase.models.pulse
  (:require [clojure.set :as set]
            [clojure.tools.logging :as log]
            [medley.core :as m]
            [metabase
             [db :as mdb]
             [events :as events]
             [util :as u]]
            [metabase.api.common :refer [*current-user*]]
            [metabase.api.common :refer [*current-user-id*]]
            [metabase.models
             [card :refer [Card]]
             [interface :as i]
             [pulse-card :refer [PulseCard]]
             [pulse-channel :as pulse-channel :refer [PulseChannel]]
             [pulse-channel-recipient :refer [PulseChannelRecipient]]
             [pulse-revision :as pulse-revision :refer [PulseRevision]]
             [permissions :as perms]]
            [metabase.mssqltoucan
             [db :as db]
             [hydrate :refer [hydrate]]
             [models :as models]]
            [metabase.util :as u]
            [metabase.util.schema :as su]
            [schema.core :s]
            [clojure.java.jdbc :as jdbc]))

;;; ------------------------------------------------------------ Perms Checking ------------------------------------------------------------

(defn- perms-objects-set [pulse read-or-write]
  (set (when-let [card-ids (db/select-field :card_id PulseCard, :pulse_id (u/get-id pulse))]
         (apply set/union (for [card (db/select [Card :dataset_query], :id [:in card-ids])]
                            (i/perms-objects-set card read-or-write))))))

(defn- channels-with-recipients
  "Get the 'channels' associated with this PULSE, including recipients of those 'channels'.
   If `:channels` is already hydrated, as it will be when using `retrieve-pulses`, this doesn't need to make any DB calls."
  [pulse]
  (or (:channels pulse)
      (-> (db/select PulseChannel, :pulse_id (u/get-id pulse))
          (hydrate :recipients))))

(defn- emails
  "Get the set of emails this PULSE will be sent to."
  [pulse]
  (set (for [channel   (channels-with-recipients pulse)
             recipient (:recipients channel)]
         (:email recipient))))

(defn- can-read? [pulse]
  (or (i/current-user-has-full-permissions? :read pulse)
      (contains? (emails pulse) (:email @*current-user*))))


;;; ------------------------------------------------------------ Entity & Lifecycle ------------------------------------------------------------

(models/defmodel Pulse :pulse)

(defn- pre-delete [{:keys [id]}]
  (db/delete! PulseCard :pulse_id id)
  (db/delete! PulseChannel :pulse_id id))

(u/strict-extend (class Pulse)
  models/IModel
  (merge models/IModelDefaults
         {:hydration-keys (constantly [:pulse])
          :properties     (constantly {:timestamped? true})
          :pre-delete     pre-delete})
  i/IObjectPermissions
  (merge i/IObjectPermissionsDefaults
         {:perms-objects-set  perms-objects-set
          ;; I'm not 100% sure this covers everything. If a user is subscribed to a pulse they're still allowed to know it exists, right?
          :can-read?          can-read?
          :can-write?         (partial i/current-user-has-full-permissions? :write)}))


;;; ------------------------------------------------------------ Hydration ------------------------------------------------------------

(defn ^:hydrate channels
  "Return the `PulseChannels` associated with this PULSE."
  [{:keys [id]}]
  (db/select PulseChannel, :pulse_id id))


(defn ^:hydrate cards
  "Return the `Cards` associated with this PULSE."
  [{:keys [id]}]
  (map #(models/do-post-select Card %)
       (db/query
        {:select    [:c.id :c.name :c.description :c.display :pc.include_csv :pc.include_xls]
         :from      [[Pulse :p]]
         :join      [[PulseCard :pc] [:= :p.id :pc.pulse_id]
                     [Card :c] [:= :c.id :pc.card_id]]
         :where     [:and
                     [:= :p.id id]
                     [:= :c.archived false]]
         :order-by [[:pc.position :asc]]})))

;;; ------------------------------------------------------------ Pulse Fetching Helper Fns ------------------------------------------------------------

(defn- hydrate-pulse [pulse]
  (-> pulse
      (hydrate :creator :cards [:channels :recipients])
      (m/dissoc-in [:details :emails])))

(defn- remove-alert-fields [pulse]
  (dissoc pulse :alert_condition :alert_above_goal :alert_first_only))

(defn retrieve-pulse
  "Fetch a single `Pulse` by its ID value."
  [id]
  {:pre [(integer? id)]}
  (-> (db/select-one Pulse {:where [:and
                                    [:= :id id]
                                    [:= :alert_condition nil]]})
      hydrate-pulse
      remove-alert-fields))

(defn retrieve-pulse-or-alert
  "Fetch an alert or pulse by its ID value."
  [id]
  {:pre [(integer? id)]}
  (-> (db/select-one Pulse {:where [:= :id id]})
      hydrate-pulse))

(defn- pulse->alert
  "Convert a pulse to an alert"
  [pulse]
  (-> pulse
      (assoc :card (first (:cards pulse)))
      (dissoc :cards)))

(defn retrieve-alert
  "Fetch a single alert by its pulse `ID` value."
  [id]
  {:pre [(integer? id)]}
  (-> (db/select-one Pulse {:where [:and
                                    [:= :id id]
                                    [:not= :alert_condition nil]]})
      hydrate-pulse
      pulse->alert))

(defn retrieve-alerts
  "Fetch all alerts"
  []
  (for [pulse (db/select Pulse, {:where [:not= :alert_condition nil]
                                 :order-by [[:name :asc]]})]

    (-> pulse
        hydrate-pulse
        pulse->alert)))

(defn retrieve-pulses
  "Fetch all `Pulses`."
  []
  (for [pulse (db/select Pulse, {:where [:= :alert_condition nil]
                                 :order-by [[:name :asc]]} )]
    (-> pulse
        hydrate-pulse
        remove-alert-fields)))

(defn- query-as [model query]
  (db/do-post-select model (db/query query)))

(defn retrieve-user-alerts-for-card
  "Find all alerts for `CARD-ID` that `USER-ID` is set to receive"
  [card-id user-id]
  (map (comp pulse->alert hydrate-pulse)
       (query-as Pulse
                 {:select [:p.*]
                  :from   [[Pulse :p]]
                  :join   [[PulseCard :pc] [:= :p.id :pc.pulse_id]
                           [PulseChannel :pchan] [:= :pchan.pulse_id :p.id]
                           [PulseChannelRecipient :pcr] [:= :pchan.id :pcr.pulse_channel_id]]
                  :where  [:and
                           [:not= :p.alert_condition nil]
                           [:= :pc.card_id card-id]
                           [:= :pcr.user_id user-id]]})))

(defn retrieve-alerts-for-card
  "Find all alerts for `CARD-IDS`, used for admin users"
  [& card-ids]
  (when (seq card-ids)
    (map (comp pulse->alert hydrate-pulse)
         (query-as Pulse
                   {:select [:p.*]
                    :from   [[Pulse :p]]
                    :join   [[PulseCard :pc] [:= :p.id :pc.pulse_id]]
                    :where  [:and
                             [:not= :p.alert_condition nil]
                             [:in :pc.card_id card-ids]]}))))

(defn create-card-ref
  "Create a card reference from a card or id"
  [card]
  {:id          (u/get-id card)
   :include_csv (get card :include_csv false)
   :include_xls (get card :include_xls false)})

;;; ------------------------------------------------------------ Other Persistence Functions ------------------------------------------------------------

(defn update-pulse-cards!
  "Update the `PulseCards` for a given PULSE.
   CARD-IDS should be a definitive collection of *all* IDs of cards for the pulse in the desired order.

   *  If an ID in CARD-IDS has no corresponding existing `PulseCard` object, one will be created.
   *  If an existing `PulseCard` has no corresponding ID in CARD-IDs, it will be deleted.
   *  All cards will be updated with a `position` according to their place in the collection of CARD-IDS"
  {:arglists '([pulse card-refs])}
  [{:keys [id]} card-refs]
  {:pre [(integer? id)
         (sequential? card-refs)
         (every? map? card-refs)]}
  ;; first off, just delete any cards associated with this pulse (we add them again below)
  (db/delete! PulseCard :pulse_id id)
  ;; now just insert all of the cards that were given to us
  (when (seq card-refs)
    (let [cards (map-indexed (fn [i {card-id :id :keys [include_csv include_xls]}]
                               {:pulse_id    id, :card_id     card-id,
                                :position    i   :include_csv include_csv,
                                :include_xls include_xls})
                             card-refs)]
      (db/insert-many! PulseCard cards))))


(defn- create-update-delete-channel!
  "Utility function which determines how to properly update a single pulse channel."
  [pulse-id new-channel existing-channel]
  ;; NOTE that we force the :id of the channel being updated to the :id we *know* from our
  ;;      existing list of `PulseChannels` pulled from the db to ensure we affect the right record
  (let [channel (when new-channel (assoc new-channel
                                    :pulse_id       pulse-id
                                    :id             (:id existing-channel)
                                    :channel_type   (keyword (:channel_type new-channel))
                                    :schedule_type  (keyword (:schedule_type new-channel))
                                    :schedule_frame (keyword (:schedule_frame new-channel))))]
    (cond
      ;; 1. in channels, NOT in db-channels = CREATE
      (and channel (not existing-channel))  (pulse-channel/create-pulse-channel! channel)
      ;; 2. NOT in channels, in db-channels = DELETE
      (and (nil? channel) existing-channel) (db/delete! PulseChannel :id (:id existing-channel))
      ;; 3. in channels, in db-channels = UPDATE
      (and channel existing-channel)        (pulse-channel/update-pulse-channel! channel)
      ;; 4. NOT in channels, NOT in db-channels = NO-OP
      :else nil)))

(defn update-pulse-channels!
  "Update the `PulseChannels` for a given PULSE.
   CHANNELS should be a definitive collection of *all* of the channels for the the pulse.

   * If a channel in the list has no existing `PulseChannel` object, one will be created.
   * If an existing `PulseChannel` has no corresponding entry in CHANNELS, it will be deleted.
   * All previously existing channels will be updated with their most recent information."
  {:arglists '([pulse channels])}
  [{:keys [id]} channels]
  {:pre [(integer? id)
         (coll? channels)
         (every? map? channels)]}
  (let [new-channels   (group-by (comp keyword :channel_type) channels)
        old-channels   (group-by (comp keyword :channel_type) (db/select PulseChannel :pulse_id id))
        handle-channel #(create-update-delete-channel! id (first (get new-channels %)) (first (get old-channels %)))]
    (assert (zero? (count (get new-channels nil)))
      "Cannot have channels without a :channel_type attribute")
    ;; for each of our possible channel types call our handler function
    (doseq [[channel-type] pulse-channel/channel-types]
      (handle-channel channel-type))))

(defn- create-notification [pulse card-ids channels]
  (db/transaction
    (let [{:keys [id] :as pulse} (db/insert! Pulse pulse)]
      ;; add card-ids to the Pulse
      (update-pulse-cards! pulse card-ids)
      ;; add channels to the Pulse
      (update-pulse-channels! pulse channels)
      id)))


(defn create-pulse!
  "Create a new `Pulse` by inserting it into the database along with all associated pieces of data such as:
  `PulseCards`, `PulseChannels`, and `PulseChannelRecipients`.

   Returns the newly created `Pulse` or throws an Exception."
  [pulse-name creator-id card-ids channels skip-if-empty?]
  {:pre [(string? pulse-name)
         (integer? creator-id)
         (sequential? card-ids)
         (seq card-ids)
         (every? map? card-ids)
         (coll? channels)
         (every? map? channels)]}
  (let [id (create-notification {:creator_id    creator-id
                                 :name          pulse-name
                                 :skip_if_empty skip-if-empty?}
                                card-ids channels)]
    ;; return the full Pulse (and record our create event)
    (events/publish-event! :pulse-create (retrieve-pulse id))))

(defn create-alert!
  "Creates a pulse with the correct fields specified for an alert"
  [alert creator-id card-id channels]
  (let [id (-> alert
               (assoc :skip_if_empty true :creator_id creator-id)
               (create-notification [card-id] channels))]
    ;; return the full Pulse (and record our create event)
    (events/publish-event! :alert-create (retrieve-alert id))))

(defn update-notification!
  "Updates the pulse/alert and updates the related channels"
  [{:keys [id name cards channels skip-if-empty?] :as pulse}]
  (db/transaction
    ;; update the pulse itself
    (db/update-non-nil-keys! Pulse id (-> pulse
                                          (select-keys [:name :alert_condition :alert_above_goal :alert_first_only])
                                          (assoc :skip_if_empty skip-if-empty?)))
    ;; update cards (only if they changed). Order for the cards is important which is why we're not using select-field
    (when (not= cards (map :card_id (db/select [PulseCard :card_id], :pulse_id id, {:order-by [[:position :asc]]})))
      (update-pulse-cards! pulse cards))
    ;; update channels
    (update-pulse-channels! pulse channels)))

(defn update-pulse!
  "Update an existing `Pulse`, including all associated data such as: `PulseCards`, `PulseChannels`, and `PulseChannelRecipients`.

   Returns the updated `Pulse` or throws an Exception."
  [{:keys [id name cards channels skip-if-empty?] :as pulse}]
  {:pre [(integer? id)
         (string? name)
         (sequential? cards)
         (> (count cards) 0)
         (every? map? cards)
         (coll? channels)
         (every? map? channels)]}
  (update-notification! pulse)
  ;; fetch the fully updated pulse and return it (and fire off an event)
  (->> (retrieve-pulse id)
       (events/publish-event! :pulse-update)))

(defn update-alert!
  "Updates the given `ALERT` and returns it"
  [{:keys [id card] :as alert}]
  (-> alert
      (assoc :skip-if-empty? true :cards [card])
      (dissoc :card)
      update-notification!)
  ;; fetch the fully updated pulse and return it (and fire off an event)
  (->> (retrieve-alert id)
       (events/publish-event! :pulse-update)))

(defn unsubscribe-from-alert
  "Removes `USER-ID` from `PULSE-ID`"
  [pulse-id user-id]
  (let [[result] (db/execute! {:delete-from PulseChannelRecipient
                               ;; The below select * clause is required for the query to work on MySQL (PG and H2 work
                               ;; without it). MySQL will fail if the delete has an implicit join. By wrapping the
                               ;; query in a select *, it forces that query to use a temp table rather than trying to
                               ;; make the join directly, which works in MySQL, PG and H2
                               :where [:= :id {:select [:*]
                                               :from [[{:select [:pcr.id]
                                                         :from [[PulseChannelRecipient :pcr]]
                                                         :join [[PulseChannel :pchan] [:= :pchan.id :pcr.pulse_channel_id]
                                                                [Pulse :p] [:= :p.id :pchan.pulse_id]]
                                                         :where [:and
                                                                 [:= :p.id pulse-id]
                                                                 [:not= :p.alert_condition nil]
                                                                 [:= :pcr.user_id user-id]]} "r"]]}]})]
    (when (zero? result)
      (log/warnf "Failed to remove user-id '%s' from pulse-id '%s'" user-id pulse-id))

    result))

;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                  PERMISSIONS GRAPH                                             |
;;; +----------------------------------------------------------------------------------------------------------------+

;;; ------------------------------------------------------ Schmeas ---------------------------------------------------

(def ^:private PulsePermisssons
  (s/enum :write :none)

(def ^:private GroupPermissionsGraph
  "pulse -> status"
  {su/NonBlankString PulsePermisssons})

(defn ^:private PermissionsGraph
  {:revision s/int
   :groups {su/IntGreaterThanZero GroupPermissionsGraph}})

;;;------------------------------------------------------- Fetch Graph ------------------------------------------------

(def ^String PULSE "pulse")

(defn- group-id->permissions-set
  (into {} (for [[group-id perms] (group-by :group_id (db/select `Permissions))]
            {group-id (set (map :object perms))})))

(s/defn ^:private perms-type-for-pulse :- PulsePermisssons
  [permissions-set]
  (cond 
    (perms/set-has-full-permissions? permissions-set (perms/pulse-readwrite-path)   :write)
    :else                                                                           :none))

(s/defn ^:private group-permissions-graph :- GroupPermissionsGraph
  "Return the permissions group for a single group havng PERMISSONS-SET"
  [permissions-set]
  {PULSE  (perms-type-for-pulse permissions-set)})

(s/defn graph :- PermissionsGraph
  "Fetch a graph representing the current permissions status for every group and all permissioned pulses. This
   works just like  the function of the same in `metabase.models.permissions`; see the documentation for that function."
  []
  (let [group-id->perms (group-id->permissions-set)]
    {:revision (pulse-revision/latest-id)
     :groups (into {} (for [group-id (db/select-ids 'PermissionsGroup)]
                        {group-id (group-permissions-graph (group-id->perms group-id))}))}))
;;;------------------------------------------------------- Update Graph -----------------------------------------------

(s/defn ^:private update-pulse-permissions!
  [group-id :- su/IntGreaterThanZero,  new-pulse-perms :- PulsePermisssons]
  (perms/revoke-pulse-permissions! group-id)
  (case new-collectioin-perms
      :write  (perms/grant-pulse-readwrite-permissons! group-id)
      :none nil))

(s/defn ^:private update-group-permissions!
  [group-id :-su/IntGreaterThanZero, new-group-perms :- GroupPermissionsGraph]
  (doseq [[pulse-identifier new-perms] new-group-perms]
    (update-pulse-permissions! group-id new perms)))

(defn- save-perms-revision!
  "Save changes made to the pulse permissions for logging/auditing purpose.
   This doesn't do anything if  `*current-user-id*` is unset (e.g. for testing or REPL usage)."
   [current-revision old new]
   (when *current-user-id*
    ;; manually specify ID here so if one was somehow inserted in the meantime in the fraction of a second since we
    ;; called 'check-revision-numbers' the PK constraints will fail and the transaction will abort
    (db/set-identity-insert PulseRevision true)
    (db/insert! PulseRevision
      :id (in current-revision)
      :before old
      :after new
      :user_id *current-user-id*)
    (db/set-identity-insert PulseRevision false)))

(s/def update-graph!
  "Update the pulse permissions graph. This works just like the function of the same name 
   in `metabase.models.permissons`, but for `Pulse`; refer to that function's extensive documentation to get a
   sense for how this works"
  ([new-graph :- PermissionsGraph]
    (let [old-graph (graph)
          [old new] (data/diff (:groups old-graph) (:grouips new-graph))]
      (perms/log-permissions-changes old new)
      (perms/check-revision-numbers old-graph new-graph)
      (when (seq new)
        (db/transaction
          (doseq [[group-id changes] new]
            (update-group-permissions! groud-id changes))
          (save-perms-revisions! (:revision old-graph) old new)))))

  ;; The following arity is provided soley for conveience for test/REPL usage
  ([ks new-value]
    {:pre [(sequential? ks)]}
    (update-graph! (assoc-in (graph) (cons :groups ks) new-value))))