package org.h2.command.query;

import org.h2.engine.SessionLocal;
import org.h2.table.TableFilter;
import org.h2.table.Table;

/**
 * Determines the best join order by following rules rather than considering every possible permutation.
 */
public class RuleBasedJoinOrderPicker {
    final SessionLocal session;
    final TableFilter[] filters;

    public RuleBasedJoinOrderPicker(SessionLocal session, TableFilter[] filters) {
        this.session = session;
        this.filters = filters;
    }

    public TableFilter[] bestOrder() {
        List<TableFilter> ordered = new ArrayList<>();
        List<TableFilter> remaining = new ArrayList<>(Arrays.asList(filters));

        // Step 1: Select the first table, i.e., the one with the lowest row count.
        TableFilter first = null;
        long minRowCount = Long.MAX_VALUE;
        for (TableFilter f : remaining) {
            long rowCount = getRowCount(f);
            if (rowCount < minRowCount) {
                minRowCount = rowCount;
                first = f;
            }
        }
        if (first == null) {
            // Should not occur, but if it does, return the original order.
            return filters;
        }
        ordered.add(first);
        remaining.remove(first);

        // Step 2: Iteratively add the remaining tables.
        // a) Prefer tables that have an explicit join condition with any already ordered table.
        // b) Among those, choose the one with the lowest row count.
        while (!remaining.isEmpty()) {
            TableFilter candidate = null;
            minRowCount = Long.MAX_VALUE;
            for (TableFilter f : remaining) {
                if (hasExplicitJoinCondition(f, ordered)) {
                    long rowCount = getRowCount(f);
                    if (rowCount < minRowCount) {
                        minRowCount = rowCount;
                        candidate = f;
                    }
                }
            }
            // If no table meets the join condition requirement, choose the one with the lowest row count.
            if (candidate == null) {
                for (TableFilter f : remaining) {
                    long rowCount = getRowCount(f);
                    if (rowCount < minRowCount) {
                        minRowCount = rowCount;
                        candidate = f;
                    }
                }
            }
            ordered.add(candidate);
            remaining.remove(candidate);
        }
        return ordered.toArray(new TableFilter[ordered.size()]);
    }

    /**
     * Retrieves the approximate row count for a given TableFilter.
     * First, it attempts to call getRowCountApproximation() on the filter.
     * If that method does not exist, it falls back to the underlying table's method.
     */
    private long getRowCount(TableFilter filter) {
        try {
            return filter.getRowCountApproximation();
        } catch (NoSuchMethodError e) {
            return filter.getTable().getRowCountApproximation();
        }
    }

    /**
     * Determines whether the given TableFilter has an explicit join condition with any of
     * the tables in the ordered list. It does this by retrieving the full join condition
     * expression, converting it to SQL, and checking if it contains any alias from the ordered tables.
     */
    private boolean hasExplicitJoinCondition(TableFilter filter, List<TableFilter> ordered) {
        Expression expr = filter.getFullCondition();
        if (expr == null) {
            return false;
        }
        String condSql = expr.getSQL();
        for (TableFilter t : ordered) {
            // Use getTableAlias() to obtain the table alias (or table name if no alias is set)
            if (condSql.contains(t.getTableAlias())) {
                return true;
            }
        }
        return false;
    }

}