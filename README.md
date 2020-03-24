# REPL-REPL - Web UI

<img align="left" src="https://github.com/raymcdermott/repl-tongue/blob/master/resources/public/images/repl-logo-yellow-transparent.png" width="100">

Web UI part of the [`REPL-REPL` shared REPL](https://github.com/raymcdermott/reptile-body). You use it to enter forms and see the results of evaluation. You can also watch your friends editing in real-time and share their REPL evaluations.

The technical implementation is a [re-frame](https://github.com/Day8/re-frame) based Single Page Application (SPA).

## Features

### REPL-REPL connectivity
- [X] Multi-user REPL connectivity
- [X] Authenticated server access
- [X] Real-time keystrokes from all connected users

### Other REPL users
- [X] Watch / hide visibility control

### Clojure code support
- [X] Parinfer integration
- [X] Colorised edits / output
- [X] Show matching / balancing parens
- [X] Code completion / suggestions 
- [X] Expose function documentation

### Clojure evaluation
- [X] Shared REPL state
- [X] Shared, accessible history
- [X] Friendly exceptions 
- [ ] Incremental feedback on long running REPL evaluations
- [ ] Cancel long running REPL evaluations

### Deps.edn
- [X] Add a library on demand (Maven & Git SHAs)

### Security
- [X] Secure REPtiLe server connection

### REPtiLe developer features
- [X] Live reloadable client code
- [X] Live reloadable server code

### User features under consideration post 1.0
- [ ] Choice of editor key mappings
- [ ] Automatic editor visibility based on activity
- [ ] OpenID Connect Services eg GitHub / Slack

## Development

### Running with a REPL:

```
clojure -A:fig:repl:body:body-path
```

### Run application on the command line:

```
clojure -A:fig:dev:body:body-path
```

Figwheel will automatically push cljs changes to the browser.

Wait a bit, then browse to [http://localhost:9500](http://localhost:9500).

The REPtiLe server will be started in the background so there is no need to start a separate server in the development process.

## Configuration

The location of the back-end server is configurable.

The name of the server can be set in the `min.cljs.edn` by changing `repl.repl.ziggy.config/TAIL_SERVER`

Please also checkout the [REPtiLE configuration](https://github.com/raymcdermott/reptile-tail) options

## Local deps.edn configuration

Add some entries to the `aliases` section of `~/.clojure/deps.edn` similar to these:

```clojure
:aliases {
  :body/local {:override-deps {repl-body {:local/root "/Users/your-name/dev/repl-house/body"}}}
  :body/path {:extra-paths ["/Users/your-name/dev/repl-house/body/dev"]}
}
```

And then run it from the command line:

```
clj -A:fig:dev:body/local:body/path
```

## Production Build

```
clojure -A:fig:min
```

#### AWS S3 hosting

AWS S3 buckets can be configured to host web sites and thus be used to serve the UI.

The `s3-publish.sh` script is provided for building the code and syncing with an S3 bucket. 

The script will also invalidate any configured CloudFront distribution.

## [License](LICENSE)

Copyright © 2019 Ray McDermott

[Distributed under the GPL v3](LICENSE)