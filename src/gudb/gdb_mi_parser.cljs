(ns gudb.gdb-mi-parser
  (:require [clojure.reader :refer [read-string]])
  (:require [clojure.string :as string])
  (:require [clojure.spec.alpha :as s])
  (:require [cljs.tools.reader.edn :as edn])
  (:require [goog.string :as gstring] [goog.string.format])
  (:require [com.rpl.specter :as sp])
  (:require [com.rpl.specter :as sp :refer-macros [select transform]])
  (:require [instaparse.core :as insta :refer-macros [defparser]])
  (:require [gudb.utils :refer [unescape fix-newline-escaping]])
  )

;; (def possible-states #{"done", "running", "connected", "error", "exit"})
;; (s/def ::state (s/cat :1 #{\d}
;;                       :2 #{\o}
;;                       :3 #{\n}
;;                       :4 #{\e}))

;; (s/def ::state (s/and (s/conformer (partial apply str)) #{"done" "error"}))

;; (s/conform ::state [\d \o \n \e])
;; (s/conform ::state "done")

;; (s/conform ::state (seq "^error"))
;; (s/explain-data ::state (seq "^error"))

;; (s/def ::result-record
;;   (s/and string?
;;          (s/conformer seq)
;;          (s/cat :carat #{\^}
;;                 :state ::state)))
;; (s/def ::result-record
;;   (s/cat :carat #{\^}
;;          :state possible-states))

;; (s/def ::output-record
;;   (s/alt :result-record ::result-record
;;          :stream-record ::stream-record
;;          :async-record ::async-record))

;; (def result-record-parse
;;   (insta/parser "S = CARAT STATE
;;                  CARAT = '^'
;;                  STATE = DONE | 'running' | 'connected' | 'error' | 'exit'
;;                  DONE = 'done' (',' RESULT)*
;;                  RESULT = BKPT_RESULT
;;                  BKPT_RESULT = 'bkpt={' BKPT_RESULT_FIELDS+ '}'
;;                  BKPT_RESULT_FIELDS = 'number' eq q NUMBER q |
;;                                       'type' eq q BKPT_TYPE q |
;;                                       'disp eq q 'keep' q;
;;                  BKPT_TYPE = 'breakpoint'
;;                  eq = '='
;;                  q = '\"'
;;                  NUMBER = #'[0-9]+'"))

;; (def result-record-parser
;;   (insta/parser "S = (<CARAT> | <TILDE>) STATE
;;                  <CARAT> = '^'
;;                  <TILDE> = '~'
;;                  STATE = DONE | 'running' | 'connected' | 'error' | 'exit'
;;                  DONE = <'done'> (<','> RESULT)*
;;                  RESULT = BKPT_RESULT
;;                  BKPT_RESULT = <'bkpt'> <eq> <lbrace> BKPT_RESULT_FIELDS+ <rbrace>
;;                  <BKPT_RESULT_FIELDS> = BKPT_RESULT_FIELD | (<','> BKPT_RESULT_FIELD)
;;                  BKPT_RESULT_FIELD = ('number' <eq> <q> NUMBER <q>) |
;;                                       ('type' <eq> <q> BKPT_TYPE <q>) |
;;                                       ('disp' <eq> <q> 'keep' <q>)
;;                  <BKPT_TYPE> = 'breakpoint'
;;                  <eq> = '='
;;                  <q> = '\"'
;;                  <lbrace> = '{'
;;                  <rbrace> = '}'
;;                  <NUMBER> = #'[0-9]+'"))

(defparser gdb-output-parser "<S> = ASYNC_RECORD | STREAM_RECORD | RESULT_RECORD | GDB_INTERNAL_RECORD | GDB_EXTRA
                 ASYNC_RECORD = (<eq> | <star>) ANYTHING+
                 STREAM_RECORD = (<tilde> | <at>) <q> (not_q | esc_q)+ <q> <not_q*>
                 RESULT_RECORD = <carat> STATE NEWLINE?
                 GDB_INTERNAL_RECORD = <amp> <q> (not_q | esc_q)+ <q> <not_q*>
                 GDB_EXTRA = '(gdb)' ANYTHING*
                 <STATE> = DONE | 'running' | 'connected' | ERROR | 'exit'
                 DONE = <'done'> (<','> RESULT)*
                 RESULT = BKPT_RESULT
                 BKPT_RESULT = <'bkpt'> <eq> <lbrace> BKPT_RESULT_FIELDS+ <rbrace>
                 <BKPT_RESULT_FIELDS> = BKPT_RESULT_FIELD | (<','> BKPT_RESULT_FIELD)
                 BKPT_RESULT_FIELD = ('number' <eq> <q> NUMBER <q>) |
                                      ('type' <eq> <q> BKPT_TYPE <q>) |
                                      ('catch-type' <eq> <q> CATCH_TYPE <q>) |
                                      ('disp' <eq> <q> ('keep' | 'del') <q>) |
                                      ('enabled' <eq> <q> ('y' | 'n') <q>) |
                                      ('addr' <eq> <q> (HEX_NUMBER | '<PENDING>' | '<MULTIPLE') <q>) |
                                      ('func' <eq> <q> ANYTHING <q>) |
                                      ('filename' <eq> <q> ANYTHING <q>) |
                                      ('fullname' <eq> <q> ANYTHING <q>) |
                                      ('line' <eq> <q> NUMBER <q>) |
                                      ('at' <eq> <q> ANYTHING <q>) |
                                      ('pending' <eq> <q> ANYTHING <q>) |
                                      ('evaluated-by' <eq> <q> ('host' | 'target') <q>) |
                                      ('thread' <eq> <q> ANYTHING <q>) |
                                      ('task' <eq> <q> ANYTHING <q>) |
                                      ('cond' <eq> <q> ANYTHING <q>) |
                                      ('ignore' <eq> <q> NUMBER <q>) |
                                      ('enable' <eq> <q> NUMBER <q>) |
                                      ('static-tracepoint-marker-string-id' <eq> <q> ANYTHING <q>) |
                                      ('mask' <eq> <q> ANYTHING <q>) |
                                      ('pass' <eq> <q> NUMBER <q>) |
                                      ('original-location' <eq> <q> ANYTHING <q>) |
                                      ('times' <eq> <q> NUMBER <q>) |
                                      ('installed' <eq> <q> ('y' | 'n') <q>) |
                                      ('what' <eq> <q> ANYTHING <q>)
                 <BKPT_TYPE> = 'breakpoint' | 'catchpoint'
                 <CATCH_TYPE> = 'unkown'
                 ERROR = <'error'> <',msg='> TEXT_IN_QUOTES
                 TEXT_IN_QUOTES = <q> (not_q | esc_q)+ <q>
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
                 <ANYTHING> = #'(.|\\n)'
                 <NEWLINE> = '\n'
                 <NUMBER> = #'[0-9]+'
                 <HEX_NUMBER> = #'0x[0-9]+'")

(defn chars-to-str [tag & cs] [tag (->> cs (apply str) fix-newline-escaping unescape string/trim)])

(def transformer (partial insta/transform {:STREAM_RECORD (partial chars-to-str :STREAM_RECORD)
                                           :ASYNC_RECORD (partial chars-to-str :ASYNC_RECORD)
                                           :GDB_INTERNAL_RECORD (partial chars-to-str :GDB_INTERNAL_RECORD)
                                           ; :RESULT_RECORD [:ERROR [:TEXT_IN_QUOTES (fn [& c] [:TEXT_IN_QUOTES (apply str c)])]]
                                           :TEXT_IN_QUOTES (partial chars-to-str :TEXT_IN_QUOTES)
                                          }))


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
