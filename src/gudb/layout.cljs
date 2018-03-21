(ns gudb.layout
  (:require 
    [gudb.utils :refer [r-el r-component]]
    [gudb.styles :refer [color-scheme]]
    [gudb.flow :refer [dispatch app-state]]
    [gudb.gdb :refer [gdb-chan]]
    [cljs.core.async :refer [chan <! >! timeout pub sub unsub unsub-all]]
    ["blessed" :as blessed])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:use-macros [shrimp-log.macros :only [trace spy]]))

(def screen (blessed/screen (js-obj "smartCSR" true)))
(swap! app-state assoc-in [:elements :screen] screen)

 ;:on-change #(dispatch :change-name (.. % -target -value))}]])

;(defn output-box [props]
;  (r-el "box" {
;    "ref" (fn [el] (dispatch :register-elem {:el-name :output-box :el-obj el}))
;    "key" 1
;    "name" "output-box"
;    "top" "center",
;    "left" "center",
;    "width" "50%",
;    "height" "50%",
;    ; "content" (spy :info (str "State: " (:input-box-value props)))
;    "content" (spy :info (str "State: " (get props :input-box-value)))
;    ;"content" "REE"
;    "border" {"type" "line"},
;    "style" {"fg" "white", "bg" "magenta"}
;    "tags" true}))

(def HistoryBox
  (r-component "HistoryBox"
   :render (fn [props] (r-el "list" {
   :ref (fn [el] (dispatch :register-elem {:el-name :history-box :el-obj el}))
   :key (:key! props)
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



(defn input-box [&{:keys [key!]}]
  (r-el "textbox" {
    :ref (fn [el] (dispatch :register-elem {:el-name :input-box :el-obj el}))
    :key key!
    :name "input-box"
    :bottom 0
    :left 0
    :height 1,
    :mouse true,
    :tags true 
    :keys true
    :inputOnFocus false
    :style (:input-box color-scheme)
    ;"onKeypress" #(dispatch :change-name (.. % -target -value))
    :onKeypress (fn [_ key-obj] (dispatch :input-box-keypress (js->clj key-obj)))
    :onSubmit (fn [text] 
                 (do 
                   (go (>! gdb-chan {:topic :cmd-sent :cmd text}))
                   (dispatch :input-box-submit text))) 
    }))

(def CommandContainer
  (r-component "CommandContainer"
   :render (fn [props] 
             (r-el "element" {:label "Commands" 
                              :height (:height props)
                              :width (:width props)
                              :bottom (:bottom props)
                              :left (:left props)
                              :border {:type "line"} 
                              } 
                             (input-box {:key! 0})
                             (r-el "line"  {:orientation "horizontal", :bottom 1, :key 1, :height 1})
                             (r-el HistoryBox {:value (:input-box-value props)
                                              :key 2})))))

(def MainLayout
  (r-component "MainLayout"
   :render (fn [props] 
             (r-el "element" {} 
               (r-el CommandContainer (merge props {:key 0 :height "50%" :width "50%" :left 0 :bottom 0}))))))



(def App
  (r-component "App"
   :render (fn [props] 
             (r-el MainLayout props))))
