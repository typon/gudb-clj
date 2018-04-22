(ns gudb.input-widget
  (:require
   [beicon.core :as rx]
   [potok.core :as ptk]
   [gudb.utils :refer [r-el r-component]]
   [gudb.styles :refer [color-scheme]]
   [gudb.streams :as strm :refer [app-state]]
   [gudb.history-widget :as hbox]
   [gudb.gdb :as gdb])
  (:use-macros [shrimp-log.macros :only [trace spy debug]]))

(def InputBox
  (r-component
   "InputBox"
   :render (fn [props]
             (r-el
              "textbox"
              {:ref #(ptk/emit! app-state (strm/->Register-Elem :input-box %))
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
              :onKeypress (fn [_ key-obj] (ptk/emit! app-state (->Input-Box-Keypress (js->clj key-obj))))
              :onSubmit #(ptk/emit! app-state (->Input-Box-Submit %1))
              :onCancel #(ptk/emit! app-state (->Input-Box-Cancel))}))))

(defrecord Input-Box-Keypress [key-obj]
  ptk/UpdateEvent
  (update [_ state]
    (let [curr-text (.-value (get-in state [:elements :input-box]))]
      (assoc-in state [:input-box :value] curr-text)))

  ptk/WatchEvent
  (watch [_ state stream]
    (let [curr-text (get-in state [:input-box :value])]
      (case (get key-obj "full")
        ; do nothing if enter was just pressed. This is handled in submit
        "return" (rx/empty)
        "enter" (rx/empty)
        "tab" (rx/just (->Auto-Box-Scroll :down))
        "S-tab" (rx/just (->Auto-Box-Scroll :up))
      (rx/just (->Auto-Box-Update curr-text))))))

(defrecord Input-Box-Set [text]
  ptk/UpdateEvent
  (update [_ state]
    (let [input-box (get-in state [:elements :input-box])]
      (assoc-in state [:input-box :value] text)))

  ptk/EffectEvent
  (effect [_ state stream]
    (let [input-box (get-in state [:elements :input-box])]
      (.setValue input-box text)
      (.readInput input-box nil))))

(defrecord Input-Box-Cancel []
  ptk/UpdateEvent
  (update [_ state]
    (let [input-box (get-in state [:elements :input-box])]
      (assoc-in state [:input-box :value] "")))

  ptk/WatchEvent
  (watch [_ state stream]
      (rx/just (->Auto-Box-Reset)))

  ptk/EffectEvent
  (effect [_ state stream]
    (let [input-box (get-in state [:elements :input-box])]
      (.clearValue input-box))))


(defrecord Input-Box-Submit [text]
  ptk/WatchEvent
  (watch [_ state stream]
    (let [input-box (get-in state [:elements :input-box])]
      (case text
        ("" nil) (rx/just (->Input-Box-Set ""))
        (rx/of
          (rx/just (hbox/->History-Box-Append :cmd text))
          (rx/just (->Input-Box-Set ""))
          (rx/just (->Auto-Box-Reset))
          (rx/just (gdb/->Send-Cmd text)))))))


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
      (as-> state $
        (assoc-in state [:auto-box :interactive] false)
        (case text
          "" (assoc-in $ [:auto-box :items] [])
          ; Default case
          ; figure out the matches to the current text from the history box list and remove the ones that dont match
          (assoc-in $ [:auto-box :items] (filter-items history-items (re-pattern (str "^" text ".*")))))))))

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
    (assoc-in state [:auto-box :interactive] true))

  ptk/WatchEvent
  (watch [_ state stream]
    (let [auto-box (get-in state [:elements :auto-box])
          offset (case direction :down 1 :up -1)
          curr-selected (when-let [item (aget (.-items auto-box) (.-selected auto-box))]
                          (.-content item))
          ; TODO: Making this render after select up/down declarative.
          events (rx/from-coll [(->Auto-Box-Move offset) (strm/->Render-Screen)])]
      (if curr-selected
        (conj events (->Input-Box-Set curr-selected))
        events))))

(defrecord Auto-Box-Move [offset]
  ptk/EffectEvent
  (effect [_ state stream]
    (let [auto-box (get-in state [:elements :auto-box])]
      (.move auto-box offset))))
