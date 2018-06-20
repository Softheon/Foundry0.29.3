import React, { Component } from "react";
import PropTypes from "prop-types";
import cx from "classnames";
import _ from "underscore";
import { t, jt } from "c-3po";

import Breadcrumbs from "metabase/components/Breadcrumbs.jsx";
import Input from "metabase/components/Input.jsx";

export default class SettingsSingleSignOnForm extends Component {
  constructor(props, context) {
    super(props, context);
    this.updateClientID = this.updateClientID.bind(this);
    this.updateDomain = this.updateDomain.bind(this);
    (this.onCheckboxClicked = this.onCheckboxClicked.bind(this)),
      (this.saveChanges = this.saveChanges.bind(this)),
      (this.clientIDChanged = this.clientIDChanged.bind(this)),
      (this.domainChanged = this.domainChanged.bind(this));
  }

  static propTypes = {
    elements: PropTypes.array,
    updateSetting: PropTypes.func.isRequired,
  };

  componentWillMount() {
    let { elements } = this.props,
      clientID = _.findWhere(elements, { key: "google-auth-client-id" }),
      domain = _.findWhere(elements, {
        key: "google-auth-auto-create-accounts-domain",
      });

    this.setState({
      clientID: clientID,
      domain: domain,
      clientIDValue: clientID.value,
      domainValue: domain.value,
      recentlySaved: false,
    });
  }

  updateClientID(newValue) {
    if (newValue === this.state.clientIDValue) return;

    this.setState({
      clientIDValue: newValue && newValue.length ? newValue : null,
      recentlySaved: false,
    });
  }

  updateDomain(newValue) {
    if (newValue === this.state.domain.value) return;

    this.setState({
      domainValue: newValue && newValue.length ? newValue : null,
      recentlySaved: false,
    });
  }

  updateIdentityUri(newValue){
    if (newValue === this.state.identityUriValue) return;

    this.setState({
      identityUriValue: newValue && newValue.length ? newValue : null,
      recentlySaved: false,
    });
  }

  updateApiSecret(newValue){
    if (newValue === this.state.apiSecretValue) return;

    this.setState({
      apiSecretValue: newValue && newValue.length ? newValue : null,
      recentlySaved: false,
    });
  }

  clientIDChanged() {
    return this.state.clientID.value !== this.state.clientIDValue;
  }

  domainChanged() {
    return this.state.domain.value !== this.state.domainValue;
  }

  identityUriChanged(){
    return this.state.identityUri.value !== this.state.identityUriValue;
  }

  apiSecretChanged(){
    return this.state.apiSecret.value !== this.state.apiSecretValue;
  }

  saveChanges() {
    let { clientID, clientIDValue, domain, domainValue } = this.state;

    if (this.clientIDChanged()) {
      this.props.updateSetting(clientID, clientIDValue);
      this.setState({
        clientID: {
          value: clientIDValue,
        },
        recentlySaved: true,
      });
    }

    if (this.domainChanged()) {
      this.props.updateSetting(domain, domainValue);
      this.setState({
        domain: {
          value: domainValue,
        },
        recentlySaved: true,
      });
    }

    if(this.identityUriChanged()){
      this.props.updateSetting(identityUri, identityUriValue);
      this.setState({
        identityUri:{
          value: identityUriValue,
        },
        recentlySaved:true;
      })
    }

    if(this.apiSecretChanged()){
      this.props.updateSetting(apiSecret, apiSecretValue);
      this.setState({
        apiSecret:{
          value:apiSecretValue,
        },
        recentlySaved:true,
      })
    }
  }

  onCheckboxClicked() {
    // if domain is present, clear it out; otherwise if there's no domain try to set it back to what it was
    this.setState({
      domainValue: this.state.domainValue ? null : this.state.domain.value,
      recentlySaved: false,
    });
  }

  render() {
    let hasChanges = this.domainChanged() || this.clientIDChanged(),
      hasClientID = this.state.clientIDValue;

    return (
      <form noValidate>
        <div className="px2" style={{ maxWidth: "585px" }}>
          <Breadcrumbs
            crumbs={[
              [t`Authentication`, "/admin/settings/authentication"],
              [t`Softheon Sign-In`],
            ]}
            className="mb2"
          />
          <h2>{t`Sign in with Softheon`}</h2>
          <p className="text-grey-4">
            {t`Allows users with existing Foundry accounts to login with a Softheon account that matches their email address in addition to their Foundry username and password.`}
          </p>
        
          <Input
            className="SettingsInput AdminInput bordered rounded h3"
            type="text"
            value={this.state.clientIDValue}
            placeholder={t`Your Softheon client ID`}
            onChange={event => this.updateClientID(event.target.value)}
          />
          
          <Input 
            className="SettingsInput AdminInput bordered rounded h3" 
            type="text" 
            value={this.state.identityUriValue} 
            placeholder={t`Softheon Identity Server URI`} 
            onChange={event => this.updateIdentityUri(event.target.value)} 
          /> 

          <Input 
            className="SettingsInput AdminInput bordered rounded h3" 
            type="text" 
            value={this.state.apiSecretValue} 
            placeholder={t`Softheon API Secret`} 
            onChange={event => this.updateApiSecret(event.target.value)} 
          /> 

          <button
            className={cx("Button mr2", { "Button--primary": hasChanges })}
            disabled={!hasChanges}
            onClick={this.saveChanges}
          >
            {this.state.recentlySaved ? t`Changes saved!` : t`Save Changes`}
          </button>
        </div>
      </form>
    );
  }
}
