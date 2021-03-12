(ns workout-tool.core
    (:require 
              [reagent.core :as reagent :refer [atom]]
              [reagent.dom :as rd]
              [goog.dom :as gdom]))

(enable-console-print!)

(defn curr-time []
  "Returns current time in milliseconds."
  (.getTime (js/Date.)))

(defn diff-in-seconds
  "Calculates the difference between two values in seconds. Accepts a modifier so we can round numbers up."
  ([start end]
   (diff-in-seconds start end int))
  ([start end modifier]
   (modifier (/ (- end start) 1000))))

;; define your app data so that it doesn't get over-written on reload

;; Sound credits go here: https://freesound.org/people/FoolBoyMedia/sounds/352652/
(defonce alert-sound (js/Audio. "alert.mp3"))
(def tick-audio-context (js/AudioContext.))
(def tick-step 1)
(def look-ahead (/ tick-step 2))

(defonce app-state (atom {:last-tick (.-currentTime tick-audio-context)
                          :tick-interval nil
                          :play-tempo false
                          :rest-time 90
                          :time-remaining 0
                          :stopwatch-current 0
                          :stopwatch-start (curr-time)
                          :stopwatch-interval nil
                          :timer-state :stopwatch
                          :target-time 0
                          :timer-interval nil
                          :edit-time false}))


(defn update-stopwatch! []
  (let [start (:stopwatch-start @app-state)]
    (swap! app-state assoc :stopwatch-current (diff-in-seconds start (curr-time)))))

(defn start-stopwatch! []
  (swap! app-state assoc
         :timer-state :stopwatch
         :stopwatch-start (curr-time)
         :stopwatch-current 0
         :stopwatch-interval (js/setInterval update-stopwatch! 200)))

(defn update-time-remaining! []
  (let* [target-time (:target-time @app-state)
         time-remaining (diff-in-seconds (curr-time) target-time (.-ceil js/Math))]
    (swap! app-state assoc :time-remaining time-remaining)
    (if (> time-remaining 0)
      (js/setTimeout update-time-remaining! 100)
      (do
        (.play alert-sound)
        (swap! app-state assoc
               :time-remaining 0
               :timer-state :stopwatch
               :stopwatch-start (curr-time)
               :stopwatch-interval (js/setInterval update-stopwatch! 200))
        (js/clearInterval (:timer-interval @app-state))))))

(defn stop-timer! []
  (js/clearInterval (:timer-interval @app-state))
  (start-stopwatch!))

(defn start-timer! []
  (let [curr-time (curr-time)
        rest-time (:rest-time @app-state)
        stopwatch-interval (:stopwatch-interval @app-state)]
    (js/clearInterval stopwatch-interval)
    (swap! app-state assoc
           :edit-time false
           :target-time (+ curr-time (* rest-time 1000))
           :time-remaining rest-time
           :timer-interval (js/setInterval update-time-remaining! 200)
           :timer-state :rest-timer)))

(defn normal-timer-display []
  (let [edit-time! #(swap! app-state assoc :edit-time true)]
    [:div {:on-click edit-time!}
     (case (:timer-state @app-state)
       :stopwatch (:stopwatch-current @app-state)
       (:time-remaining @app-state))]))

(defn timer-edit-display []
  (letfn [(quit-edit-mode [] (swap! app-state assoc :edit-time false))]
    [:div
     [:input {:type "text"
              :placeholder "0"
              :value (:rest-time @app-state)
              :on-change #(swap! app-state assoc :rest-time (-> % .-target .-value))}]
      [:button {:on-click quit-edit-mode} "X"]]))

(defn timer-display []
  (let [timer-running (not= (:timer-state @app-state) :rest-timer)]
    [:div (if (:edit-time @app-state)
            (timer-edit-display)
            (normal-timer-display))
     [:button {:on-click (if timer-running start-timer! stop-timer!)}
      (if timer-running "Start" "Stop")]]))

;; Metronome method taken from here:
;; https://stackoverflow.com/questions/62512755/accurately-timing-sounds-in-browser-for-a-metronome

(defn schedule-tick [time duration]
  (let [osc (.createOscillator tick-audio-context)]
    (doto osc
      (.connect (.-destination tick-audio-context))
      (.start time)
      (.stop (+ time duration)))))

(defn tick! []
  (let* [last-tick (:last-tick @app-state)
         diff (- (.-currentTime tick-audio-context) last-tick)]
    (when (>= diff look-ahead)
      (let [next-note (+ tick-step last-tick)]
        (schedule-tick next-note 0.025)
        (swap! app-state assoc :last-tick next-note)))))

(defn resume-tick! []
  (.resume tick-audio-context)
  (swap! app-state assoc :tick-interval (js/setInterval tick! 15)))

(defn toggle-tempo! []
  (let [tempo-is-on (not (:play-tempo @app-state))]
    (swap! app-state assoc :play-tempo tempo-is-on)
    (if tempo-is-on
      (resume-tick!)
      (js/clearInterval (:tick-interval @app-state)))))

(defn tempo-button []
  [:div
   [:button {:on-click toggle-tempo!} (if (:play-tempo @app-state)
                                        "Stop tempo"
                                        "Start tempo")]])
(defn app []
  [:div {:class "app"}
   (timer-display)
   (tempo-button)])

(rd/render [app] (gdom/getElement "app"))

(start-stopwatch!)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  (swap! app-state update-in [:__figwheel_counter] inc)
)
