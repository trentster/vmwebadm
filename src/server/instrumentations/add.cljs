(ns server.instrumentations.add
  (:use [server.utils :only [clj->js clj->json]])
  (:require [server.storage :as storage]
            [cljs.nodejs :as node]
            [clojure.string :as c.s]
            [dtrace :as dtrace]
            [server.vm :as vm]
            [server.http :as http]))

(defn handle [resource request response account]
  (http/with-reqest-body request
    (fn [data]
      (if (map? data)
        (if-let [clone (data "clone")]
          (let [clone (js/parseInt clone)]
            (if (map? (getstorage/instrumentations account))
              (swap! storage/instrumentations update-in [account]
                     #(conj % (nth % clone)))
              (http/e404 response
                         "Instrumentation to clone not found."))
            (http/ret response
                      (get-in @storage/instrumentations [account clone])))
          
          (if-let [handler (get-in @dtrace/handler [(data "module")
                                                    (data "stat")])]
            (let [data (if (data "predicate")
                         (assoc data "predicate" (js->clj
                                                  (.parse
                                                   js/JSON
                                                   (data "predicate"))))
                         data)
                  consumer (dtrace/new)
                  data (assoc data
                         :consumer consumer)]
              (vm/lookup
               {"owner_uuid" account}
               {:full false}
               (fn [error vms]
                 (let [code (handler
                             (if (get-in @storage/data [:users account :admin])
                               :all
                               vms)
                             data)]
                   (print "=======DTRACE=======\n"
                          code "\n"
                          "====================\n"
                          "\n")
                   (dtrace/compile consumer code)
                   (dtrace/start consumer))))
              (swap! storage/instrumentations (fn [insts]
                                                (update-in
                                                 insts
                                                 [account]
                                                 #(vec (conj % data)))))
              (http/ret response
                        {:data (dissoc
                                data
                                :consumer)}))
            (http/e404 response
                       "unknown metric"))))
            (http/e404 response
                       "unknown parameters"))))
