(ns repl.repl.ziggy.main-view
  (:require
    [re-frame.core :refer [subscribe]]
    [repl.repl.ziggy.subs :as subs]
    [repl.repl.ziggy.views.login :as login]
    [repl.repl.ziggy.views.editor :as editor]))

(defn main-panel
  []
  (let [logged-in-user @(subscribe [::subs/logged-in-user])]
    (if-not logged-in-user
      [login/authenticate]
      [editor/main-panels logged-in-user])))
