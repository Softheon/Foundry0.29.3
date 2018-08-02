import React, { Component } from "react";
import { connect } from "react-redux";
import PermissionsEditor from "../components/PermissionsEditor";
import PermissionsApp from "./PermissionsApp";
import { PulseApi } from "metabase/services";

import {
  getPulsePermissionsGrid,
  getIsDirty,
  getSaveError,
  getDiff
} from "../selectors";

import { updatePermission, savePermissions, } from "../permissions";

import { goBack, push } from "react-router-redux";

const mapStateToProps = (state, props) => {
  return {
    grid: getPulsePermissionsGrid(state, props),
    isdirty: getIsDirty(state, props),
    saveError: getSaveError(state, props),
    diff: getDiff(state, props)
  };
};

const mapDispatchToProps = {
  onUpdatePermission: updatePermission,
  onSave: savePermissions,
  onCancel: () => (window.history.length > 1 ? goBack() : push ("/pulse")),
};

const Editor = connect(mapStateToProps, mapDispatchToProps)(PermissionsEditor);

@connect(null, null)
export default class PulsePermissionsApp extends Component {
  render() {
    return (
      <PermissionsApp
        {...this.props}
        load={PulseApi.graph}
        save={PulseApi.updateGraph}
      >
      <Editor {...this.props} modal confirmCancel={false}/>
      </PermissionsApp>
    )
  }
}

