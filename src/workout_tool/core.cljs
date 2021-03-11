(ns workout-tool.core
    (:require 
              [reagent.core :as reagent :refer [atom]]
              [reagent.dom :as rd]
              [goog.dom :as gdom]))

(enable-console-print!)

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
                          :stopwatch-start (.getTime (js/Date.))
                          :timer-state :stopwatch
                          :target-time 0
                          :timer-interval nil
                          :edit-time false}))

(defn play-alert! []
  (.play alert-sound))

(defn update-stopwatch! []
  (let [start (:stopwatch-start @app-state)
        curr (.getTime (js/Date.))]
    (swap! assoc :stopwatch-current (int (/ (- curr start) 1000)))))

(defn update-time-remaining! []
  (let* [target-time (:target-time @app-state)
         curr-time  (.getTime (js/Date.))
         time-remaining (int (/ (- target-time curr-time) 1000))]
    (swap! app-state assoc :time-remaining time-remaining)
    (if (> time-remaining 0)
      (js/setTimeout update-time-remaining! 100)
      (do
        (play-alert!)
        (swap! app-state assoc :time-remaining 0)
        (swap! app-state :timer-state :stopwatch)
        (js/clearInterval (:timer-interval @app-state))
        (js/setInterval update-stopwatch! 200)))))

(defn reset-timer! []
  (swap! app-state assoc :time-remaining 0)
  (js/clearInterval (:timer-interval @app-state)))

(defn start-timer! []
  (let [curr-time (.getTime (js/Date.))
        rest-time (:rest-time @app-state)]
    (swap! app-state assoc :edit-time false)
    (swap! app-state assoc :target-time (+ curr-time (* rest-time 1000)))
    (swap! app-state assoc :timer-interval (js/setInterval update-time-remaining! 200))))

(defn normal-timer-display []
  [:div {:on-click #(swap! app-state assoc :edit-time true)}
   (case (:timer-state @app-state)
         :stopwatch (:stopwatch-current @app-state)
         (:time-remaining @app-state))])

(defn timer-edit-display []
  [:input {:type "text"
           :placeholder "0"
           :value (:rest-time @app-state)
           :on-change #(swap! app-state assoc :rest-time (-> % .-target .-value))}])

(defn timer-display []
  (let [timer-running (> (:time-remaining @app-state) 0)]
    [:div (if (:edit-time @app-state)
            (timer-edit-display)
            (normal-timer-display))
     [:button {:on-click (if timer-running start-timer! reset-timer!)}
      (if timer-running "Start" "Reset")]]))

;; Metronome method taken from here:
;; https://stackoverflow.com/questions/62512755/accurately-timing-sounds-in-browser-for-a-metronome

(defn schedule-tick [time duration]
  (let [osc (.createOscillator tick-audio-context)]
    (.connect osc (.-destination tick-audio-context))
    (.start osc time)
    (.stop osc (+ time duration))))

(defn tick! []
  (let [diff (- (.-currentTime tick-audio-context) (:last-tick @app-state))]
    (when (>= diff look-ahead)
      (let [next-note (+ tick-step (:last-tick @app-state))]
        (schedule-tick next-note, 0.025)
        (swap! app-state assoc :last-tick next-note)))))

(defn resume-tick! []
  (.resume tick-audio-context)
  (swap! app-state assoc :tick-interval (js/setInterval tick! 15)))

(defn toggle-tempo! []
  (let [play-tempo (not (:play-tempo @app-state))]
    (swap! app-state assoc :play-tempo play-tempo)
    (if play-tempo
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

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  (swap! app-state update-in [:__figwheel_counter] inc)
)
