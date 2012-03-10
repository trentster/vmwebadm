(ns server.machines.get
  (:use [server.utils :only [clj->js prn-js clj->json transform-keys prn]])
  (:require [server.vm :as vm]
            [server.http :as http]
            [server.machines.list :as machines.list]))

(defn handle [resource request response uuid]
  (vm/lookup
   {"uuid" uuid}
   {:full true}
   (fn [error vms]
     (if error
       (http/error response error)
       (http/write response 200
                   {"Content-Type" "application/json"}
                   (clj->json (transform-keys machines.list/res-map (first vms))))))))