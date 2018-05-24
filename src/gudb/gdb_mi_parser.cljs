(ns gudb.gdb-mi-parser
  (:require
    [clojure.reader :refer [read-string]]
    [clojure.string :as str]
    [clojure.string :as string]
    [clojure.spec.alpha :as s]
    [cljs.tools.reader.edn :as edn]
    [goog.string :as gstring]
    [goog.string.format]
    [com.rpl.specter :as sp]
    [com.rpl.specter :as sp :refer-macros [select transform]]
    [instaparse.core :as insta :refer-macros [defparser]]
    [potok.core :as ptk]
    [gudb.utils :refer [unescape fix-escaping]]
    [gudb.streams :as strm :refer [app-state app-state-view app-stream]]
    [gudb.threads :as threads]
    [gudb.bpts :as bpts])
  (:use-macros [shrimp-log.macros :only [trace debug spy info]]))

(defparser gdb-output-parser "<S> = ASYNC_RECORD | STREAM_RECORD | RESULT_RECORD | GDB_INTERNAL_RECORD | GDB_EXTRA_RECORD
                 ASYNC_RECORD = (<eq> | <star>) (BKPT_CREATED / STOPPED / MISC_ASYNC_RECORD)
                 STREAM_RECORD = (<tilde> | <at>) MISC_STREAM_RECORD <not_q*>
                 RESULT_RECORD = <carat> STATE NEWLINE?
                 GDB_INTERNAL_RECORD = <amp> MISC_INTERNAL_RECORD <not_q*>
                 GDB_EXTRA_RECORD = <'(gdb)'> MISC_GDB_EXTRA_RECORD

                 MISC_GDB_EXTRA_RECORD = STR*

                 MISC_INTERNAL_RECORD = STR_IN_QUOTES
                 MISC_STREAM_RECORD = STR_IN_QUOTES

                 MISC_ASYNC_RECORD = STR
                 BKPT_CREATED = <'breakpoint-created'> <comma> BKPT
                 STOPPED = <'stopped'> <comma> STOPPED_FIELDS*

                 <STOPPED_FIELDS> = STOPPED_FIELD | (<comma> STOPPED_FIELD)*
                 STOPPED_FIELD = ('reason' <eq> STR_IN_QUOTES) |
                                 ('disp' <eq> STR_IN_QUOTES) |
                                 ('bkptno' <eq> NUM_IN_QUOTES) |
                                 ('frame' <eq> FRAME) |
                                 ('thread-id' <eq> NUM_IN_QUOTES) |
                                 ('stopped-threads' <eq> STR_IN_QUOTES)

                 FRAME = <lbrace> FRAME_FIELDS+ <rbrace>
                 <FRAME_FIELDS> = FRAME_FIELD | (<comma> FRAME_FIELD)
                 FRAME_FIELD = ('addr' <eq> <q> (HEX_NUMBER | '<PENDING>' | '<MULTIPLE') <q>) |
                               ('func' <eq> STR_IN_QUOTES) |
                               ('args' <eq> LIST) |
                               ('file' <eq> STR_IN_QUOTES) |
                               ('fullname' <eq> STR_IN_QUOTES) |
                               ('line' <eq> NUM_IN_QUOTES)


                 <STATE> = DONE | 'running' | 'connected' | ERROR | 'exit'
                 <DONE> = <'done'> (<','> RESULT)*
                 <RESULT> = BKPT_CREATED2
                 BKPT_CREATED2 = BKPT

                 BKPT = <'bkpt'> <eq> <lbrace> BKPT_FIELDS+ <rbrace>
                 <BKPT_FIELDS> = BKPT_FIELD | (<','> BKPT_FIELD)
                 BKPT_FIELD = ('number' <eq> NUM_IN_QUOTES) |
                                      ('type' <eq> <q> BKPT_TYPE <q>) |
                                      ('catch-type' <eq> <q> CATCH_TYPE <q>) |
                                      ('disp' <eq> <q> ('keep' | 'del') <q>) |
                                      ('enabled' <eq> <q> ('y' | 'n') <q>) |
                                      ('addr' <eq> <q> (HEX_NUMBER | '<PENDING>' | '<MULTIPLE') <q>) |
                                      ('func' <eq> STR_IN_QUOTES) |
                                      ('file' <eq> STR_IN_QUOTES) |
                                      ('filename' <eq> <q> ANYTHING <q>) |
                                      ('fullname' <eq> STR_IN_QUOTES ) |
                                      ('line' <eq> NUM_IN_QUOTES) |
                                      ('at' <eq> <q> ANYTHING <q>) |
                                      ('pending' <eq> <q> ANYTHING <q>) |
                                      ('evaluated-by' <eq> <q> ('host' | 'target') <q>) |
                                      ('thread' <eq> <q> ANYTHING <q>) |
                                      ('task' <eq> <q> ANYTHING <q>) |
                                      ('cond' <eq> <q> ANYTHING <q>) |
                                      ('ignore' <eq> NUM_IN_QUOTES) |
                                      ('enable' <eq> NUM_IN_QUOTES) |
                                      ('static-tracepoint-marker-string-id' <eq> <q> ANYTHING <q>) |
                                      ('mask' <eq> STR_IN_QUOTES) |
                                      ('pass' <eq> NUM_IN_QUOTES) |
                                      ('original-location' <eq> STR_IN_QUOTES ) |
                                      ('times' <eq> NUM_IN_QUOTES) |
                                      ('installed' <eq> <q> ('y' | 'n') <q>) |
                                      ('thread-groups' <eq> LIST) |
                                      ('what' <eq> <q> ANYTHING <q>)
                 <BKPT_TYPE> = 'breakpoint' | 'catchpoint'
                 <CATCH_TYPE> = 'unkown'
                 ERROR = <'error,msg='> STR_IN_QUOTES2
                 (* ERROR = <'error'> <',msg='> STR_IN_QUOTES *)

                 STR_IN_QUOTES2 = STR
                 STR_IN_QUOTES = #'\"([^\"]|\\\")*?\"'
                 <NUM_IN_QUOTES> = <q> #'[0-9]+' <q>
                 <LIST> = #'\\[[^\\[]*\\]'
                 <comma> = ','
                 <star> = '*'
                 <carat> = '^'
                 <tilde> = '~'
                 <amp> = '&'
                 <at> = '@'
                 <eq> = '='
                 <q> = '\"'
                 <not_q> = #'[^\"]'
                 <esc_q> = '\\\"'
                 <lbrace> = '{'
                 <rbrace> = '}'
                 <STR> = #'.*'
                 <ANYTHING> = #'(.|\\n)'
                 <NEWLINE> = '\n'
                 <HEX_NUMBER> = #'0x[0-9A-f]+'")

;; (defn chars-to-str [tag & cs]
;;   [tag (->> cs
;;             (apply str)
;;             fix-escaping
;;             unescape
;;             string/trim)])
; STR_IN_QUOTES = <q> (not_q | esc_q)* <q>

(defn chars-to-str [& cs]
  (->> cs
    (apply str)
    fix-escaping
    unescape
    string/trim))

(defn remove-quotes [s]
  (subs s 1 (dec (count s))))

(defn normalize-str [s]
  (->> s
       remove-quotes
       fix-escaping
       unescape
       string/trim))

(defn vectorize [key val]
  (vector (keyword key) val))

(def gdb-output-transformer
  (partial insta/transform
    {
      :STREAM_RECORD identity
      :GDB_INTERNAL_RECORD identity
      :ASYNC_RECORD identity
      :MISC_ASYNC_RECORD identity
      :MISC_STREAM_RECORD identity
      :MISC_INTERNAL_RECORD identity
      :RESULT_RECORD identity
      :ERROR identity

      ; :BKPT_CREATED #(ptk/emit! app-state (bpts/->Create %1))
      ; :BKPT_CREATED #(ptk/emit! app-state (bpts/->Create %1))
      :BKPT_CREATED identity
      :BKPT_CREATED2 identity
      :BKPT #(bpts/map->BPoint (into {} %&))
      :BKPT_FIELD vectorize

      :STOPPED #(threads/map->Stopped (into {} %&))
      :STOPPED_FIELD vectorize

      :FRAME #(threads/make-frame (into {} %&))
      ; :FRAME #(threads/map->Frame (into {} %&))
      :FRAME_FIELD vectorize


      :STR_IN_QUOTES normalize-str
      :STR_IN_QUOTES2 normalize-str

     
     }))
      ;; :RESULT_RECORD [:DONE
      ;;                 [:RESULT
      ;;                  [:BKPT_RESULT_FIELD ]]]


;; (defparser pp "S = '~' <q> (nq | eq)+ <q>
;;                A = 'hello'
;;               <nq> = #'[^\"]'
;;               <eq> = '\\\"'
;;               <q> = '\"'")
;; (defparser pp "S = H O
;;                H = C+
;;                <C> = #'(h|e|l)'
;;                O = 'oo'
;;               <nq> = #'[^\"]'
;;               <eq> = '\\\"'
;;               <q> = '\"'")

;; (def input "helloo")
;; (def tree (pp input))
;; tree

;; (insta/transform {:H str} tree)

;; (def pp
;;   (insta/parser
;;     "S = '^error,msg=' <q> 'Function ' <q> 'foo' <q> ' not defined.' <q>
;;      <q> = '\"'
;; "))
; (pp "^error,msg=\"Function \"foo\" not defined.\"")


