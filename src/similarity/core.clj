(ns similarity.core
  (:use     [cascalog.api]
            [cascalog.more-taps :only (hfs-delimited)])
  (:require [clojure.string :as str]
            [cascalog.ops :as c]
            [cascalog.conf :as conf]
            [cascalog.vars :as v])
  (:import  [com.google.common.hash Hashing])
  (:gen-class))

(defmapcatop [extract-shingles [k]]
  [s]
  "Returns the set of k-shingles that appear one or more times in string s."
  (->> (str/replace s #"\s+" " ")
       (partition k 1 s)
       (map #(apply str %))
       set))

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

(defn find-by-id
  [doc-sigs doc-id]
  (<- [?doc-sig]
      (doc-sigs ?doc-id ?doc-sig)
      (= ?doc-id doc-id)))

(defn simvector
  [v1 v2]
  (let [counts (group-by #(= (first %) (second %))
                         (partition 2 2 (interleave v1 v2)))]
    (if (empty? counts)
      0
      (double (/ (count (counts true))
                 (+ (count (counts true))
                    (count (counts false))))))))

(defmapcatop [bands [b]] [minhash-sig]
  (let [n (count minhash-sig)
        r (/ n b)]
    [[(partition 2 2 (interleave (range r)
                                 (partition r r minhash-sig)))]]))

(defn multibandhash
  [bands]
  [(map #(.asInt (Hashing/combineOrdered %))
        (map (fn [band]
               (let [[seed row] band
                     hash-fn    (Hashing/murmur3_32 seed)]
                 (map (fn [hash-code]
                        (.hashLong hash-fn hash-code))
                      row)))
             bands))])

(defn lsh-sigs [doc-sigs b]
  (<- [?doc-id ?lsh-sig]
      (doc-sigs ?doc-id ?minhash-sig)
      (bands b ?minhash-sig :> ?bands)
      (multibandhash ?bands :> ?lsh-sig)))

(deffilterop candidate-pair? [v1 v2]
  (some true? (map #(= %1 %2) v1 v2)))

(defn candidates [doc-sigs lsh-sigs doc-id]
  (let [[[[target-sig]]] (??- (find-by-id lsh-sigs doc-id))]
    (<- [?doc-id ?minhash-sig]
        (doc-sigs ?doc-id ?minhash-sig)
        (lsh-sigs ?doc-id ?lsh-sig)
        (candidate-pair? target-sig ?lsh-sig))))

(defn minhash-similarity [docs doc-id threshold k n]
  (let [doc-sigs         (minhash-sigs docs k n)
        [[[target-sig]]] (??- (find-by-id doc-sigs doc-id))]
    (<- [?doc-id ?similarity]
        (doc-sigs ?doc-id ?minhash-sig)
        ((c/negate #'=) ?doc-id doc-id)
        (simvector target-sig ?minhash-sig :> ?similarity)
        (> ?similarity threshold))))

(defn similarity [docs doc-id k n b]
  (let [doc-sigs         (minhash-sigs docs k n)
        candidates       (candidates doc-sigs (lsh-sigs doc-sigs b) doc-id)
        [[[target-sig]]] (??- (find-by-id doc-sigs doc-id))]
    (<- [?doc-id ?similarity]
        (candidates ?doc-id ?minhash-sig)
        ((c/negate #'=) ?doc-id doc-id)
        (simvector target-sig ?minhash-sig :> ?similarity))))

(defn -main [in out doc-id k n b & args]
  (let [docs      (hfs-delimited in :skip-header? false)
        ;; threshold (Double/parseDouble threshold)
        k         (Integer/parseInt k)
        n         (Integer/parseInt n)
        b         (Integer/parseInt b)]
    (?- (hfs-delimited out)
        (similarity docs doc-id k n b))))
