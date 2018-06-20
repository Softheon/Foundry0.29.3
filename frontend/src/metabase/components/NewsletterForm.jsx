/* eslint "react/prop-types": "warn" */
import React, { Component } from "react";
import PropTypes from "prop-types";
import ReactDOM from "react-dom";
import { t } from "c-3po";
import Icon from "metabase/components/Icon.jsx";

export default class NewsletterForm extends Component {
  constructor(props, context) {
    super(props, context);

    this.state = { submitted: false };

    this.styles = {
      container: {
        borderWidth: "2px",
      },

      input: {
        fontSize: "1.1rem",
        color: "#676C72",
        width: "350px",
      },

      label: {
        top: "-12px",
      },
    };
  }

  static propTypes = {
    initialEmail: PropTypes.string.isRequired,
  };

  subscribeUser(e) {
    e.preventDefault();


    this.setState({ submitted: true });
  }

  render() {
    const { initialEmail } = this.props;
    const { submitted } = this.state;

    return (
      <div
        style={this.styles.container}
        className="bordered rounded p4 relative"
      >
        
      </div>
    );
  }
}
