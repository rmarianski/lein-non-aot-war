# lein-non-aot-war

A Leiningen plugin to generate war files that use a non aot compiled
servlet.

## Usage

Put `[lein-non-aot-war "0.1.0"]` into the `:plugins` vector of your
`:user` profile, or if you are on Leiningen 1.x do `lein plugin install
lein-non-aot-war 0.1.0`.

Then, you will need to configure your `project.clj` file. At a
minimum, there must be a `:ring` map with a ring `:handler` function
and a `:resource-scripts` map:

    :ring {:handler hello-world.core/handler
           :resource-scripts [hello-world/core.clj]}

The following options can be used in the `:ring` map:

* `:resource-scripts`
  A sequence of scripts that will each be loaded with a call to
  `RT.loadResourceScript`. It's important that each separate clj file
  that is used for your init, destroy, and handler functions is listed
  here.

* `:init` -
  A function to be called once before your handler starts. It should
  take no arguments.

* `:destroy` -
  A function called before your handler exits or is unloaded. It
  should take no arguments.

* `:handler` -
  The ring handler function. This should be a function that takes a
  request map and returns a response map.

To build an uberwar from the command line:

    $ lein non-aot-war

You can be explicit:

    $ lein non-aot-war uberwar warfile.war

And generate a skinny war:

    $ lein non-aot-war war warfile.war

## Approach

The main reasoning behind this approach is to eliminate the long aot
compile times when creating a servlet using gen-class. This is done by
generating a java servlet implementation that looks up the ring
handler at runtime with `RT.var`.

## License

Copyright © 2012 Robert Marianski

Distributed under the Eclipse Public License, the same as Clojure.
