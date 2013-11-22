(ns chat_server.core
  (:gen-class)
  (:use org.httpkit.server
        [clojure.java.io]
        [ring.middleware.file-info :only [wrap-file-info]]
        [clojure.tools.logging :only [info]]
        [clojure.data.json :only [json-str read-json]]
        (compojure [core :only [defroutes GET POST]]
          [route :only [files not-found]]
          [handler :only [site]]
          [route :only [not-found]])))
; store max-id and function next-id
(defonce max-id (atom 0))
(defn next-id []
  (swap! max-id inc))
; return current second, quot = divide
(defn- now [] (quot (System/currentTimeMillis) 1000))
; create references 'clients' to dictionary
(def clients (atom {}))
(def clients-last-id (atom {}))
(defn min-clients-last-id []
  (if (empty? @clients-last-id)
    max-id
    (apply min (vals @clients-last-id))))
; strip html
(import '[org.jsoup Jsoup])
(defn strip-html-tags [s]
  (.text (Jsoup/parse s)))
; perfomance log
(defonce log-msgs (atom []))
(defn add-log [msg]
  (let [msg (clojure.string/join " " [(Thread/currentThread) (System/currentTimeMillis) msg "\r\n"])]
  (swap! log-msgs conj msg)))
(defn dump []
  (do
    (with-open [wrtr (writer "perfomance.txt" :append true)]
      (doseq [line @log-msgs]
      (.write wrtr line)))
    (reset! log-msgs [])))
; messages
(defonce all-msgs (ref [{:id (next-id) :time (now) :msg "Добро пожаловаать в чат!" :author "system"}]))
(defn add-msg-to-storage [msg]
  (dosync (let [all-msgs* (conj @all-msgs msg)] (ref-set all-msgs all-msgs*))))
(defn min-msg-id []
  (apply min (map (fn [msg] (:id msg)) @all-msgs)))
(defn clear-old-msgs []
  (let [min-id (min-clients-last-id)]
    (dosync (ref-set all-msgs (filter (fn [msg] (>= (:id msg) (- min-id 10))) @all-msgs)))))
; multi method, dispatch by command and version
(defn dispatcher [packet channel] [(:command packet) (:version packet)])
(defmulti  process-packet dispatcher)
; login version 1
(defmethod process-packet ["login" "1"] [packet channel]
  (do
    (let [login (:data packet)]
      (swap! clients         assoc channel login)
      (swap! clients-last-id assoc channel (min-msg-id))
      (send! channel (json-str {:status "ok"})))))
; logout version 1
(defn logout [channel]
  (do
    (swap! clients dissoc channel)
    (swap! clients-last-id dissoc channel)
    (clear-old-msgs)))
(defmethod process-packet ["logout" "1"] [packet channel]
  (do
    (logout channel)
    (send! channel (json-str {:status "ok"}))))
; send version 1
(defmethod process-packet ["send" "1"] [packet channel]
  (when (:data packet)
    (let [author (@clients channel) text (strip-html-tags (:data packet))]
      (do
        (dosync (ref-set all-msgs (conj @all-msgs {:id (next-id) :time (now) :msg text :author author})))
        (send! channel (json-str {:status "ok"}))))))
; fetch version 1
(defmethod process-packet ["fetch" "1"] [packet channel]
  (let [last-id (@clients-last-id channel) cur-id (+ @max-id 1)]
    (do
      (send! channel (json-str {:status "ok" :data (filter (fn [msg] (>= (:id msg) last-id)) @all-msgs)}))
      (swap! clients-last-id assoc channel cur-id)
      (clear-old-msgs))))
; perfomance dump version 1
(defmethod process-packet ["dump" "1"] [packet channel] (dump))
; default method - simple print packet
(defmethod process-packet :default [packet channel] (info "unknown packet: " packet))
; handler for process http-kit
(defn chat-handler [req]
  (with-channel req channel
    (on-receive channel (fn [msg]
      (let [data (read-json msg)]
        (add-log "start packet process")
        (process-packet data channel)
        (add-log "end packet process"))))
    (on-close channel (fn [status] (logout channel)))))
; routes for http-kit
(defroutes chartrootm
  (GET "/ws" []  chat-handler)
  (files "" {:root "static"})
  (not-found "<p>Page not found.</p>" ))
; simple run server
(defn -main [& args]
  (run-server (-> #'chartrootm) {:port 9001})
  (info "server started on port 9001"))