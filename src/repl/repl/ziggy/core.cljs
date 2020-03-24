(ns repl.repl.ziggy.core
  (:require
    [reagent.core :as reagent]
    [re-frame.core :as re-frame]
    [repl.repl.ziggy.events :as events]
    [repl.repl.ziggy.main-view :as main-view]
    [repl.repl.ziggy.config :as config]))

(defn dev-setup []
  (enable-console-print!))

(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [main-view/main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init []
  (re-frame/dispatch-sync [::events/initialize-db])
  (dev-setup)
  (mount-root))
