(ns gudb.layout
  (:require
   [shrimp-log.core :as l]
   [gudb.flow]
   [gudb.utils :refer [r-el r-component]]
   [gudb.styles :refer [color-scheme]]
   [gudb.streams :refer [dispatch]]
   [cljs.core.async :refer [chan <! >! timeout pub sub unsub unsub-all]]
   ["blessed" :as blessed]
   ["editor-widget" :as editor-widget]
   ["react-blessed-contrib" :refer  [createBlessedComponent]]

   ; ["editor-widget" :as Editor]
   ; ["react-blessed-contrib" :refer  [createBlessedComponent]])
   )
  (:use-macros [shrimp-log.macros :only [trace spy debug]]))

; (def Editor (createBlessedComponent editor-widget))

(debug blessed/list)
(debug editor-widget)
; (def Editor (createBlessedComponent blessed/list))
(def Editor (createBlessedComponent editor-widget))

(defonce screen-ref (atom nil))

; import { createBlessedComponent } = 'react-blessed-contrib';

; (def Editor (createBlessedComponent editor-widget))

(def SourceBox
  (r-component "SourceBox"
               :render (fn [props] (r-el Editor {:key 123
                                                 :label "Source" 
                                                 :top (:top props),
                                                 :left (:left props),
                                                 :width (:width props),
                                                 :height (:height props),
                                                 :style {:fg "white",
                                                         :bg "magenta"},
                                                 :tags true}))))

(def HistoryBox
  (r-component "HistoryBox"
               :render (fn [props] (r-el "list" {:ref (fn [el] (dispatch :register-elem {:el-name :history-box :el-obj el}))
                                                 :key (:key props)
                                                 :name "history-box"
                                                 :bottom 2,
                                                 :top 0,
                                                 :left 0,
                                                 :width "100%-2",
                                                 :height "100%-4",
                                                 :invertSelected false,
                                                 :alwaysScroll true,
                                                 :scrollable true,
                                                 :scrollbar {"ch" "|", "inverse" true},
                                                 :search true,
                                                 :mouse true,
                                                 :keys true,
                                                 :vi true,
                                                 :style {:fg "white",
                                                         :bg "magenta",
                                                         :selected {:fg "white" :bg "magenta"}}
                                                 :tags true}))))

(def ProgramOutputBox
  (r-component "ProgramOutputBox"
               :render (fn [props] (r-el "log" {:ref (fn [el] (dispatch :register-elem {:el-name :prog-output-box :el-obj el}))
                                                :key (:key props)
                                                :name "prog-output-box"
                                                :label "Program Output"
                                                :border {:type "line"}
                                                :bottom (:bottom props),
                                                :right (:right props),
                                                :width (:width props)
                                                :height (:height props)
                                                :invertSelected false,
                                                :alwaysScroll true,
                                                :scrollable true,
                                                :scrollbar {"ch" "|", "inverse" true},
                                                :search true,
                                                :mouse true,
                                                :keys true,
                                                :vi true,
                                                :style {:fg "white",
                                                        :bg "magenta",
                                                        :selected {:fg "white" :bg "magenta"}}
                                                :tags true}))))

(defn input-box [& {:keys [key]}]
  (r-el "textbox" {:ref (fn [el] (dispatch :register-elem {:el-name :input-box :el-obj el}))
                   :key key
                   :name "input-box"
                   :bottom 0
                   :left 0
                   :height 1,
                   :mouse true,
                   :tags true
                   :keys true
                   :inputOnFocus true
                   :style (:input-box color-scheme)
    ;"onKeypress" #(dispatch :change-name (.. % -target -value))
                   :onKeypress (fn [_ key-obj] (dispatch :input-box-keypress (js->clj key-obj)))
                   :onSubmit (fn [text]
                               (do
                                 (dispatch :input-box-submit text)))
                   :onCancel (fn [_]
                               (do
                                 (dispatch :input-box-cancel nil)))}))

(def AutoCompleteBox
  (r-component "AutoCompleteBox"
               :render (fn [props] (r-el "list" {:ref (fn [el] (dispatch :register-elem {:el-name :autocomplete-box :el-obj el}))
                                                 :key (:key props)
                                                 :name "autocomplete-box"
                                                 :items (:value props)
                                                 :hidden (empty? (:value props))
                                                 :bottom 2,
                                                 :left 1,
                                                 :width "50%-2",
                                                 :height 4
                                                 :invertSelected false,
                                                 :interactive (:interactive props)
   ; :alwaysScroll true,
                                                 :scrollable true,
                                                 :scrollbar {"ch" "|", "inverse" true},
                                                 :search true,
                                                 :mouse true,
                                                 :keys true,
                                                 :vi true,
                                                 :style {:fg "black",
                                                         :bg "white"}
                                                 :tags true,
   ; :border {:type "bg", 
   ;          :left false
   ;          :top true
   ;          :right false
   ;          :bottom false}
}))))

(def CommandContainer
  (r-component "CommandContainer"
               :render (fn [props]
                         (r-el "element" {:label "Commands"
                                          :height (:height props)
                                          :width (:width props)
                                          :bottom (:bottom props)
                                          :left (:left props)
                                          :border {:type "line"}}
                               (input-box {:key 0})
                               (r-el "line"  {:orientation "horizontal", :bottom 1, :key 1, :height 1})
                               (r-el HistoryBox {:value (:input-box-value props) :key 2})
                               (r-el AutoCompleteBox (assoc (:auto-box props) :key 3))))))

(def MainLayout
  (r-component "MainLayout"
               :render (fn [props]
                         (r-el "element" {}
                               (r-el SourceBox (merge props {:key 0 :height "50%" :width "50%" :left 0 :top 0}))
                               (r-el CommandContainer (merge props {:key 1 :height "50%" :width "50%" :left 0 :bottom 0}))
                               (r-el ProgramOutputBox (merge props {:key 2 :height "50%" :width "50%" :right 0 :bottom 0}))))))
                               ;))))

(def App
  (r-component "App"
               :render (fn [props]
                         (r-el MainLayout props))))
