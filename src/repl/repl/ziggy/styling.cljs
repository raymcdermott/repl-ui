(ns repl.repl.ziggy.styling)

;; TODO - maintain styling as new users added

;; TODO SPEC INPUTS TO THIS
(defn styled-editor
    [{:keys [name] :as editor} color icon]
  (assoc editor :style {:abbr  (subs name 0
                                     (min (count name) 2))
                        :style {:color color}
                        :icon  icon}))

(defn editor-icons
    ([] (editor-icons :random false))
    ([& {:keys [random]}]
     (let [data    ["mood" "mood-bad" "run" "walk" "face" "male-female" "lamp" "cutlery"
                    "flower" "flower-alt" "coffee" "cake" "attachment" "attachment-alt"
                    "fire" "nature" "puzzle-piece" "drink" "truck" "car-wash" "bug"]
           sort-fn (if random (partial shuffle) (partial sort))]
       (sort-fn (map (partial str "zmdi-") data)))))

(defn colour-palette
    ([] (colour-palette :random false))
    ([& {:keys [random]}]
     (let [data    ["silver" "gray" "black" "red" "maroon" "olive" "lime"
                    "green" "aqua" "teal" "blue" "navy" "fuchsia" "purple"]
           sort-fn (if random (partial shuffle) (partial sort))]
       (sort-fn data))))
