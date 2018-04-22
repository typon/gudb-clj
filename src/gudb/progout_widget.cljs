(ns gudb.progout-widget
  (:require
   [clojure.string :as string]
   [beicon.core :as rx]
   [potok.core :as ptk]
   [gudb.utils :refer [r-el r-component]]
   [gudb.styles :refer [color-scheme]]
   [gudb.streams :as strm :refer [app-state]])
  (:use-macros [shrimp-log.macros :only [trace spy debug]]))


(def ProgramOutputBox
  (r-component "ProgramOutputBox"
               :render (fn [props] (r-el "log" {:ref #(ptk/emit! app-state (strm/->Register-Elem :prog-output-box %))
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

(def xform-prog-output
  (comp
   (drop 1)
   (map #(:text %))
   (mapcat string/split-lines)
   (remove (fn [text] (not= nil (re-find #"^&\"warning: GDB:" text)))) ; Supress warnings
   ))

(defrecord Target-Output-Received-Raw [text thread]
  ptk/EffectEvent
  (effect [_ state stream]
    nil))

(rx/subscribe (->>
               (ptk/input-stream app-state)
               (rx/filter #(instance? Target-Output-Received-Raw %))
               (rx/transform xform-prog-output))
              #(ptk/emit! app-state (->Target-Output-Received %1 %2)))


(defrecord Target-Output-Received [text thread]
  ptk/UpdateEvent
  (update [_ state]
    (assoc-in state [:prog-output-box :last] text))
  ptk/WatchEvent
  (watch [_ state stream]
      (case text
        ("" nil) (rx/empty)
        (rx/just (->Log text)))))


(defrecord Log [text]
  ptk/EffectEvent
  (effect [_ state stream]
    (let [prog-output-box (get-in state [:elements :prog-output-box])]
      (.log prog-output-box text))))
