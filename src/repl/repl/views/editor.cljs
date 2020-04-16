(ns repl.repl.views.editor
  (:require
    [re-frame.core :refer [subscribe dispatch]]
    [re-com.core :refer [h-box v-box box button gap line scroller border label input-text md-circle-icon-button
                         md-icon-button input-textarea h-split v-split popover-anchor-wrapper
                         popover-content-wrapper title flex-child-style p slider]]
    [re-com.splits :refer [hv-split-args-desc]]
    [reagent.core :as reagent]
    [repl.repl.code-mirror :as code-mirror]
    [repl.repl.events :as events]
    [repl.repl.subs :as subs]
    [repl.repl.views.other-editor :as other-editor]
    [repl.repl.views.add-lib :as add-lib]
    [repl.repl.views.show-team-data :as team]
    [repl.repl.views.eval :as eval-view]
    [repl.repl.views.status :as status]
    [repl.repl.user :as user]))

(defonce default-style {:font-family   "Menlo, Lucida Console, Monaco, monospace"
                        :border-radius "3px"
                        :border        "1px solid lightgrey"
                        :padding       "5px 5px 5px 5px"})

(defonce eval-panel-style (merge (flex-child-style "1")
                                 default-style))

(defonce edit-style (assoc default-style :border "2px solid lightgrey"))
(defonce edit-panel-style (merge (flex-child-style "1") edit-style))

(defonce status-style (merge (dissoc default-style :border)
                             {:font-size   "10px"
                              :font-weight "lighter"
                              :color       "lightgrey"}))

(defn notify-edits
  [new-value change-object]
  (dispatch [::events/current-form new-value change-object]))

(defn editor-did-mount
  []
  (fn [this-textarea]
    (let [node            (reagent/dom-node this-textarea)
          extra-edit-keys {:Cmd-Enter (fn [cm]
                                        (dispatch
                                          [::events/eval (.getValue cm)]))}
          options         {:options {:lineWrapping  true
                                     :autofocus     true
                                     :matchBrackets true
                                     :lineNumbers   true
                                     :extraKeys     extra-edit-keys}}
          code-mirror     (code-mirror/parinfer node options)]

      (.on code-mirror "change" (fn [cm co]
                                  (notify-edits (.getValue cm) co)))

      (dispatch [::events/code-mirror code-mirror]))))

(defn edit-component
  [panel-name]
  (reagent/create-class
    {:component-did-mount  (editor-did-mount)
     :reagent-render       (code-mirror/text-area panel-name)
     :component-did-update #(-> nil)                        ; noop to prevent reload
     :display-name         "local-editor"}))

(defn edit-panel
  [user]
  (let [current-form (subscribe [::subs/current-form])]
    (fn []
      (let [editor-name (::user/name user)]
        [v-box :size "auto" :children
         [[box :size "auto"
           :style edit-panel-style
           :child [edit-component editor-name]]
          [gap :size "5px"]
          [h-box :children
           [[button
             :label "Eval (or Cmd-Enter)"
             :tooltip "Send the form(s) for evaluation"
             :class "btn-success"
             :on-click #(dispatch [::events/eval @current-form])]
            [gap :size "5px"]]]]]))))

(defn others-panel
  [other-users]
  (let [visible-count (count other-users)]
    (if (and visible-count (> visible-count 0))
      [other-editor/other-panels other-users]
      [other-editor/waiting-panel])))

(defn top-row
  []
  [h-box :height "20px"
   :style other-editor/other-editors-style
   :justify :between
   :children
   [[box :align :center :justify :start
     :child
     [button :label "👋🏽" :class "btn-default btn"
      :tooltip "Logout of the system"
      :on-click #(dispatch [::events/logout])]]
    [h-box :align :center
     :children
     [[add-lib/add-lib-panel]
      [button :label "✚ ⚗️" :class "btn-default btn"
       :tooltip "Dynamically add a dependency"
       :on-click #(dispatch [::events/show-add-lib-panel true])]]]
    [h-box :align :center
     :children
     [[team/team-data-panel]
      [button :label "✚ 👥" :class "btn-default btn"
       :tooltip "Get a link to invite others to the REPL session"
       :on-click #(dispatch [::events/show-team-data true])]]]
    [gap :size "50px"]
    [box :align :center :justify :start
     :child
     [button :label "Hide / Show Others" :class "btn-warning btn"
      :tooltip "Hide / Show live keyboard input from the other editors"
      :on-click #(dispatch [::events/toggle-others])]]]])

(defn main-panels
  [user]
  (let [other-users (subscribe [::subs/other-users])]
    (fn []
      [v-box :style {:position "absolute"
                     :top      "0px"
                     :bottom   "0px"
                     :width    "100%"}
       :children
       [[top-row]
        [v-split :initial-split 20 :splitter-size "5px"
         :panel-1 [others-panel @other-users]
         :panel-2 [h-split :margin "5px" :splitter-size "5px"
                   :panel-1 [edit-panel user]
                   :panel-2 [v-box :style eval-panel-style
                             :children [[eval-view/eval-panel user]]]]]
        [status/status-bar user]]])))
