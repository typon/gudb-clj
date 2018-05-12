(ns gudb.input-widget
  (:require
   [beicon.core :as rx]
   [potok.core :as ptk]
   [gudb.utils :refer [r-el r-component jsx->clj]]
   [gudb.styles :refer [color-scheme]]
   [gudb.streams :as strm :refer [app-state app-state-view]]
   [gudb.history-widget :as hbox]
   [gudb.gdb :as gdb])
  (:use-macros [shrimp-log.macros :only [trace spy debug]]))

(def InputBox
  (r-component
   "InputBox"
   :componentDidMount (fn [] (ptk/emit! app-state (->Input-Box-Initialize)))
   :shouldComponentUpdate (fn [nextProps, nextState] false)
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
              ; :keys true
              :inputOnFocus true
              :style (:input-box color-scheme)
              ; :readInput (fn [ev] (debug (str "KEYYYY: "ev)))
              ; :onKeypress (fn [_ key-obj] (.setValue (get-in @app-state-view [:elements :input-box]) "REE"))
              ; :onKeypress (fn [ch key-obj] (ptk/emit! app-state (->Input-Box-Keypress ch (js->clj key-obj))))
              :onSubmit #(ptk/emit! app-state (->Input-Box-Submit %1))
              ; :onCancel #(ptk/emit! app-state (->Input-Box-Cancel))
              }))))


(defn input-box-key-press [ch key]
  (do
    (trace (str "Emitting inputboxkey: " key))
    (ptk/emit! app-state (->Input-Box-Keypress (js->clj ch) (js->clj key)))))

(defrecord Input-Box-Initialize []
  ptk/UpdateEvent
  (update [_ state]
    (let [input-box (get-in state [:elements :input-box])
          kf (.-__olistener input-box)]
      (trace "Initializing input box!!!!!!!!!")
      (assoc-in state [:input-box :keypress-func] kf)))

  ptk/EffectEvent
  (effect [_ state stream]
    (let [input-box (get-in state [:elements :input-box])]
      (set! (.-__olistener input-box) input-box-key-press))))


(defrecord Input-Box-Update-Value []
  ptk/UpdateEvent
  (update [_ state]
    (let [curr-text (.-value (get-in state [:elements :input-box]))]
      (trace (str "In IBOX (update): current value: " curr-text))
      (assoc-in state [:input-box :value] curr-text))))

(defrecord Input-Box-Default-Key-Handler [ch key-obj]
  ptk/EffectEvent
  (effect [_ state stream]
    (let [kf (get-in state [:input-box :keypress-func])
          input-box (get-in state [:elements :input-box])]
      (trace (str "Calling default key handler with: " key-obj))
      (.call kf input-box (clj->js ch) (clj->js key-obj)))))


(defrecord Input-Box-Keypress [ch key-obj]
  ptk/WatchEvent
  (watch [_ state stream]
    (let [curr-text (get-in state [:input-box :value])
          keyo key-obj]
      (trace (str "In input key-press (watch). key: " (get key-obj "full")))
      (case (get key-obj "full")
        ; do nothing if enter was just pressed. This is handled in submit
        ; ("enter" "return") (rx/just (->Input-Box-Default-Key-Handler ch key-obj))
        ("escape") (rx/of (->Input-Box-Cancel) (->Input-Box-Default-Key-Handler ch key-obj))
        ("enter" "return") rx/empty
        "tab" (rx/just (->Auto-Box-Scroll :down))
        "S-tab" (rx/just (->Auto-Box-Scroll :up))
        (rx/of (->Input-Box-Default-Key-Handler ch key-obj)
               (->Input-Box-Update-Value)
               (->Auto-Box-Update))))))


(defrecord Input-Box-Focus []
  ptk/EffectEvent
  (effect [_ state stream]
    (let [input-box (get-in state [:elements :input-box])]
      (.focus input-box))))

(defrecord Input-Box-Set [text]
  ptk/UpdateEvent
  (update [_ state]
    (let [input-box (get-in state [:elements :input-box])]
      (trace (str "Setting input to value: " text))
      (assoc-in state [:input-box :value] text)))

  ptk/WatchEvent
  (watch [_ state stream]
    (let [input-box (get-in state [:elements :input-box])]
      (trace (str "ACtually VALUE: " text ";"))
      (.setValue input-box text)
      ; (.readInput input-box #(trace "Reading input..."))
      (rx/of (->Input-Box-Focus) (strm/->Render-Screen)))))


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
          (hbox/->History-Box-Append :cmd text)
          (->Input-Box-Set "")
          (->Auto-Box-Reset)
          (gdb/->Send-Cmd text))))))
          ;))))))


(def AutoCompleteBox
  (r-component "AutoCompleteBox"
               :render (fn [props] (r-el "list" {:ref (fn [el] (ptk/emit! app-state (strm/->Register-Elem :auto-box el)))
                                                 :key (:key props)
                                                 :name "auto-box"
                                                 :items (:items props)
                                                 :hidden (empty? (:items props))
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

(defrecord Auto-Box-Update []
  ptk/UpdateEvent
  (update [_ state]
    (let [text (get-in state [:input-box :value])
          auto-box (get-in state [:elements :auto-box])
          history-cmds (get-in state [:history-box :cmds])]
      (.setFront auto-box)
      (as-> state $
        (assoc-in $ [:auto-box :interactive] false)
        (case text
          "" (assoc-in $ [:auto-box :items] [])
          ; Default case
          ; figure out the matches to the current text from the history box list and remove the ones that dont match
          (assoc-in $ [:auto-box :items] (filter-items history-cmds (re-pattern (str "^" text ".*"))))))))

  ptk/WatchEvent
  (watch [_ state stream]
    (let [auto-items (get-in state [:auto-box :items])]
      (case auto-items
        [] (rx/of (->Auto-Box-Reset))
        (rx/empty)))))


(defrecord Auto-Box-Reset []
  ptk/UpdateEvent
  (update [_ state]
    (-> state
      (assoc-in [:auto-box :interactive] false)
      (assoc-in [:auto-box :items] [])))

  ptk/EffectEvent
  (effect [_ state stream]
    (let [auto-box (get-in state [:elements :auto-box])]
      (.clearItems auto-box)
      (set! (.-selected auto-box) 0))))

(defrecord Auto-Box-Set-Interactive [interactive?]
  ptk/UpdateEvent
  (update [_ state]
    (assoc-in state [:auto-box :interactive] interactive?)))

(defrecord Auto-Box-Scroll [direction]

  ptk/WatchEvent
  (watch [_ state stream]
    (let [auto-box (get-in state [:elements :auto-box])
          offset (case direction :down 1 :up -1)
          _ (.move auto-box offset)
          curr-selected (when-let [item (aget (.-items auto-box) (.-selected auto-box))]
                          (.-content item))
          ; TODO: Making this render after select up/down declarative.
          events [(->Auto-Box-Set-Interactive true)]
          events' (if curr-selected
                    (conj events (->Input-Box-Set curr-selected))
                    events)]
      (rx/from-coll events'))))
