test *args:
    kmono run --run-in-order false --skip-unchanged true -M :test

build *args:
    clojure -T:build build {{ args }}

release *args:
    clojure -T:build release {{args}}
