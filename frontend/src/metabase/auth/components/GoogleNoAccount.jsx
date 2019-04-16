import React from "react";
import { t } from "c-3po";
import AuthScene from "./AuthScene.jsx";
import LogoIcon from "metabase/components/LogoIcon.jsx";
import BackToLogin from "./BackToLogin.jsx";

const GoogleNoAccount = () => (
  <div className="full-height bg-white flex flex-column flex-full md-layout-centered">
    <div className="wrapper">
      <div className="Login-wrapper Grid  Grid--full md-Grid--1of2">
        <div className="Grid-cell flex layout-centered text-brand">
          <img src="app/assets/img/Softheon_Logo_Color.png" width={64} height={64} /> 
        </div>
        <div className="Grid-cell text-centered bordered rounded shadowed p4">
          <h3 className="mt4 mb2">{t`No Softheon account exists for this Google account.`}</h3>
          <p className="mb4 ml-auto mr-auto" style={{ maxWidth: 360 }}>
            {t`You'll need an administrator to create a Softheon account before you can use Google to log in.`}
          </p>

          <BackToLogin />
        </div>
      </div>
    </div>
    <AuthScene />
  </div>
);

export default GoogleNoAccount;
