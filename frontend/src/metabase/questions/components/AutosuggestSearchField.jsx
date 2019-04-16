import React, { Component } from "react";
import { connect } from "react-redux";
import PropTypes from "prop-types";
// import { push, replace, goBack } from "react-router-redux";
import Autosuggest from "react-autosuggest";
import cx from "classnames";
import S from "./List.css";
import Icon from "metabase/components/Icon.jsx";
import pure from "recompose/pure";
import Color from "color";
import IsolatedScroll from "react-isolated-scroll";
import {
  KEYCODE_FORWARD_SLASH,
  KEYCODE_ENTER,
  KEYCODE_ESCAPE
} from "metabase/lib/keyboard";
import { Motion, spring } from "react-motion";
import theme from "./theme.css";
import { CardApi } from "metabase/services";
import * as Urls from "metabase/lib/urls";
import _ from "underscore";

function autocompleteResults(prefix) {
  let apiCall = CardApi.card_autocomplete_suggestions({
    prefix: prefix
  });
  return apiCall;
}

const ITEM_ICON_SIZE = 20;
const SuggestionCollectionBadge = ({ className, collection }) => {
  const color = Color(collection.color);
  const darkened = color.darken(0.1);
  const lightened = color.lighten(0.4);
  return (
    <a
      className={cx(className, "px1 rounded mx1")}
      style={{
        fontSize: 16,
        color: lightened.isDark() ? "#fff" : darkened,
        //backgroundColor: lightened
      }}
    >
      {collection.name}
    </a>
  );
};
const SuggestionItemBody = pure(({ name, collection }) => {
  return (
    <div className={S.suggestionItemBody}>
      <div className={cx("flex", S.suggestionItemTitle)}>
        <span>{name}</span>
      </div>
      {collection &&
        collection.name &&
        collection.color && (
          <SuggestionCollectionBadge collection={collection} />
        )}
    </div>
  );
});

const SuggestionItem = ({ suggestion, query, isHighlighted }) => (
  <div className={cx("hover-parent hover--visibility", S.suggestionItem)}>
    <div className="flex flex-full align-center">
      <div
        className="relative flex ml1 mr2"
        style={{ width: ITEM_ICON_SIZE, height: ITEM_ICON_SIZE }}
      >
        {suggestion.display && (
          <Icon
            className="text-light-blue absolute top left visible"
            name={suggestion.display}
            size={ITEM_ICON_SIZE}
          />
        )}
      </div>
      <SuggestionItemBody
        name={suggestion.name}
        collection={suggestion.collection}
      />
    </div>
  </div>
);

export default class AutosuggestSearchField extends Component {
  static propTypes = {
    onSearch: PropTypes.func.isRequired
  };

  constructor(props, context) {
    super(props, context);
    this.state = {
      value: "",
      suggestions: [],
      selected: ""
    };
    this.loadSuggestionThrottled = _.throttle(this.loadSuggestion, 500);
    this.loadSuggestionDebounced = _.debounce(this.loadSuggestion, 800);
  }

  loadSuggestion = async value => {
    try {
      let results = await autocompleteResults(value);
      if (results.search === this.state.value) {
        this.setState({
          suggestions: results.suggestions
        });
      }
      // this.setState({
      //   suggestions: results.suggestions
      // });
    } catch (error) {
      console.log("error getting autocompletion card data", error);
      this.setState({
        suggestions: []
      });
    }
  };

  onSuggestionsFetchRequested = ({ value, reason }) => {
    if (reason === "input-changed") {
      if (value.length < 5 || value.endsWith(" ")) {
        this.loadSuggestionThrottled(value);
      } else {
        this.loadSuggestionDebounced(value);
      }
    }
  };

  onSuggestionsClearRequested = () => {
    this.setState({
      suggestions: []
    });
  };

  getSuggestionValue = suggestion => {
    return suggestion.name;
  };

  renderSuggestion = (suggestion, { query, isHighlighted }) => {
    return (
      <SuggestionItem
        suggestion={suggestion}
        query={query}
        isHighlighted={isHighlighted}
      />
    );
  };

  onChange = (event, { newValue, method }) => {
    this.setState({
      value: newValue
    });
  };

  onSuggestionSelected = (
    event,
    { suggestion, suggestionValue, suggestionIndex, sectionIndex, method }
  ) => {
    this.setState({
      selected: suggestion
    });
    this.props.push(Urls.question(suggestion.id));
  };

  shouldRenderSuggestions = value => {
    return value.trim().length > 0;
  };

  renderInputComponent = inputProps => {
    return (
      <SuggestionExpandingSearchField
        inputProps={inputProps}
        onSearch={this.props.onSearch}
      />
    );
  };

  renderSuggestionsContainer = ({ containerProps, children, query }) => {
    const { ref, ...restContainerProps } = containerProps;
    const callRef = isolatedScroll => {
      if (isolatedScroll !== null) {
        ref(isolatedScroll.component);
      }
    };
    return (
      <IsolatedScroll ref={callRef} {...restContainerProps}>
        {children}
      </IsolatedScroll>
    );
  };

  render() {
    const { value, suggestions } = this.state;
    const inputProps = {
      value,
      onChange: this.onChange
    };
    return (
      <Autosuggest
        suggestions={suggestions}
        onSuggestionsFetchRequested={this.onSuggestionsFetchRequested}
        onSuggestionsClearRequested={this.onSuggestionsClearRequested}
        getSuggestionValue={this.getSuggestionValue}
        renderSuggestion={this.renderSuggestion}
        inputProps={inputProps}
        onSuggestionSelected={this.onSuggestionSelected}
        shouldRenderSuggestions={this.shouldRenderSuggestions}
        renderInputComponent={this.renderInputComponent}
        renderSuggestionsContainer={this.renderSuggestionsContainer}
      />
    );
  }
}

class SuggestionExpandingSearchField extends Component {
  static propTypes = {
    inputProps: PropTypes.object.isRequired,
    className: PropTypes.string,
    onSearch: PropTypes.func.isRequired
  };
  constructor(props, context) {
    super(props, context);
    this.state = {
      active: false
    };
  }

  onKeyDown = e => {
    if (!this.state.active && e.keyCode === KEYCODE_FORWARD_SLASH) {
      this.setActive();
      e.preventDefault();
    }
  };

  onKeyPress = e => {
    if (e.keyCode === KEYCODE_ENTER) {
      this.props.onSearch(e.target.value);
    } else if (e.keyCode === KEYCODE_ESCAPE) {
      this.setInactive();
    }
  };

  setActive = () => {
    this.setState({
      active: true
    });
  };

  setInactive = () => {
    this.setState({
      active: false
    });
  };
  toggleActive = () => {
    this.setState({
      active: !this.state.active
    });
  };
  onFocus = e => {
    this.setActive();
    e.preventDefault();
  };

  onBlur = e => {
    this.setInactive();
    e.preventDefault();
  };

  render() {
    const { className } = this.props;
    const { active } = this.state;
    let { inputProps } = this.props;

    inputProps = {
      ...inputProps,
      onKeyUp: this.onKeyPress
    };
    return (
      <div
        className={cx(
          className,
          "bordered border-grey-1 flex align-center pr2 transition-border",
          { "border-brand": active }
        )}
        onClick={this.setActive}
        style={{ borderRadius: 99 }}
        // onFocus={this.onFocus}
        onBlur={this.onBlur}
      >
        <Icon
          className={cx("ml2 text-grey-3", { "text-brand": active })}
          name="search"
        />
        <Motion style={{ width: spring(500) }}>
          {interpolatingStyle => (
            <input
              {...inputProps}
              className="input borderless text-bolder"
              style={Object.assign({}, interpolatingStyle, { fontsize: "1em" })}
            />
          )}
        </Motion>
      </div>
    );
  }
}
