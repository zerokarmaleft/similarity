(ns similarity.playground
  (:use     [similarity.core])
  (:require [cascalog.ops :as c]
            [cascalog.conf :as conf]
            [cascalog.util :as u]
            [cascalog.vars :as v]))

(use 'cascalog.playground)

(def documents
  [["docA" "A pangram is a phrase that contains all of the letters of the English alphabet. The quick brown fox jumps over the lazy dog. This is a pangram."]
   ["docB" "A pangram is a phrase that contains all of the letters of the English alphabet. The quick white wolf eats the lazy sheep."]
   ["docC" "A pangram is a phrase that contains all of the letters of the English alphabet. The slow brown fox jumps into the quizzical dog."]
   ["docD" "A pangram is a phrase that contains all of the letters of the English alphabet. The slow white wolf lays next to the lazy dog."]
   ["docE" "A pangram is a phrase that contains all of the letters of the English alphabet. The quick brown fox jumps over the lazy cat."]
   ["docF" "A pangram is a phrase that contains all of the letters of the English alphabet. The quick brown fox jumps over the lazy dog. This is a pangram.."]])

(def D
  [["S1" "ad"]
   ["S2" "c"]
   ["S3" "bde"]
   ["S4" "acd"]])

(comment
  (?- (stdout) (minhash-sigs D 1 2))
  (?- (stdout) (minhash-sigs D 1 8)))

(comment
  (?- (stdout) (minhash-similarity documents "docA" 0.6 4 1000)))

(comment
  (?- (stdout)
      (lsh-sigs (minhash-sigs documents 4 1000) 20)))

(comment
  (let [doc-sigs (minhash-sigs documents 4 1000)]
    (?- (stdout)
        (candidates doc-sigs (lsh-sigs doc-sigs 20) "docA"))))

(comment
  (?- (stdout) (minhash-similarity documents "docA" 0.5 4 1000))
  (?- (stdout) (similarity documents "docA" 0.5 4 1000 20)))
