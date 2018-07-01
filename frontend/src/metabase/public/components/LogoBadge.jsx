/* @flow */

import React from "react";
import LogoIcon from "metabase/components/LogoIcon";

import cx from "classnames";

type Props = {
  dark: boolean,
};

const LogoBadge = ({ dark }: Props) => (
  <a
    href="http://www.softheon.com/"
    target="_blank"
    className="h4 flex text-bold align-center no-decoration"
  >
    <img src="https://www.softheon.com/HTMLCache/Resources/64x64-logo-01.png" width={64} height={64} />
    <span className="text-small">
      <span className="ml1 text-grey-3">Powered by</span>{" "}
      <span className={cx({ "text-brand": !dark }, { "text-white": dark })}>
        Softheon
      </span>
    </span>
  </a>
);

export default LogoBadge;
