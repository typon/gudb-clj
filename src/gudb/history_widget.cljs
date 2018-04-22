(ns gudb.history-widget
  (:require
   [potok.core :as ptk]
   [gudb.utils :refer [r-el r-component]]
   [gudb.constants :refer [prefix]]
   [gudb.styles :refer [color-scheme]]
   [gudb.streams :as strm :refer [app-state]])
  (:use-macros [shrimp-log.macros :only [trace spy debug]]))

(def HistoryBox
  (r-component
   "HistoryBox"
   :render (fn [props]
             (r-el "list"
              {:ref #(ptk/emit! app-state (strm/->Register-Elem :history-box %))
              :key (:key props)
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

(defrecord History-Box-Append [msg-type text]
  ptk/UpdateEvent
  (update [_ state]
    (update-in state [:history-box :items] #(conj % text)))

  ptk/EffectEvent
  (effect [_ state stream]
    (let [history-box (get-in state [:elements :history-box])
          disp-text (case msg-type
                         :cmd (str prefix " " text)
                         text)]
      (.pushItem history-box disp-text)
      (.setScrollPerc history-box 100))))
