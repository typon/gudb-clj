(ns gudb.control
  (:require
    [potok.core :as ptk]
    [beicon.core :as rx]
    [gudb.utils :refer [jsx->clj file-exists?]]
    [gudb.streams :as strm]
    )
  (:use-macros [shrimp-log.macros :only [trace debug spy info]]))
