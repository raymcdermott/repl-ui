(ns repl.repl.ziggy.views.status
  (:require
    [re-frame.core :as re-frame]
    [re-com.core :refer [h-box v-box box button gap line scroller border label input-text md-circle-icon-button
                         md-icon-button input-textarea modal-panel h-split v-split title flex-child-style
                         radio-button p]]
    [repl.repl.ziggy.subs :as subs]
    [repl.repl.user :as user]))

(defonce default-style
         {:font-family "Menlo, Lucida Console, Monaco, monospace"
          :border      "1px solid lightgray"
          :padding     "5px 5px 5px 5px"})

(defonce status-style (merge (dissoc default-style :border)
                         {:font-size   "10px"
                          :font-weight "lighter"
                          :color       "lightgrey"}))

(defn status-bar
  [user]
  (let [network-status @(re-frame/subscribe [::subs/network-status])
        network-style  {:color (if network-status
                                 "rgba(127, 191, 63, 0.32)"
                                 "red")}]
    [v-box :children
     [[line]
      [h-box :size "20px" :style status-style
       :justify :between :align :center
       :children
       [[label :label (str "Login: " (::user/name user))]
        [h-box :gap "5px" :children
         [[label :style network-style :label "Connect Status:"]
          (let [icon-suffix (if network-status "-done" "-off")]
            [md-icon-button :md-icon-name (str "zmdi-cloud" icon-suffix)
             :size :smaller :style network-style])]]
        [label :style network-style :label "Font size: Large"]]]]]))
