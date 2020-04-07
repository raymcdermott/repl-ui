(ns repl.repl.ziggy.events
  (:require
    [cljs.tools.reader.edn :as rdr]
    [clojure.core.async]
    [clojure.string :as string]
    [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx reg-fx]]
    [repl.repl.ziggy.code-mirror :as code-mirror]
    [repl.repl.ziggy.db :as db]
    [repl.repl.ziggy.helpers :refer [js->cljs]]
    [repl.repl.ziggy.ws :as ws]
    [repl.repl.user :as user]
    [taoensso.sente :as sente]))

(def default-server-timeout 3000)

; --- Events ---
(reg-event-db
  ::initialize-db
  (fn [_ _]
    db/default-db))

(reg-event-db
  ::network-status
  (fn [db [_ status]]
    (assoc db :network-status status)))

(reg-event-db
  ::client-uid
  (fn [db [_ uid]]
    (if-let [{::user/keys [name]} (::user/user db)]
      (do (println "Reconnecting existing user uid")
          (assoc db ::user/user (user/->user name uid)))
      (assoc db ::user/uid uid))))

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

(def bugs "🐛 🐞 🐜\n")

;; TODO integrate a nice spec formatting library
(defn check-exception
  [val]
  (let [{:keys [cause via trace data phase] :as exc} (read-exception val)
        problems   (:clojure.spec.alpha/problems data)
        spec       (:clojure.spec.alpha/spec data)
        value      (:clojure.spec.alpha/value data)
        args       (:clojure.spec.alpha/args data)
        spec-fails (and problems (pred-fails problems))]
    (when-let [problem (or spec-fails cause)]
      (str bugs problem))))

(defn format-response
  [show-times? result]
  (let [{:keys [val form tag ms]} result
        exception-data (check-exception val)]
    (cond
      exception-data
      (str form "\n"
           "=> " exception-data "\n\n")

      (= tag :err)
      (str form "\n"
           bugs val "\n\n")

      (= tag :out)
      (str val)

      (= tag :ret)
      (str form "\n"
           (when show-times? (str ms " ms "))
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
    (let [user         (::user/user db)
          form-to-eval (if (string? input) input (:form input))]
      {:db          (assoc db :form-to-eval form-to-eval)
       ::>repl-eval [:user user form-to-eval]})))

(reg-fx
  ::>login
  (fn [{:keys [options timeout]}]
    (let [user (user/->user (::user/name options)
                            (::user/uid options))]
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
    (when-let [uid (::user/uid db)]
      {:db      (assoc db
                  :proposed-user (::user/name login-options)
                  ::user/name nil)
       ::>login {:options
                 (assoc login-options ::user/uid uid)}})))

(reg-fx
  ::>logout
  (fn [{:keys [options timeout]}]
    (ws/chsk-send! [:repl-repl/logout options]
                   (or timeout default-server-timeout))))

(reg-event-fx
  ::logout
  (fn [{:keys [db]} _]
    (when-let [user (::user/user db)]
      {:db       (dissoc db ::user/user)
       ::>logout {:options user}})))

(reg-event-db
  ::show-add-lib-panel
  (fn [db [_ show?]]
    (assoc db :show-add-lib-panel show?)))

(reg-event-fx
  ::add-lib
  (fn [cofx [_ {:keys [name version url sha maven] :as lib}]]
    (let [use-ns   "(use 'clojure.tools.deps.alpha.repl)"
          lib-spec (str "(add-lib '" (string/trim name) " {"
                        (if maven
                          (str ":mvn/version \""
                               (string/trim version) "\"")
                          (str ":git/url \""
                               (string/trim url) "\" :sha \""
                               (string/trim sha) "\""))
                        "})")]
      {:db          (assoc (:db cofx) :proposed-lib lib)
       ::>repl-eval [:system (str use-ns "\n" lib-spec)]})))

;; ---------------------- Network sync

;; Text
(reg-fx
  ::>current-form
  (fn [[user other-users current-form]]
    (when other-users
      (ws/chsk-send!
        [:reptile/keystrokes {:form current-form
                              :user user}]))))

(reg-event-fx
  ::current-form
  (fn [{:keys [db]} [_ current-form]]
    (when-not (string/blank? (string/trim current-form))
      (let [user        (::user/user db)
            other-users (user/other-users (::user/name user)
                                          (::user/users db))]
        {:db             (assoc db :current-form current-form)
         ::>current-form [user other-users current-form]}))))

;; ------------------------------------------------------------------

(reg-event-db
  ::logged-in-user
  (fn [db [_ user]]
    (assoc db ::user/user user)))

(reg-event-db
  ::users
  (fn [db [_ users]]
    (println ::users users)
    (assoc db ::user/users users)))

(reg-event-db
  ::code-mirror
  (fn [db [_ code-mirror]]
    (assoc db :code-mirror code-mirror)))

(reg-event-db
  `::other-user-code-mirror
  (fn [db [_ code-mirror user]]
    (let [user-key         (keyword (::user/name user))
          user-code-mirror (assoc {} user-key code-mirror)]
      (assoc db :other-user-code-mirrors
                (merge (:other-user-code-mirrors db)
                       user-code-mirror)))))

(reg-event-fx
  ::other-user-keystrokes
  (fn [{:keys [db]} [_ {:keys [user form]}]]
    (when-not (= user (::user/user db))
      (let [editor-key   (keyword (::user/name user))
            code-mirrors (:other-user-code-mirrors db)
            code-mirror  (get code-mirrors editor-key)]
        {:db                        db
         ::code-mirror/set-cm-value [code-mirror form]}))))


