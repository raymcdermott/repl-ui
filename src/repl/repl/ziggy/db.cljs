(ns repl.repl.ziggy.db)

(defonce default-db
         {:name          "repl"
          ; 1 minute
          :inactivity-ms (* 60 1000)})
