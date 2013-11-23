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
  (if (< (count @log-msgs) 4096)
    (do
      (let [msg (clojure.string/join " " [(Thread/currentThread) (System/currentTimeMillis) msg "\r\n"])]
      (swap! log-msgs conj msg)))))
(defn dump []
  (do
    (with-open [wrtr (writer "perfomance.txt" :append true)]
      (doseq [line @log-msgs]
      (.write wrtr line)))
    (reset! log-msgs [])))
; messages
(defonce all-msgs (ref [{:id (next-id) :time (now) :msg "Добро пожаловать в чат!" :author "system"}]))
(defn clear-old-msgs []
  (dosync
    (let [total (count @all-msgs)]
      (if (> total 4096)
        (ref-set all-msgs (vec (drop (- total 4096) (sort-by :id < @all-msgs))))
        (ref-set all-msgs @all-msgs)))))
(defn add-msg-to-storage [msg]
  (dosync
    (let [all-msgs* (conj @all-msgs msg)] (ref-set all-msgs all-msgs*))
    (clear-old-msgs)))
(defn min-msg-id []
  (apply min (map (fn [msg] (:id msg)) @all-msgs)))

(def not-nil? (complement nil?))
; multi method, dispatch by command and version
(defn dispatcher [packet channel] [(:command packet) (:version packet)])
(defmulti  process-packet dispatcher)
; login version 1
(defmethod process-packet ["login" "1"] [packet channel]
  (do
    (let [login (:data packet) id (- @max-id 20)]
      (swap! clients         assoc channel login)
      (swap! clients-last-id assoc channel id)
      (send! channel (json-str {:status "ok"}))
      (let [author (@clients channel)]
        (if (not-nil? author)(add-msg-to-storage {:id (next-id)
                                                  :time (now)
                                                  :msg (format "Пришел: %s" author)
                                                  :author "system"}))))))
; logout version 1
(defn logout [channel]
  (do
    (let [author (@clients channel)]
      (if (not-nil? author) (add-msg-to-storage {:id (next-id)
                                                 :time (now)
                                                 :msg (format "Ушел: %s" author)
                                                 :author "system"})))
    (swap! clients dissoc channel)
    (swap! clients-last-id dissoc channel)))
(defmethod process-packet ["logout" "1"] [packet channel]
  (do
    (logout channel)
    (send! channel (json-str {:status "ok"}))))
; send version 1
(defmethod process-packet ["send" "1"] [packet channel]
  (when (:data packet)
    (let [author (@clients channel) text (strip-html-tags (:data packet))]
      (do
        (add-msg-to-storage {:id (next-id) :time (now) :msg text :author author})
        (send! channel (json-str {:status "ok"}))))))
; fetch version 1
(defmethod process-packet ["fetch" "1"] [packet channel]
  (let [last-id (@clients-last-id channel) cur-id (+ @max-id 1)]
    (do
      (if (not-nil? last-id)
        (do
          (send! channel
            (json-str {:status "ok" :data (sort-by :id < (filter (fn [msg] (>= (:id msg) last-id)) @all-msgs))}))
          (swap! clients-last-id assoc channel cur-id))))))
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