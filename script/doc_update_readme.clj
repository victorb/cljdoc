#!/usr/bin/env bb

(ns doc-update-readme
  "Script to update README.adoc to credit people who have contributed
  Run manually as needed."
  (:require [babashka.fs :as fs]
            [babashka.pods :as pods]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [hiccup.util :as hu]
            [hiccup2.core :as h]
            [lread.status-line :as status]))

;; use etaoin pod to capture html renderings to pngs
(pods/load-pod 'org.babashka/etaoin "0.1.0")
(require '[pod.babashka.etaoin :as etaoin])

(def contributions-lookup
  {:code ["💻" "code"]
   :doc ["📖" "doc"]
   :design ["⚖️" "design"]   ;; upfront design/collab/hamocking
   :issue ["💡" "issue"]     ;; raise an issue
   :sponsor ["💵" "sponsor"]
   :ops ["🔧️" "ops"]         ;; keeps the servers running
   :infra ["☁️" "infra"]
   :support ["💬" "support"] ;; answers questions on Slack or GitHub
   :review ["👀" "review"]}) ;; reviews work/PR

(defn- generate-asciidoc [contributors {:keys [images-dir image-width]}]
  (str ":imagesdir: " images-dir "\n"
       "[]\n"
       "--\n"
       (apply str (for [{:keys [github-id]} contributors]
                    (str "image:" github-id ".png[" github-id ",width=" image-width ",link=\"https://github.com/" github-id "\"]\n")))
       "--\n"))

(defn- update-readme-text [old-text marker-id new-content]
  (let [marker (str "// AUTO-GENERATED:" marker-id)
        marker-start (str marker "-START")
        marker-end (str marker "-END")]
    (string/replace-first old-text
                          (re-pattern (str "(?s)" marker-start ".*" marker-end))
                          (str marker-start "\n" (string/trim new-content) "\n" marker-end))))

(defn- update-readme-contributor-count [old-text new-count]
  (string/replace-first old-text
                        #"(?m)(^:num-contributors: +)(\d+)"
                        (str "$1" new-count)))

(defn update-readme-file! [people readme-filename image-info]
  (let [contributors (-> people :contributors)
        num-contributors (-> contributors count)]
    (status/line :head "updating README with %d contributors" num-contributors)
    (let [old-text (slurp readme-filename)
          new-text (-> old-text
                       (update-readme-text "CONTRIBUTORS" (generate-asciidoc contributors image-info))
                       (update-readme-contributor-count num-contributors))]
      (if (not (= old-text new-text))
        (do
          (spit readme-filename new-text)
          (status/line :detail (str  readme-filename " text updated")))
        (status/line :detail (str  readme-filename " text unchanged"))))))

(defn generate-contributor-html
  "Some shenanigins herein.
  Needed (?) to calc wrapper div so that when grabbing div would also grab shadowbox."
  [github-id contributions {:keys [image-width]}]
  (let [card-margin-right 5
        card-padding 7
        card-shadow-h-offset 4
        card-border 1
        card-width (- image-width (+ card-margin-right card-padding (* 2 card-shadow-h-offset) card-border))
        avatar-size 110
        contrib-font-size (if (< (count contributions) 5) "1.2em" "0.8em")]
    (str
     (h/html
      [:head
       [:link {:href "https://fonts.googleapis.com", :rel "preconnect"}]
       [:link {:href "https://fonts.gstatic.com", :rel "preconnect" :crossorigin "crossorigin"}]
       [:link {:type "text/css", :href "https://fonts.googleapis.com/css2?family=Fira+Code&display=swap" :rel "stylesheet"}]
       [:style
        (hu/raw-string
         (str
          "* {-webkit-font-smoothing: antialiased; -moz-osx-font-smoothing: grayscale;}\n"
          "body {font-family: 'Fira Code', monospace; margin: 0;}\n"
          (format ".wrapper {overflow:hidden; min-width: %dpx; max-width: %dpx;}" image-width image-width) "\n"
          (format ".card {float: left; border-radius: 5px;
                          border: %dpx solid #ccc;
                          padding: %dpx;
                          margin: 0 5px %dpx 0;
                          box-shadow: %dpx 4px 3px grey;
                          background-color: #f4f4f4;
                          width: %dpx;}"
                  card-border card-padding card-margin-right card-shadow-h-offset card-width) "\n"
          (format ".avatar {float: left; height: %dpx; border-radius: 5px; padding: 0; margin-right: 10px;}"
                  avatar-size) "\n"
          ".image {margin: 2px;}\n"
          (format ".contribs {padding-top: 3px; margin-left: 5px; line-height: 1.4; max-height: %dpx; overflow: hidden;}"
                  avatar-size) "\n"
          (format ".contrib {display:inline-block;font-size: %s;white-space: nowrap;margin: 0;margin-right: 0.8em;}\n"
                  contrib-font-size)
          ".symbol {margin-right: 0.3em;}\n"
          ".text {}\n"
          ".name {font-size: 1.3em; margin: 0; clear:both;}\n"))]]
      [:div.wrapper
       [:div.card
        [:div
         [:img.avatar {:src (str "https://github.com/" github-id ".png")}]
         [:div.contribs
          (doall
           (for [c (sort contributions)]
             (let [[c-sym c-text] (c contributions-lookup)]
               [:span.contrib
                [:span.symbol c-sym]
                [:span.text c-text]])))]
         [:p.name (str "@" github-id)]]]]))))

(defn- move-path [source target]
  (when (fs/exists? target)
    (fs/delete-tree target))
  (fs/create-dirs (fs/parent target))
  (fs/move source target {:replace-existing true :atomic-move true}))

(defn- generate-image! [driver target-dir github-id contributions opts]
  (let [html-file (fs/file target-dir (str github-id ".html"))]
    (try
      (spit html-file (generate-contributor-html github-id contributions opts))
      (etaoin/go driver (str "file://" html-file))
      (etaoin/screenshot-element driver
                                 {:tag :div :class :wrapper}
                                 (str target-dir "/" github-id ".png"))
      (finally
        ;; comment this out to leave generated .html around for tweaking/debugging
        (when (fs/exists? html-file)
          (fs/delete html-file))))))

(defn- generate-contributor-images! [contributors image-opts]
  (status/line :head "generating contributor images")
  (let [driver (etaoin/chrome {:headless true})
        work-dir (fs/create-temp-dir {:prefix "cljdoc-update-readme"})]
    (try
      (doall
       (for [contributor-type (keys contributors)]
         (do
           (status/line :detail (str contributor-type))
           (doseq [{:keys [github-id contributions]} (contributor-type contributors)]
             (status/line :detail (str "  " github-id " " contributions))
             (generate-image! driver (str work-dir) github-id contributions image-opts)))))
      (let [target-path (:images-dir image-opts)]
        (move-path work-dir target-path))
      (catch java.lang.Exception e
        (when (fs/exists? work-dir)
          (fs/delete-tree work-dir))
        (throw e))
      (finally
        (etaoin/quit driver)))))

(defn- missing-prerequesites []
  (let [need "chromedriver"
        found (fs/which need)]
    (if found
      (do
        (status/line :detail "found: %s" found)
        nil)
      (format "not found: %s" need))))

(defn- load-people []
  (let [people-source "doc/people.edn"
        people (-> (slurp people-source) edn/read-string)
        contributors (-> people :contributors)]
    (status/line :detail (str  "people source: " people-source))
    (doseq [{:keys [github-id contributions] :as p} contributors]
      (when (not (and github-id contributions))
        (status/die 1 "Malformed entry: %s" p)))
    (doseq [{:keys [contributions] :as p} contributors
            c contributions]
      (when (not (get contributions-lookup c))
        (status/die 1 "Unrecognized contribution keyword %s in %s" c p)))
    (let [dupes (for [[id freq] (->> contributors (map :github-id) frequencies)
                      :when (> freq 1)]
                  id)]
      (when (seq dupes)
        (status/die 1 "Found duplicate github-id entries: %s" (into [] dupes))))
    people))

(defn -main [& _args]
  (let [readme-filename "README.adoc"
        image-opts {:image-width 273
                    :images-dir "./doc/generated/people"}
        people (load-people)]
    (status/line :head "updating docs to honor those who contributed")
    (when-let [missing (missing-prerequesites)]
      (status/die 1 "Pre-requisites not met\n%s" missing))
    (generate-contributor-images! people image-opts)
    (update-readme-file! people readme-filename image-opts)
    (status/line :detail "SUCCESS"))
  (shutdown-agents))
