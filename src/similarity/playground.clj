(ns similarity.playground
  (:use [similarity.core :only [shingles]]
        [cascalog.api]
        [cascalog.playground :only [bootstrap bootstrap-emacs]]
        [cascalog.more-taps :only (hfs-delimited)])
  (:require [clojure.string :as str]
            [cascalog.ops :as c]
            [cascalog.conf :as conf]
            [cascalog.vars :as v])
  (:import [java.io PrintStream]
           [cascalog WriterOutputStream]
           [org.apache.log4j Logger WriterAppender SimpleLayout]
           [com.google.common.hash Hashing]))

(def documents
  [["docA" "A pangram is a phrase that contains all of the letters of the English alphabet. The quick brown fox jumps over the lazy dog. This is a pangram."]
   ["docB" "A pangram is a phrase that contains all of the letters of the English alphabet. The quick white wolf eats the lazy sheep."]
   ["docC" "A pangram is a phrase that contains all of the letters of the English alphabet. The slow brown fox jumps into the quizzical dog."]
   ["docD" "A pangram is a phrase that contains all of the letters of the English alphabet. The slow white wolf lays next to the lazy dog."]
   ["docE" "A pangram is a phrase that contains all of the letters of the English alphabet. The quick brown fox jumps over the lazy cat."]])

(def D
  [["S1" "ad"]
   ["S2" "c"]
   ["S3" "bde"]
   ["S4" "acd"]])

(defmapcatop [extract-shingles [k]] [line] (shingles k line))

(defmapop [multihash [n]] [shingle]
  [(map (fn [seed]
          (.asInt (.hashString (Hashing/murmur3_32 seed) shingle)))
        (range n))])

(defn merge-vectors
  [v1 v2]
  (map #(map min %1 %2) v1 v2))

(defbufferop minhash-sig
  [hash-sigs]
  [(reduce merge-vectors hash-sigs)])

(defn minhash-sigs
  [docs k n]
  (<- [?doc-id ?minhash-sig]
      (docs :> ?doc-id ?line)
      (extract-shingles k ?line :> ?shingle)
      (multihash n ?shingle :> ?hash-sig)
      (minhash-sig ?hash-sig :> ?minhash-sig)))

(defn simvector
  [v1 v2]
  (let [counts (group-by #(= (first %) (second %))
                         (partition 2 2 (interleave v1 v2)))]
    (double (/ (count (counts true))
               (+ (count (counts true))
                  (count (counts false)))))))

(defn similarity [docs doc-id threshold k n]
  (let [sigs       (minhash-sigs docs k n)
        target-sig (first (first (??<- [?minhash-sig]
                                       (sigs ?doc-id ?minhash-sig)
                                       (= ?doc-id doc-id))))]
    (<- [?doc-id ?similarity]
        (sigs ?doc-id ?minhash-sig)
        ((c/negate #'=) ?doc-id doc-id)
        (simvector target-sig ?minhash-sig :> ?similarity)
        (> ?similarity threshold))))

(comment
  (??- (minhash-sigs D 1 2)
       (minhash-sigs D 1 8)))

(comment
  (?- (stdout) (similarity D "S1" 0.10 1 2))
  (?- (stdout) (similarity D "S1" 0.10 1 8))
  (?- (stdout) (similarity D "S1" 0.10 1 1000)))

(comment
  (?- (stdout) (similarity documents "docA" 0.6 4 2))
  (?- (stdout) (similarity documents "docA" 0.6 4 8))
  (?- (stdout) (similarity documents "docA" 0.6 4 1000)))
