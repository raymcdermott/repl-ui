(ns repl.repl.ws
  (:require
    goog.date.Date
    [taoensso.encore :refer [have have?]]
    [taoensso.timbre :refer [tracef debugf infof warnf errorf]]
    [taoensso.sente :as sente :refer [cb-success?]]
    [taoensso.sente.packers.transit :as sente-transit]
    [re-frame.core :as re-frame]
    [repl.repl.config :as config]))

; --- WS client ---
(declare chsk ch-chsk chsk-send! chsk-state)

(defmulti -event-msg-handler
          "Dispatch on :id from Sente `event-msg`s"
          :id)

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [ev-msg]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler :default
  [{:keys [event]}]
  (println "Unhandled event: %s" event))

(defmethod -event-msg-handler :chsk/state
  [{:keys [?data]}]
  (let [[_ new-state-map] (have vector? ?data)]
    (re-frame/dispatch [:repl.repl.events/client-uid (:uid new-state-map)])
    (re-frame/dispatch [:repl.repl.events/network-status (:open? new-state-map)])))

(defmethod -event-msg-handler :chsk/recv
  [{:keys [?data]}]
  (let [push-event (first ?data)
        push-data  (first (rest ?data))]
    (cond
      (= push-event :repl-repl/keystrokes)
      (re-frame/dispatch [:repl.repl.events/other-user-keystrokes push-data])

      (= push-event :repl-repl/users)
      (re-frame/dispatch [:repl.repl.events/users push-data])

      (= push-event :repl-repl/eval)
      (re-frame/dispatch [:repl.repl.events/eval-result push-data])

      (= push-event :chsk/ws-ping)
      :noop                                                 ; do reply

      :else
      (println "Unhandled data push: %s" push-event))))

;; The WS connection is established ... get the team name and secret
(defmethod -event-msg-handler :chsk/handshake
  []
  ;; TODO add the user in here if we are logged in
  (re-frame/dispatch [:repl.repl.events/team-bootstrap]))

(defonce router_ (atom nil))

(defn stop-router! []
  (when-let [stop-f @router_]
    (stop-f)))

(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-client-chsk-router!
            ch-chsk event-msg-handler)))

(let [packer (sente-transit/get-transit-packer)

      {:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
        "/chsk"
        {:type   :auto
         :host   config/server-host
         :packer packer})]

  (def chsk chsk)

  ; ChannelSocket's receive channel
  (def ch-chsk ch-recv)

  ; ChannelSocket's send API fn
  (def chsk-send! send-fn)

  ; Watchable, read-only atom
  (def chsk-state state)

  ;; Now we have all of this set up we can start the router
  (start-router!))
