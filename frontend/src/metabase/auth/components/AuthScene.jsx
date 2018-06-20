import React, { Component } from "react";
import ReactDOM from "react-dom";

export default class AuthScene extends Component {
  componentDidMount() {
    // HACK: React 0.14 doesn't support "fill-rule" attribute. We can remove this when upgrading to React 0.15.
    ReactDOM.findDOMNode(this.refs.HACK_fill_rule_1).setAttribute(
      "fill-rule",
      "evenodd",
    );
    ReactDOM.findDOMNode(this.refs.HACK_fill_rule_2).setAttribute(
      "fill-rule",
      "evenodd",
    );
  }

  render() {
    return (
      <section className="z1 brand-scene absolute bottom left right hide sm-show">
        
      </section>
    );
  }
}
