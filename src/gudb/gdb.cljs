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
   [gudb.utils :refer [throttle jsx->clj]]
   [gudb.streams :refer [app-state]]
   [gudb.gdb-mi-parser :refer [gdb-output-parser gdb-output-transformer]]
   [gudb.bpts :as bpts]
   [gudb.source-widget :as sbox]
   [gudb.history-widget :as hbox]
   [gudb.progout-widget :as pbox])
   ; [gudb.gdb-msgs :refer [handle-gdb-result gdb-cmds-state]]
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:use-macros [shrimp-log.macros :only [trace debug spy info]]))


; Node builtins
(defonce process (js* "process"))

; Singletons
(defonce gdb-proc (atom nil))


(defrecord Handle-GDB-Result [r-type r-sub-type record parse-tree]
  ptk/WatchEvent
  (watch [_ state stream]
    (let [rcontents (first record)]
      (debug (str "RECORD: " record))
      (debug (str "RTYPE: " r-type))
      (debug (str "RSUBTYPE: " r-sub-type))
      (case r-type
        :ASYNC_RECORD (case r-sub-type
                        :BKPT_CREATED (rx/just (bpts/->Add rcontents))
                        :STOPPED (rx/just (sbox/->Set-Stopped-Info rcontents))
                        (do
                          (debug (str "UNCAUGHT ASYNC RECORD: " record))
                          (rx/empty)))

        :RESULT_RECORD (case r-sub-type
                        :BKPT_CREATED2 (rx/just (bpts/->Add rcontents))
                        :ERROR (rx/just (hbox/->History-Box-Append :error rcontents))
                        (do
                          (debug (str "UNCAUGHT RESULT RECORD: " record))
                          (rx/empty)))


        ;; :RESULT_RECORD (case (spy :trace (sp/select-one [sp/FIRST 1 sp/FIRST] record))
        ;;                   :ERROR (rx/just (hbox/->History-Box-Append :gdb (spy :trace (str (sp/select-one [sp/FIRST 1 1 1] record)))))
        ;;                   ; :DONE (do (debug (str "DONE RESULT_RECORD: " record) (rx/empty)))
        ;;                   ; :DONE (case (sp/select-one [sp/FIRST 1 1 1]
        ;;                   :DONE (case (sp/select-one [sp/FIRST 1 1 1 0] record)
        ;;                           ; TODO: Maybe check here that the last gdb command was
        ;;                           ; a break-insert
        ;;                           :BKPT_RESULT (rx/just (bpts/->Create (extract-bpt-result record)))
        ;;                           (rx/empty))
        ;;                   (do
        ;;                     (debug (str "UNCAUGHT RESULT RECORD: " record))
        ;;                     (rx/empty)))

        :STREAM_RECORD (rx/just (hbox/->History-Box-Append :gdb (spy :info rcontents)))
        :GDB_INTERNAL_RECORD (let [last-cmd (get-in state [:gdb :last-cmd])]
                               (case r-sub-type
                                 :MISC_INTERNAL_RECORD (cond
                                                        (= rcontents last-cmd) (rx/empty)
                                                        (= rcontents "The program is not being run.") (rx/empty)
                                                        :else (rx/just (hbox/->History-Box-Append :gdb rcontents)))))
                                                          ; :else (rx/empty)))
        :GDB_EXTRA (rx/empty)
        ; (trace (str "UNCAUGHT: " (sp/select-one [sp/FIRST 1] record)))
        (do
          (debug (str "UNCAUGHT OUTPUT: " parse-tree))
          (rx/empty))))))


(defrecord Output-Received-Raw [text]
  ptk/EffectEvent
  (effect [_ state stream]
    nil))

(defrecord Output-Received [text]
  ptk/WatchEvent
  (watch [_ state stream]
    (let [_ (debug "Raw Text: " text)
          parse-tree (gdb-output-parser text)
          transformed (gdb-output-transformer (spy :info parse-tree))
          record-sub-type (sp/select-one [0 1 0] parse-tree)
          record-type (sp/select-one [sp/FIRST sp/FIRST] parse-tree)
          ]
      (rx/just (->Handle-GDB-Result record-type record-sub-type transformed parse-tree)))))

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


(rx/subscribe (->>
               (ptk/input-stream app-state)
               (rx/filter #(instance? sbox/Send-Cmd %))
               (rx/transform xform-gdb-input))
              #(ptk/emit! app-state (->Send-Cmd' %1)))

(defrecord Send-Cmd' [cmd]
  ptk/UpdateEvent
  (update [_ state]
    (let []
      (debug "SENDING: " cmd)
      (assoc-in state [:gdb :last-cmd] cmd)))

  ptk/EffectEvent
  (effect [_ state stream]
    (.write (.-stdin @gdb-proc) (str cmd "\n"))))

(defrecord Init-GDB []
  ptk/WatchEvent
  (watch [_ state stream]
    (let [init-cmds (vector "set confirm off")]
      (->>
       init-cmds
       (rx/from-coll)
       (rx/map #(sbox/->Send-Cmd %))))))


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
        [gdb-args] [(clj->js ["-i=mi",  (str "-tty=" (.-_pty p-tty)), executable-path])]
        [gdb-proc'] [(spawn "gdb" gdb-args)]
        [callback] [(.. gdb-proc -stdout)]]
    (reset! gdb-proc gdb-proc')
    (.on (.-stdout gdb-proc') "data" #(ptk/emit! app-state (->Output-Received-Raw (str %))))
    (.on (.-stderr gdb-proc') "data" (fn [msg] (debug (str "ERROR FROM GDB:\n" msg))))
    (.on p-tty "data" #(ptk/emit! app-state (pbox/->Target-Output-Received-Raw %1 0)))
    (ptk/emit! app-state (->Init-GDB))))
