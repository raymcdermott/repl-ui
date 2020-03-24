(ns repl.repl.ziggy.helpers)

(defn js->cljs
  [js-obj]
  (js->clj js-obj :keywordize-keys true))
