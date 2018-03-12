(ns gudb.layout
  (:require 
    [gudb.utils :refer [r-el r-component]]
    [gudb.styles :refer [color-scheme]]
    [gudb.flow :refer [dispatch app-state]]
    ["blessed" :as blessed])
  (:use-macros [shrimp-log.macros :only [trace spy]]))

(def screen (blessed/screen (js-obj "smartCSR" true)))
(swap! app-state assoc-in [:elements :screen] screen)

 ;:on-change #(dispatch :change-name (.. % -target -value))}]])

(def output-box
  (r-el "box" {
    "ref" (fn [el] (dispatch :register-elem {:el-name :output-box :el-obj el}))
    "key" 1
    "name" "output-box"
    "top" "center",
    "left" "center",
    "width" "50%",
    "height" "50%",
    "content" "Blessed React WORKS WOOO try this {bold} world{/bold}"
    "border" {"type" "line"},
    "style" {"fg" "white", "bg" "magenta"}
    "tags" true}))

(def input-box
  (r-el "textbox" {
    "ref" (fn [el] (dispatch :register-elem {:el-name :input-box :el-obj el}))
    "key" 0
    "name" "input-box"
    "bottom" 0
    "left" 0
    "height" 1
    "tags" true 
    "keys" true
    "inputOnFocus" false
    "style" (:input-box color-scheme)
    ;"onKeypress" #(dispatch :change-name (.. % -target -value))
    "onKeypress" (fn [_ key-obj] (dispatch :input-box-keypress (js->clj key-obj)))
    ;"onKeypress" (fn [_ key-obj] (dispatch :input-box-keypress (js* "this.refs")))
    }))

; (def input-box
;   (r-component "input-box" 
;    :render (fn [props] (r-el "textbox" {
;     "ref" (fn [el] (dispatch :register-elem el))
;     "key" 0
;     "name" "input-box"
;     "bottom" 0
;     "left" 0
;     "height" 1
;     "tags" true "keys" true
;     "inputOnFocus" false
;     "style" (:input-box color-scheme)
;     ;"onKeypress" #(dispatch :change-name (.. % -target -value))
;     "onKeypress" (fn [_ key-obj] (dispatch :input-box-keypress (js->clj key-obj)))
;     ;"onKeypress" (fn [_ key-obj] (dispatch :input-box-keypress (js* "this.refs")))
;     }))))

(def layout-grid [[input-box] [output-box]])

(def App
  (r-component "App"
   ; :render (fn [props] (r-el "element" {} (r-el input-box {"key" 2}) output-box))))
   :render (fn [props] (r-el "element" {} input-box output-box))))
