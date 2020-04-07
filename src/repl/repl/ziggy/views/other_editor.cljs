(ns repl.repl.ziggy.views.other-editor
  (:require
    [re-frame.core :as re-frame :refer [subscribe]]
    [re-com.core :refer [box h-box v-box gap label
                         md-icon-button slider flex-child-style]]
    [re-com.splits :refer [hv-split-args-desc]]
    [reagent.core :as reagent]
    [repl.repl.ziggy.subs :as subs]
    [repl.repl.ziggy.events :as events]
    [repl.repl.ziggy.code-mirror :as code-mirror]
    [repl.repl.user :as user]))

(defonce other-editors-style {:padding "20px 20px 20px 10px"})

(defn other-editor-did-mount
  [user]
  (fn [this]
    (let [node        (reagent/dom-node this)
          options     {:options {:lineWrapping true
                                 :readOnly     true}}
          code-mirror (code-mirror/parinfer node options)]
      (re-frame/dispatch [::events/other-user-code-mirror code-mirror user]))))

;; TODO visibility toggle ... we never get here cos react
(defn other-component
  [user]
  (let [editor-name (::user/name user)]
    (reagent/create-class
      {:component-did-mount  (other-editor-did-mount user)
       :reagent-render       (code-mirror/text-area editor-name)
       :component-did-update #(-> nil)                      ; noop to prevent reload
       :display-name         (str "network-editor-" editor-name)})))

; 1 - use the outer / inner pattern
; 2 - use the visible invisible property on each editor icon
; 3 - outer component to subscribe on the given editor and check the visibility

(defonce other-panel-style
         (merge (flex-child-style "1")
                {:font-family   "Menlo, Lucida Console, Monaco, monospace"
                 :border-radius "8px"
                 :border        "1px solid lightgrey"
                 :padding       "5px 5px 5px 5px"}))

(defn network-editor-panel
  [user]
  [box :size "auto" :style other-panel-style
   :child [other-component user]])

(defn other-panels
  [other-users]
  [v-box :gap "2px" :size "auto"
   :children
   (vec (map #(network-editor-panel %) other-users))])
