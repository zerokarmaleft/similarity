# similarity

Finding similar documents with minhashing/locality-sensitive hashing.

## Usage

[Install Leiningen](https://github.com/technomancy/leiningen#installation).

Clone the repository and download dependencies.
```
	$ git clone https://github.com/zerokarmaleft/similarity.git
	$ cd similarity
	$ lein deps
```

The playground has some dummy in-memory datasets for exploring in the REPL.
```
        $ lein repl
```
At the REPL prompt:
```
        similarity.core=> (use 'similarity.playground)
        similarity.core=> (in-ns 'similarity.playground)
        similarity.playground=> (bootstrap)
```
Some example queries:
```
        similarity.playground=> (?- (stdout) (similarity D "S1" 0.10 1 1000))
        similarity.playground=> (?- (stdout) (similarity documents "docA" 0.6 4 1000))
```

To run with Hadoop locally, build an uberjar (which packages the job, and all dependencies, including Clojure into a single JAR).
```
        $ lein uberjar
        $ hadoop jar target/similarity.jar <input path> <output path> <document index> <similarity threshold> <size of k-shingles> <number of hash functions>
```

## License

Copyright © 2012 Edward Cho.

Distributed under the Eclipse Public License, the same as Clojure.
