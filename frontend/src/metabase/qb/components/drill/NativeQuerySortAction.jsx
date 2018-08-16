/* @flow */

import Query from "metabase/lib/query";
import StructuredQuery from "metabase-lib/lib/queries/StructuredQuery";
import { t } from "c-3po";
import type {
  ClickAction,
  ClickActionProps
} from "metabase/meta/types/Visualization";

export default ({ question, clicked }: ClickActionProps): ClickAction[] => {
  const query = question.query();
  if (!clicked || !clicked.column || clicked.value !== undefined) {
    return [];
  }
  const { column, columnIndex } = clicked;

  const actions = [];
  actions.push({
    name: "sort-ascending",
    section: "sort",
    title: t`Ascending`,
    sort: () => query.sortTable("ascending", columnIndex)
  });
  actions.push({
    name: "sort-descending",
    section: "sort",
    title: t`Descending`,
    sort: () => query.sortTable("descending", columnIndex)
  });

  return actions;
};
