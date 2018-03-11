(ns gudb.core
  (:require [gudb.utils :refer [r-el r-component]]
            ["blessed" :as blessed]
            ["react" :as react]
            ["create-react-class" :as create-react-class]
            ["react-blessed" :as react-blessed]))


(def app-state (atom {:display 0 :history []}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components

(def mybox
  (r-el "box" {
    "top" "center",
    "left" "center",
    "width" "50%",
    "height" "50%",
    "content" "Blessed React WORKS WOOO try this {bold} world{/bold}"
    "border" {"type" "line"},
    "style" {"fg" "white", "bg" "magenta"}
    "tags" true}))

(def App
  (r-component "App"
   :render (fn [props] mybox)))

(def screen (blessed/screen (js-obj "smartCSR" true)))

(defn render [state]
  (react-blessed/render (r-el App state) screen))

(defn main! [] (do
  (render @app-state)
  ))
