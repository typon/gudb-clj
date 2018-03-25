(ns gudb.core
  (:require 
    [gudb.utils :refer [r-el r-component]]
    [gudb.layout :refer [App screen]]
    [gudb.flow :refer [app-state]]
    [gudb.gdb :refer [load-gdb]]
    [cljs.pprint :refer [cl-format]]
    [shrimp-log.core :as l]
    ["blessed" :as blessed]
    ["react" :as react]
    ["create-react-class" :as create-react-class]
    ["react-blessed" :as react-blessed])
  (:use-macros [shrimp-log.macros :only [debug trace spy]]))

(l/set-opts! :out-file :log-file
             :pretty-print true
             :log-level :debug)

(defonce process (js* "process"))


(defn process-args [] 
  (assoc {} :prog-path (nth (.-argv process) 2)))

(defn render [state]
  (do 
    (debug "Rendering...........")
    (react-blessed/render (r-el App state) screen)))

(defn main! []
  (let [{:keys [prog-path]} (process-args)]
    (trace "Starting gudb")
    (load-gdb prog-path)
    (.key screen (clj->js ["escape" "q" "C-c"]) (fn [ch, key] (js/process.exit 0)))
    (render @app-state)
  ))

; Every time state changes, call render function again to redraw everything.
(add-watch app-state :redraw 
  (fn [_ _ old-state new-state]
    ; Only re-render when old-state != new-state, ignoring the :elements
    ; because they get regenerated every time react renders. If we re-render
    ; then we get in infinite loop.
    (when-not 
      (= 
        (dissoc old-state :elements)
        (dissoc new-state :elements))
      (render new-state))))
