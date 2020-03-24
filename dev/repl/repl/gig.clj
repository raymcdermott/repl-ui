(ns repl.repl.gig
  (:use [repl.repl.band.reload-server]))


;------------------- ************* -------------------
;
; Bootstrap when the namespace is loaded
;
;------------------- ************* -------------------

(boot-and-watch-fs! "/Users/ray/dev/repl-repling/repl-repl/server"
                    58885 "warm-blooded-lizards-rock")

