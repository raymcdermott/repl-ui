(ns repl.repl.events
  (:require
    [cljs.tools.reader.edn :as rdr]
    [clojure.core.async]
    [clojure.string :as string]
    [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx reg-fx]]
    [repl.repl.code-mirror :as code-mirror]
    [repl.repl.helpers :refer [js->cljs]]
    [repl.repl.ws :as ws]
    [repl.repl.messages :as message-specs]
    [repl.repl.user :as user-specs]
    [taoensso.sente :as sente]))

(def default-server-timeout 3000)

; --- Events ---
(reg-event-db
  ::initialize-db
  (fn [_ _]
    {::name             "repl"
     ::other-visibility true}))

(reg-event-db
  ::network-status
  (fn [db [_ status]]
    (assoc db :network-status status)))

(reg-event-db
  ::client-uid
  (fn [db [_ uid]]
    (if-let [{::user-specs/keys [name]} (::user-specs/user db)]
      (assoc db ::user-specs/user (user-specs/->user name uid))
      (assoc db ::user-specs/uid uid))))

(defn pred-fails
  [problems]
  (some->> problems
           (map #(str "🤔  " (:val %) " is not " (:pred %)))
           (interpose "\n")
           (apply str)))

(defn default-reptile-tag-reader
  [tag val]
  {:nk-tag tag :nk-val (rdr/read-string (str val))})

;; TODO deal with ex-data / Throwable->map
(defn read-exception
  [val]
  (try
    (let [reader-opts {:default default-reptile-tag-reader}]
      (rdr/read-string reader-opts val))
    (catch :default _ignore-reader-errors)))

(def bugs "...\n")

;; TODO integrate a nice spec formatting library
(defn check-exception
  [val]
  (let [{:keys [cause via trace data phase]} (read-exception val)
        problems (:clojure.spec.alpha/problems data)
        spec     (:clojure.spec.alpha/spec data)
        value    (:clojure.spec.alpha/value data)
        args     (:clojure.spec.alpha/args data)]
    (or problems cause)))

(defn format-response
  [show-times? result]
  (let [{:keys [val form tag ms]} result
        exception-data (check-exception val)]
    (cond
      exception-data
      (str form "\n"
           "=> " exception-data "\n\n")

      (= tag :err)
      (str form "\n" val "\n\n")

      (= tag :out)
      (str val)

      ;; TODO show-user?
      (= tag :ret)
      (str form "\n" (when show-times? (str ms " ms "))
           "=> " (or val "nil") "\n\n"))))

(defn format-responses
  [show-times? {:keys [prepl-response]}]
  (doall (apply str (map (partial format-response show-times?)
                         prepl-response))))

(defn format-results
  [show-times? results]
  (doall (map (partial format-responses show-times?) results)))

(reg-event-fx
  ::clear-evals
  (fn [{:keys [db]} [_ _]]
    (when-let [code-mirror (:eval-code-mirror db)]
      {:db                        (assoc db :eval-results [])
       ::code-mirror/set-cm-value {:value       ""
                                   :code-mirror code-mirror}})))
(reg-event-fx
  ::eval-result
  (fn [{:keys [db]} [_ eval-result]]
    (println ::eval-result eval-result)
    (let [code-mirror  (:eval-code-mirror db)
          show-times?  (true? (:show-times db))
          eval-results (conj (:eval-results db) eval-result)
          str-results  (apply str (reverse
                                    (format-results show-times? eval-results)))]
      {:db                        (assoc db :eval-results eval-results)
       ::code-mirror/set-cm-value {:value       str-results
                                   :code-mirror code-mirror}})))

(reg-event-fx
  ::show-times
  (fn [{:keys [db]} [_ show-times]]
    (let [code-mirror  (:eval-code-mirror db)
          show-times?  (true? show-times)
          eval-results (:eval-results db)
          str-results  (apply str (reverse (format-results show-times? eval-results)))]
      {:db                        (assoc db :show-times show-times)
       ::code-mirror/set-cm-value {:value       str-results
                                   :code-mirror code-mirror}})))

(reg-event-db
  ::eval-code-mirror
  (fn [db [_ code-mirror]]
    (assoc db :eval-code-mirror code-mirror)))

(reg-fx
  ::>repl-eval
  (fn [[source user form]]
    (when-not (string/blank? form)
      (ws/chsk-send!
        [:repl-repl/eval {:form      form
                          :team-name "team-name"
                          :source    source
                          :user      user
                          :forms     form}]
        (or (:timeout form) default-server-timeout)))))

(reg-event-fx
  ::eval
  (fn [{:keys [db]} [_ input]]
    (let [user         (::user-specs/user db)
          form-to-eval (if (string? input) input (:form input))]
      {:db          (assoc db :form-to-eval form-to-eval)
       ::>repl-eval [:user user form-to-eval]})))

(reg-fx
  ::>login
  (fn [{:keys [options timeout]}]
    (let [user (user-specs/->user (::user-specs/name options)
                                  (::user-specs/uid options))]
      (ws/chsk-send!
        [:repl-repl/login user]
        (or timeout default-server-timeout)
        (fn [reply]
          (if (and (sente/cb-success? reply)
                   (= reply :login-ok))
            (re-frame/dispatch [::logged-in-user user])
            (js/alert "Login failed")))))))

(reg-event-fx
  ::login
  (fn [{:keys [db]} [_ login-options]]
    (when-let [uid (::user-specs/uid db)]
      {:db      (assoc db
                  :proposed-user (::user-specs/name login-options)
                  ::user-specs/name nil)
       ::>login {:options
                 (assoc login-options ::user-specs/uid uid)}})))

(reg-fx
  ::>logout
  (fn [{:keys [options timeout]}]
    (ws/chsk-send! [:repl-repl/logout options]
                   (or timeout default-server-timeout))))

(reg-event-fx
  ::logout
  (fn [{:keys [db]} _]
    (when-let [user (::user-specs/user db)]
      {:db       (dissoc db ::user-specs/user)
       ::>logout {:options user}})))

(reg-event-db
  ::show-add-lib-panel
  (fn [db [_ show?]]
    (assoc db :show-add-lib-panel show?)))

(reg-event-fx
  ::add-lib
  (fn [cofx [_ {:keys [name version url sha maven] :as lib}]]
    (let [lib-spec (str "(add-lib '" (string/trim name) " {"
                        (if maven
                          (str ":mvn/version \""
                               (string/trim version) "\"")
                          (str ":git/url \""
                               (string/trim url) "\" :sha \""
                               (string/trim sha) "\""))
                        "})")]
      (re-frame/dispatch [::show-add-lib-panel false])
      {:db          (assoc (:db cofx) :proposed-lib lib)
       ::>repl-eval [:user "system" lib-spec]})))

;; ---------------------- Network sync

;; Share editing updates
(reg-fx
  ::>current-form
  (fn [[user user-count form]]
    (when (> user-count 1)
      (ws/chsk-send!
        [:repl-repl/keystrokes (message-specs/->keystrokes form user)]))))

(reg-event-fx
  ::current-form
  (fn [{:keys [db]} [_ current-form]]
    (when-not (string/blank? (string/trim current-form))
      (let [user       (::user-specs/user db)
            user-count (count (::user-specs/users db))]
        {:db             (assoc db :current-form current-form)
         ::>current-form [user user-count current-form]}))))

;; ------------------------------------------------------------------

(reg-event-db
  ::logged-in-user
  (fn [db [_ user]]
    (assoc db ::user-specs/user user)))

(reg-event-db
  ::toggle-others
  (fn [db _]
    (assoc db ::other-visibility (not (::other-visibility db)))))

(reg-event-db
  ::code-mirror
  (fn [db [_ code-mirror]]
    (assoc db :code-mirror code-mirror)))

(reg-event-db
  ::other-user-code-mirror
  (fn [db [_ code-mirror user]]
    (let [user-key   (first user)
          cm-entries (:other-user-code-mirrors db)
          cm-entry   (assoc {} user-key code-mirror)
          cm-update  (merge cm-entries cm-entry)]
      (assoc db :other-user-code-mirrors cm-update))))

(reg-event-db
  ::users
  (fn [db [_ users]]
    (assoc db ::user-specs/users users)))

;; TODO NEXT
(reg-event-fx
  ::other-user-keystrokes
  (fn [{:keys [db]} [_ {:keys [::user-specs/user
                               ::message-specs/form]}]]
    (when-not (= user (::user-specs/user db))
      (let [editor-key   (keyword (::user-specs/name user))
            code-mirrors (:other-user-code-mirrors db)
            code-mirror  (get code-mirrors editor-key)]
        {:db                        db
         ::code-mirror/set-cm-value {:code-mirror code-mirror
                                     :value       form}}))))


