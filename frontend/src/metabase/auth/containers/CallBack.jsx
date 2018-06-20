import React, { Component } from 'react';
import { connect } from 'react-redux';
import { CallbackComponent } from 'redux-oidc';
import { push } from 'react-router-redux';
import userManager from '../userManager';
import * as authActions from "../auth";

const mapDispatchToProps = {
    ...authActions
}

@connect(null, mapDispatchToProps)
export default class CallbackPage extends Component {
    constructor(props, context) {
        super(props, context);  
    }

    softheonAuthSuccess = (user) => {
        console.log(user);
        const { loginGoogle, location } = this.props;
        
        loginGoogle(user, location.query.redirect);
    }

    render() {
        return (
            <CallbackComponent
            userManager={userManager}
                successCallback={this.softheonAuthSuccess}
            >
            <div>Redirecting...</div>
            </CallbackComponent>                       
        )
    }
}
