### What is this

See this [ClojureVerse thread](https://clojureverse.org/t/creating-a-central-documentation-repository-website-codox-complications/1287/)
about a central documentation hub for Clojure similar to Elixir's https://hexdocs.pm/.

If you want to know more, open an issue or contact me elsewhere.

## Design (wip)

- build an ecosystem-encompassing Grimoire store
- build various tools around that store, ideas:
  - static html API docs + guides
  - single page app to browse documentation
  - API
  - machine readable doc-bundles
  - [Docsets](https://kapeli.com/docsets) for documentation browsing apps
- build tools to raise overall quality of documentation (Github bots, Doc templates, etc)

## Progress

<!-- I'm using parts of Boot for the first prototypes of this,  -->
<!-- it's not set in stone that it uses Boot in the end. -->

#### HOSTING

- [x] setup s3 bucket / static website using [Confetti](https://github.com/confetti-clj/confetti)
- [x] sync files generated by `build-docs` to S3

#### SERVICE + ISOLATION

- [ ] create Docker setup to run `build-docs`
- [ ] create an API server that runs the Docker setup
- [ ] copy created files to host, upload to S3 (so that AWS keys are not exposed in Docker env)

#### API DOCS

- [ ] derive source-uri
  - probably needs parsing of project.clj or build.boot or perhaps we can derive source locations by overlaying jar contents
  - :face_with_head_bandage: from quick testing it seems that getting this to work with codox could be hard
  - Grimoire data contains source so perhaps source URI becomes less important
  - still would be nice to jump to the entire source file
- [x] give grimoire and related tools a go
  - Grimoire notes / questions
    - Building docs from jar vs src — what are the tradeoffs?
    - Are there any fundamental issues with grimoire that could become problematic later?
    - How do you build docs for a [prj v] when another version is already on the classpath? (e.g. grimoire itself)
- [ ] Build Codox style index page based on grimoire info
- [ ] throw an error or something if grimoire does not find *anything*
- [ ] think about how different platforms can be combined in API docs
- [ ] build static site generator or SPA that runs on top of grimoire data

#### GITHUB + NON-API DOCS

- [x] read Github URL from pom.xml or Clojars
- [x] clone repo, copy `doc` directory, provide to codox
- [x] try to check out Git repo at specific tag for `doc/` stuff; warn if no tags
- [ ] figure out what other metadata should be imported
- [ ] Figure out how to deal with changes between tagged releases
  - We probably don't want to pick up API changes since people commonly push development progress straight to `master`
  - Picking up changes to `doc/` would be nice and the above problem probably applies a little less
  - When updating `doc/` triggered by Github commits we need to derive the correct version
    - Take latest "RELEASE" version on Clojars?
    - Take version specified by most recent tag?

#### LONG SHOTS

- [ ] think about discovery of projects with same group-id
- [ ] think about how something like dynadoc (interactive docs) could be integrated
- [ ] think about how stack style REPL examples could be integrated

#### BOT

- [ ] notify people that there are api docs available for a jar they just published
- [ ] suggest to add some plain text documentation/guides + provide templates
- [ ]
