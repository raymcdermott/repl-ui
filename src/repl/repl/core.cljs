(ns repl.repl.core
  (:require
    [reagent.core :as reagent]
    [re-frame.core :as re-frame]
    [repl.repl.events :as events]
    [repl.repl.subs]
    [repl.repl.main-view :as main-view]))

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
