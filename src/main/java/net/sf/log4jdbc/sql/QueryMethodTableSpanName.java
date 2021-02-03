/*
 * Copyright 2017-2020 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.sf.log4jdbc.sql;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryMethodTableSpanName {

    static QueryMethodTableSpanName INSTANCE = new QueryMethodTableSpanName();

    QueryMethodTableSpanName() {
    }

    enum ManipulationMethod {
        SELECT, INSERT, UPDATE, DELETE, NOT_FOUND
    }

    private final String[] SQL_MANIPULATION_METHODS = {
            "SELECT",
            "INSERT",
            "UPDATE",
            "DELETE"
    };

    enum DefinitionMethod {
        CREATE_TABLE,
        ALTER_TABLE, DROP_TABLE, TRUNCATE, CREATE_INDEX, DROP_INDEX,
        CREATE_MATERIALIZED_VIEW, ALTER_MATERIALIZED_VIEW,
        DROP_MATERIALIZED_VIEW, NOT_FOUND
    }

    private final String[] SQL_DEFINITION_METHODS = {
            "CREATE TABLE",
            "ALTER TABLE",
            "DROP TABLE",
            "TRUNCATE",
            "CREATE INDEX",
            "DROP INDEX",
            "CREATE MATERIALIZED VIEW",
            "ALTER MATERIALIZED VIEW",
            "DROP MATERIALIZED VIEW"
    };

    public String querySpanName(String query) {
        // Short Circuit
        if (query == null || query.equals("")) {
            return "SQL";
        }

        String queryMethod;
        String queryTable = "N/A";
        // First check to see if query is a manipulation method
        ManipulationMethod manipulationMethod = getManipulationMethod(query);
        queryMethod = manipulationMethod.toString();
        if (!manipulationMethod.equals(ManipulationMethod.NOT_FOUND)) {
            switch (manipulationMethod) {
                case SELECT:    // SELECT ... FROM X ...
                case DELETE:    // DELETE ... FROM X ...
                    queryTable = findTargetEntityAfter(query, " FROM");
                    break;
                case INSERT:    // INSERT ... INTO X ...
                    queryTable = findTargetEntityAfter(query, " INTO");
                    break;
                case UPDATE:    // UPDATE X ...
                    queryTable = findTargetEntityAfter(query, "UPDATE");
                    break;
                default:
                    return "SQL";
            }
            return String.format("SQL.%s - %s", manipulationMethod.toString(), queryTable);
        }

        // If reached this point, manipulationMethod is set to  NOT_FOUND
        DefinitionMethod definitionMethod = getDefininitionMethod(query);
        queryMethod = definitionMethod.toString().replace("_", " ");
        if (!definitionMethod.equals(DefinitionMethod.NOT_FOUND)) {
            switch (definitionMethod) {
                case ALTER_MATERIALIZED_VIEW:   // ALTER MATERIALIZED VIEW X ...
                case ALTER_TABLE:         // ALTER TABLE X ...
                    queryTable = findTargetEntityAfter(query, queryMethod);
                    break;
                case DROP_INDEX:        // DROP INDEX [IF EXISTS] X ...
                case DROP_MATERIALIZED_VIEW:  // DROP MATERIALIZED VIEW [IF EXISTS] X ...
                case DROP_TABLE:        // DROP TABLE [IF EXISTS] X ...
                    queryTable = findTargetEntityAfter(query, queryMethod + " IF EXISTS");
                    if (queryTable.equals("N/A")) {
                        queryTable = findTargetEntityAfter(query, queryMethod);
                    }
                    break;
                case CREATE_INDEX:        // CREATE INDEX [IF NOT EXISTS] X ...
                case CREATE_MATERIALIZED_VIEW:  // CREATE MATERIALIZED VIEW [IF NOT EXISTS] X ...
                case CREATE_TABLE:        // CREATE TABLE [IF NOT EXISTS] X ...
                    queryTable = findTargetEntityAfter(query, queryMethod + " IF NOT EXISTS");
                    if (queryTable.equals("N/A")) {
                        queryTable = findTargetEntityAfter(query, queryMethod);
                    }
                    break;
                case TRUNCATE:          // TRUNCATE [TABLE] X
                    queryTable = findTargetEntityAfter(query, queryMethod + " TABLE");
                    if (queryTable.equals("N/A")) {
                        queryTable = findTargetEntityAfter(query, queryMethod);
                    }
                    break;
            }
            return String.format("SQL.%s - %s", definitionMethod.toString(), queryTable);
        }
        return "SQL";
    }

    /*
      Scan the string to find a manipulation method, if one exists.
      Returns NOT_FOUND if no manipulation method is found, otherwise
      returns the enum for the found manipulation method.
     */
    private ManipulationMethod getManipulationMethod(String query) {
        String[] queryParts = query.toUpperCase().split("\\s+");
        for (String queryPart : queryParts) {
            for (String method : SQL_MANIPULATION_METHODS) {
                if (queryPart.contains(method)) {
                    return ManipulationMethod.valueOf(method);
                }
            }
        }
        return ManipulationMethod.NOT_FOUND;
    }

    /*
      Scan the string to find a definition method, if one exists.
      Returns NOT_FOUND if no definition method is found, otherwise returns
      the enum for the found definition method.
     */
    private DefinitionMethod getDefininitionMethod(String query) {
        String upperQuery = query.toUpperCase().replaceAll("\\s{2,}", " ").trim();

        for (String method : SQL_DEFINITION_METHODS) {
            if (upperQuery.startsWith(method)) {
                return DefinitionMethod.valueOf(method.replace(' ', '_'));
            }
        }
        return DefinitionMethod.NOT_FOUND;
    }

    /*
      Find the target entity in a query, given a substring that occurs right before it.
      Returns N/A if no target is found, otherwise returns the target.
     */
    private String findTargetEntityAfter(String query, String after) {
        // Regex to find the first word (containing only alphanumeric, period, or underscore characters)
        // that occurs after the String after
        Pattern findTablePattern = Pattern.compile(Pattern.quote(after) + " ([\\w.]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = findTablePattern.matcher(query.replaceAll("\\s{2,}", " "));
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "N/A";
        }
    }
}
