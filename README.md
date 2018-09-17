# lein-ring-extract-static

[![Clojars Project](https://img.shields.io/clojars/v/lein-ring-extract-static.svg)](https://clojars.org/lein-ring-extract-static)

A Leiningen plugin to extract static content from Ring endpoints.

## Usage

In `:plugins` in your `project.clj`:

```text
[lein-ring-extract-static "0.1.2"]
```

To extract static into `resources/public` and create `Dockerfile` for serving them:

```
$ lein ring-extract-static
```

To build Docker container from extracted resources:

```
$ lein ring-build-static
```

To build and run Docker container:

```
$ lein ring-build-static run
```

To build and push Docker container to repository:

```
$ lein ring-build-static push
```

## Configuration

You can add the following configuration options at the root of your `project.clj`:

```clojure
  :ring {:handler your-app.core/app
         :port 8080
         :static {"/" "index.html"
                  :image-name "example/example-static"}}
```

Defaults in `:ring`:

* `:handler` is your ring handler against which all content is extracted
* `:port` is the port of your web application (or 3000 by default)
* `:static` is a map of resources to be extracted from the handler with keys as endpoints and values as files to be written and Docker image name


## License

Copyright © 2018 Sergey Sobko

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
