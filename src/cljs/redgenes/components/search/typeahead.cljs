(ns redgenes.components.search.typeahead
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame :refer [subscribe dispatch]]
            [accountant.core :refer [navigate!]]
            [redgenes.components.search.views :as views]
            [dommy.core :as dommy :refer-macros [sel sel1]]))

(defn navigate-to
  "Navigate to the report page for the given item and reset the UI" [item]
    (dispatch [:search/reset-selection])
    (dispatch [:search/reset-quicksearch])
    (navigate! (str "#/objects/" (:type item) "/" (:id item)))
  )

(defn suggestion
  "The UI element and behaviour for a single suggestion in the dropdown" []
  (let [search-term (subscribe [:search-term])]
    (fn [item is-active?]
      (let [info   (clojure.string/join " " (interpose ", " (vals (:fields item))))
            parsed (clojure.string/split info (re-pattern (str "(?i)" @search-term)))]
        [:div.list-group-item
         {:on-mouse-down (fn [e]
                           (let [clicked-button (.-button e)]
                              (cond (= clicked-button 0) ;;left click only pls.
                                  (navigate-to item))))
          :class (cond is-active? "active")}
         [:div.row-action-primary
          [:i.fa.fa-cog.fa-spin.fa-3x.fa-fw]]
         [:div.row-content
          [:h4.list-group-item-heading (:type item)]
          (into
            [:div.list-group-item-text]
            (interpose [:span.highlight @search-term] (map (fn [part] [:span part]) parsed)))]]))))

(defn monitor-enter-key [e]
  (let [keycode (.-charCode e)
        active-selection (subscribe [:quicksearch-selected-index])
        results (subscribe [:suggestion-results])
        selected-result (nth @results @active-selection nil)]
     (cond
       (= keycode 13) ;;enter key is 13
        (do
          (if selected-result
          ;; go to the result direct if they're navigating with keyboard
          ;; and they just pressed enter
          (navigate-to selected-result)
          ;; go to the results page if they just type and press enter without
          ;; selecting a typeahead result
          (do (navigate! "#/search")
              (views/search)))
       ;;no matter what the result, stop showing the quicksearch, kthx.
       (.blur (. e -target)))
 )))

(defn monitor-arrow-keys
  "Navigate the dropdown suggestions if the user presses up or down" [e]
  (let [keycode (.-key e)
        input (.. e -target -value)]
     (cond
       (= keycode "ArrowUp")
         (dispatch [:search/move-selection :prev])
       (= keycode "ArrowDown")
         (dispatch [:search/move-selection :next])
    )))

(defn main []
  (reagent/create-class
    (let [results     (subscribe [:suggestion-results])
          search-term (subscribe [:search-term])]
      {:component-did-mount (fn [e]
                              (let [node (reagent/dom-node e)]
                                (-> node
                                    (sel1 :input)
                                    (dommy/listen! :focus (fn [] (dommy/add-class! node :open)))
                                    (dommy/listen! :blur (fn [] (dommy/remove-class! node :open))))))
       :reagent-render
                            (fn []
                              [:div.dropdown
                               [:input.form-control.input-lg.square
                                {:type        "text"
                                 :value       @search-term
                                 :placeholder "Search"
                                 :on-change   #(dispatch [:bounce-search (-> % .-target .-value)])
                                 ;Navigate to the main search results page if the user presses enter.
                                 :on-key-press (fn [e] (monitor-enter-key e))
                                 ; Why is this separate from on-key-press, you ask? arrow keys don't trigger keypress events apparent. what meanies.
                                 :on-key-up (fn [e] (monitor-arrow-keys e) )}]
                              (if @results
                                 [:div.dropdown-menu
                                  (into [:div.list-group]
                                    (interpose [:div.list-group-separator]
                                      (map-indexed  (fn [index result] (let [active-selection (subscribe [:quicksearch-selected-index])
                                                                    is-active? (= index @active-selection)]
                                                                [suggestion result is-active?])) @results)))])])})))
