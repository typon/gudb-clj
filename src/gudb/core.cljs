(ns gudb.core
  (:require 
    [gudb.utils :refer [r-el r-component]]
    [gudb.layout :refer [App screen]]
    [gudb.flow :refer [app-state]]
    [cljs.pprint :refer [cl-format]]
    [shrimp-log.core :as l]
    ["blessed" :as blessed]
    ["react" :as react]
    ["create-react-class" :as create-react-class]
    ["react-blessed" :as react-blessed])
  (:use-macros [shrimp-log.macros :only [trace spy]]))


(l/set-opts! :out-file :log-file
             :pretty-print true)



; (def app-state (atom {:display 0 :history []}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components

; (def mybox
;   (r-el "box" {
;     "top" "center",
;     "left" "center",
;     "width" "50%",
;     "height" "50%",
;     "content" "Blessed React WORKS WOOO try this {bold} world{/bold}"
;     "border" {"type" "line"},
;     "style" {"fg" "white", "bg" "magenta"}
;     "tags" true}))

; (def App
;   (r-component "App"
;    :render (fn [props] mybox)))


(defn render [state]
  (react-blessed/render (r-el App state) screen))

(defn main! [] (do
  (trace "Starting gudb")
  (.key screen (clj->js ["escape" "q" "C-c"]) (fn [ch, key] (js/process.exit 0)))
  (render @app-state)
  ))
