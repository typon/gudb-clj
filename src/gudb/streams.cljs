(ns gudb.streams
  (:require [cljs.core.async :refer [chan <! >! put!]]
            [cljs.cache :as cache]
            [potok.core :as ptk]
            [beicon.core :as rx])
(:require-macros [cljs.core.async.macros :refer [go go-loop]])

  (:use-macros [shrimp-log.macros :only [trace debug spy info]]))

(def app-state (ptk/store {:state {:prefix ">>>"
                          :elements {}
                          :source-box {:line-els (cache/lru-cache-factory {} :threshold 50)
                                       :lineno-els (cache/lru-cache-factory {} :threshold 50)
                                       :source-lines [] :current-line 0
                                       :vert-padding 2}
                          :source-box-value {}
                          :input-box-value ""
                          :history-box-value []
                          :prog-output-box-value "" ; stores the last log
                          :auto-box {:value [] :curr-selected-index 0 :interactive false}
                          :changed false}}))
(defonce app-state-view (rx/to-atom app-state))
