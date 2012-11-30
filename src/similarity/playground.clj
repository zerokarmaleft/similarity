(ns similarity.playground
  (:use     [similarity.core])
  (:require [cascalog.ops :as c]
            [cascalog.conf :as conf]
            [cascalog.vars :as v]))

(use 'cascalog.playground)

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

(comment
  (?<- (stdout)
       [?doc-id ?lshash-sig]
       ((minhash-sigs documents 4 100) ?doc-id ?minhash-sig)
       (bands 20 ?minhash-sig :> ?bands)
       (multibandhash ?bands :> ?lshash-sig)))
