# lein-pjotest

A leiningen plugin for running test namespaces in parallel with junit output

## Installation

Use this for user-level plugins:

Put `[lein-pjotest "0.1.2-SNAPSHOT"]` into the `:plugins` vector of your
`:user` profile, or if you are on Leiningen 1.x do `lein plugin install
lein-pjotest 0.1.2-SNAPSHOT`.

Use this for project-level plugins:

Put `[lein-pjotest "0.1.2-SNAPSHOT"]` into the `:plugins` vector of your project.clj.

## Usage

    $ lein pjotest

Test selectors are also supported as per `lein test` but are specified using the `-selector` switch, e.g.

    $ lein pjotest -selector :integration

## License

Distributed under the Eclipse Public License, the same as Clojure.
