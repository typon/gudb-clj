(ns gudb.auto-widget
  (:require
   [potok.core :as ptk]
   [gudb.utils :refer [r-el r-component]]
   [gudb.styles :refer [color-scheme]]
   [gudb.streams :as strm :refer [app-state app-state-view]]
   [gudb.input-widget :as ibox])
  (:use-macros [shrimp-log.macros :only [trace spy debug]]))

(def AutoCompleteBox
  (r-component "AutoCompleteBox"
               :render (fn [props] (r-el "list" {:ref (fn [el] (ptk/emit! app-state (strm/->Register-Elem :auto-box el)))
                                                 :key (:key props)
                                                 :name "auto-box"
                                                 :items (:items props)
                                                 :hidden (empty? (:value props))
                                                 :bottom 2,
                                                 :left 1,
                                                 :width "50%-2",
                                                 :height 4
                                                 :invertSelected false,
                                                 :interactive (:interactive props)
                                                 :scrollable true,
                                                 :scrollbar {"ch" "|", "inverse" true},
                                                 :search true,
                                                 :mouse true,
                                                 :keys true,
                                                 :vi true,
                                                 :style {:fg "black",
                                                         :bg "white"}
                                                 :tags true}))))


(defn filter-items [items filter-regex]
  (as-> items $
    (map #(re-matches filter-regex %1) $)
    (remove nil? $)
    (distinct $)
    (into [] $)))

(defrecord Auto-Box-Update [text]
  ptk/UpdateEvent
  (update [_ state]
    (let [auto-box (get-in state [:elements :auto-box])
          history-items (:history-box-items state)]
      (.setFront auto-box)
      (as-> state $)
        (assoc-in state [:auto-box :interactive] false)
        (case text
          "" (assoc-in $ [:auto-box :items] [])
          ; Default case
          ; figure out the matches to the current text from the history box list and remove the ones that dont match
          (assoc-in $ [:auto-box :items] (filter-items history-items (re-pattern (str "^" text ".*"))))))))

(defrecord Auto-Box-Reset []
  ptk/UpdateEvent
  (update [_ state]
    (let [auto-box (get-in state [:elements :auto-box])]
      (.clearItems auto-box)
      (set! (.-selected auto-box) 0)
      (-> state
        (assoc-in [:auto-box :interactive] false)
        (assoc-in [:auto-box :items] [])))))

(defrecord Auto-Box-Scroll [direction]
  ptk/UpdateEvent
  (update [_ state]
    (assoc-in state [:auto-box :interactive] true)

  ptk/WatchEvent
  (watch [_ state stream]
    (let [auto-box (get-in state [:elements :auto-box])
          offset (case direction :down 1 :up -1)
          curr-selected (when-let [item (aget (.-items auto-box) (.-selected auto-box))]
                          (.-content item))
          ; TODO: Making this render after select up/down declarative.
          events (rx/from-coll [(->Auto-Box-Move offset) (strm/->Render-Screen)])]
      (if curr-selected
        (conj events (ibox/->Input-Box-Set curr-selected))
        events)))))

(defrecord Auto-Box-Move [offset]
  ptk/EffectEvent
  (effect [_ state stream]
    (let [auto-box (get-in state [:elements :auto-box])]
      (.move auto-box offset))))
