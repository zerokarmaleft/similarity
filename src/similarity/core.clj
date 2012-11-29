(ns similarity.core
  (:use     [cascalog.api]
            [cascalog.more-taps :only (hfs-delimited)])
  (:require [clojure.string :as str]
            [cascalog.ops :as c]
            [cascalog.conf :as conf]
            [cascalog.vars :as v])
  (:import  [com.google.common.hash Hashing])
  (:gen-class))

(defn shingles
  [k s]
  "Returns the set of k-shingles that appear one or more times in string s."
  (->> (str/replace s #"\s+" " ")
       (partition k 1 s)
       (map #(apply str %))
       set))

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

(defn -main [in out doc-id threshold k n & args]
  (let [docs      (hfs-delimited in :skip-header? true)
        threshold (Double/parseDouble threshold)
        k         (Integer/parseInt k)
        n         (Integer/parseInt n)]
    (?- (hfs-delimited out)
        (similarity docs doc-id threshold k n))))
