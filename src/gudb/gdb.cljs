(ns gudb.gdb
  (:require
   ["child_process" :as child_process :refer [spawn]]
   ["node-pty" :as pty]
   [com.rpl.specter :as sp]
   [clojure.string :as string]
   [beicon.core :as rx]
   [potok.core :as ptk]
   [cljs.core.async :refer [chan <! >! timeout pub sub unsub unsub-all put! sliding-buffer]]
   [cljs.pprint :refer [pprint]]
   [gudb.utils :refer [throttle]]
   [gudb.streams :refer [app-state]]
   [gudb.gdb-mi-parser :refer [gdb-output-parser gdb-output-transformer]]
   [gudb.history-widget :as hbox]
   [gudb.progout-widget :as pbox])
   ; [gudb.gdb-msgs :refer [handle-gdb-result gdb-cmds-state]]
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:use-macros [shrimp-log.macros :only [trace debug spy info]]))


; Node builtins
(defonce process (js* "process"))

; Singletons
(defonce gdb-proc (atom nil))


(defrecord Handle-GDB-Result [record-type record parse-tree]
  ptk/WatchEvent
  (watch [_ state stream]
    (case (spy :trace record-type)
      :ASYNC_RECORD (do
                      (debug (str "UNCAUGHT RESULT: " record))
                      (rx/empty))

      :RESULT_RECORD (case (spy :trace (sp/select-one [sp/FIRST 1 sp/FIRST] record))
                        :ERROR (rx/just (hbox/->History-Box-Append :gdb (spy :trace (str (sp/select-one [sp/FIRST 1 1 1] record)))))
                        :DONE (rx/empty)
                        (do
                          (debug (str "UNCAUGHT RESULT: " record))
                          (rx/empty)))

      :STREAM_RECORD (rx/just (hbox/->History-Box-Append :gdb (str (sp/select-one [sp/FIRST 1] (spy :trace record)))))
      :GDB_INTERNAL_RECORD (let [[text] [(sp/select-one [sp/FIRST 1] (spy :trace record))]]
                            (if (and (not= text (get-in state [:gdb :last-cmd]) (not= text "The program is not being run.")))
                              (rx/just (hbox/->History-Box-Append :gdb text))
                              (rx/empty)))
      :GDB_EXTRA (rx/empty)
      ; (trace (str "UNCAUGHT: " (sp/select-one [sp/FIRST 1] record)))
      (do
        (debug (str "UNCAUGHT RESULT: " parse-tree))
        (rx/empty)))))


(defrecord Output-Received-Raw [text]
  ptk/EffectEvent
  (effect [_ state stream]
    nil))

(defrecord Output-Received [text]
  ptk/WatchEvent
  (watch [_ state stream]
    (let [parse-tree (gdb-output-parser (spy :trace text))
          transformed (gdb-output-transformer parse-tree)
          record-type (sp/select-one [sp/FIRST sp/FIRST] (spy :trace transformed))]
      (rx/just (->Handle-GDB-Result record-type transformed parse-tree)))))

(def xform-gdb-output
  (comp
   (mapcat (fn [msg] (string/split (:text (spy :trace msg)) "\n")))
   (remove string/blank?)))

(rx/subscribe (->>
               (ptk/input-stream app-state)
               (rx/filter #(instance? Output-Received-Raw %))
               (rx/transform xform-gdb-output))
              #(ptk/emit! app-state (->Output-Received %)))


(def xform-gdb-input
  (comp
   (map #(:cmd %))
   (map string/trim)
   (remove string/blank?)))

(defrecord Send-Cmd [cmd]
  ptk/EffectEvent
  (effect [_ state stream]
    nil))

(rx/subscribe (->>
               (ptk/input-stream app-state)
               (rx/filter #(instance? Send-Cmd %))
               (rx/transform xform-gdb-input))
              #(ptk/emit! app-state (->Send-Cmd' %1)))

(defrecord Send-Cmd' [cmd]
  ptk/UpdateEvent
  (update [_ state]
    (assoc-in state [:gdb :last-cmd] cmd))

  ptk/EffectEvent
  (effect [_ state stream]
    (.write (.-stdin @gdb-proc) (str cmd "\r\n"))))

(defrecord Init-GDB []
  ptk/WatchEvent
  (watch [_ state stream]
    (let [init-cmds (vector "set confirm off")]
      (->>
       init-cmds
       (rx/from-coll)
       (rx/map #(->Send-Cmd %))))))


(defn spawn-pseudo-tty []
  (.spawn pty "bash" ;; the FD of the pseudo-terminal is stored in the protected memeber variable '_pty'
          (clj->js [])
          (clj->js {"name" "xterm"
                    "cols" 80
                    "rows" 30
                    "cwd" (.. process -env -HOME)
                    "env" (.. process -env)})))

(defn load-gdb [executable-path]
  (let [[p-tty] [(spawn-pseudo-tty)]
        ; ; [gdb-args] [(clj->js ["-i=mi2",  (spy :info (str "-tty=" (.-_pty p-tty))), executable-path])]
        [gdb-args] [(clj->js ["-i=mi",  (str "-tty=" (.-_pty p-tty)), executable-path])]
        [gdb-proc'] [(spawn "gdb" gdb-args)]
        [callback] [(.. gdb-proc -stdout)]]
    (reset! gdb-proc gdb-proc')
    (.on (.-stdout gdb-proc') "data" #(ptk/emit! app-state (->Output-Received (str %))))
    (.on (.-stderr gdb-proc') "data" (fn [msg] (debug (str "ERROR FROM GDB:\n" msg))))
    ; (.on p-tty "data" (fn [text] (dispatch :target-output-received {:thread 0 :text text})))))
    (.on p-tty "data" #(ptk/emit! app-state (pbox/->Target-Output-Received-Raw %1 0)))
    (ptk/emit! app-state (->Init-GDB))))
