(ns gudb.core
  (:require
   ["blessed" :as blessed]
   ["react" :as react]
   ["react-blessed" :as react-blessed]
   [shrimp-log.core :as log]
   [beicon.core :as rx]
   [potok.core :as ptk]
   [clojure.string :as str]
   [cljs.pprint :refer [cl-format]]
   [gudb.utils :refer [r-el r-component]]
   [gudb.layout :refer [App screen-ref]]
   [gudb.streams :as strm :refer [app-state app-state-view]]
   [gudb.gdb :refer [load-gdb]])
  (:use-macros [shrimp-log.macros :only [debug trace spy]]))


(log/set-opts! :out-file :log-file
             :pretty-print true
             :log-level :debug)

(defonce process (js* "process"))

(defn process-args []
  (assoc {} :prog-path (nth (.-argv process) 2)))

(defn render [state]
  (do
    (debug "Rendering...........")
    (react-blessed/render (r-el App state) @screen-ref)))

(defn main! []
  (let [{:keys [prog-path]} (process-args)
        [screen] [(blessed/screen (clj->js {:smartCSR true "cursor.blink" true :dockBorders true :log "blessedz.log"}))]]
    (debug "Starting GUDB!")
    (load-gdb prog-path)
    (reset! screen-ref screen)
    (ptk/emit! app-state (strm/->Register-Elem :screen @screen-ref))
    ; (swap! app-state assoc-in [:elements :screen] @screen-ref)
    (.key @screen-ref (clj->js ["q" "C-c"]) (fn [ch, key] (js/process.exit 0)))
    (render @app-state-view)))

; Every time state changes, call render function again to redraw everything.
;; (add-watch app-state :redraw
;;            (fn [_ _ old-state new-state]
;;     ; Only re-render when old-state != new-state, ignoring the :elements
;;     ; because they get regenerated every time react renders. If we re-render
;;     ; then we get in infinite loop.
;;              (when-not
;;               (=
;;                (dissoc old-state :elements)
;;                (dissoc new-state :elements))
;;                (render new-state))))

;; (rx/subscribe
;;  (->> app-state (rx/dedupe))
;;  #(debug (str "State changed to: " %)))
