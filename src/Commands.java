
import static java.lang.System.out;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class Commands {

    public static void parseUserCommand(String userString) {

        /*
         * commandTokens is an array of Strings that contains one token per array
         * element The first token can be used to determine the type of command The
         * other tokens can be used to pass relevant parameters to each command-specific
         * method inside each case statement
         */
        ArrayList<String> commandTokens = new ArrayList<String>(Arrays.asList(userString.split(" ")));

        /*
         * This switch handles a very small list of hardcoded commands of known syntax.
         * You will want to rewrite this method to interpret more complex commands.
         */
        switch (commandTokens.get(0)) {
            case "show":
                if (commandTokens.get(1).equals("tables"))
                    parseUserCommand("select * from davisbase_tables");
                else if (commandTokens.get(1).equals("rowid")) {
                    DavisBaseBinaryFile.showRowId = true;
                    System.out.println("* Table Select will now include RowId.");
                } else
                    System.out.println("! I didn't understand the command: \"" + userString + "\"");
                break;
            case "select":
                parseString(userString);
                break;
            case "drop":
                if (commandTokens.get(1).equals("table"))
                    dropTable(userString);
                else if(commandTokens.get(1).equals("index"))
                    dropIndex(userString);
                break;
            case "create":
                if (commandTokens.get(1).equals("table"))
                    parseCreateTable(userString);
                else if (commandTokens.get(1).equals("index"))
                    parseCreateIdx(userString);
                break;
            case "update":
                parseUpdateTable(userString);
                break;
            case "insert":
                parseInsertTable(userString);
                break;
            case "delete":
                parseDeleteTable(userString);
                break;
            case "help":
                help();
                break;
            case "version":
                displayVersion();
                break;
            case "exit":
                Settings.setExit(true);
                break;
            case "quit":
                Settings.setExit(true);
                break;
            case "test":
                test();
                break;
            default:
                System.out.println("! I didn't understand the command: \"" + userString + "\"");
                break;
        }
    }
    public static void parseString(String queryStr) {
        String table_name = "";
        List<String> column_names = new ArrayList<>();
    
        // Split the query into tokens
        ArrayList<String> queryTokens = new ArrayList<>(Arrays.asList(queryStr.split(" ")));
        int i = 0;
    
        // Parse table name and columns
        for (i = 1; i < queryTokens.size(); i++) {
            if (queryTokens.get(i).equalsIgnoreCase("from")) {
                ++i;
                table_name = queryTokens.get(i);
                break;
            }
            if (!queryTokens.get(i).equals("*") && !queryTokens.get(i).equals(",")) {
                if (queryTokens.get(i).contains(",")) {
                    column_names.addAll(Arrays.asList(queryTokens.get(i).split(",")));
                } else {
                    column_names.add(queryTokens.get(i));
                }
            }
        }
    
        // Load table metadata
        MetaData tableMetaData = new MetaData(table_name);
        if (!tableMetaData.tabExists) {
            System.out.println("! Table does not exist");
            return;
        }
    
        SpecialCondition condition = null;
        try {
            // Extract condition using the updated method
            condition = getComplexConditionFromQuery(tableMetaData, queryStr);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return;
        }
    
        // If no specific columns are mentioned, select all
        if (column_names.isEmpty()) {
            column_names = tableMetaData.colNames;
        }
    
        try {
            RandomAccessFile tableFile = new RandomAccessFile(TableUtils.getTablePath(table_name), "r");
            DavisBaseBinaryFile tableBinaryFile = new DavisBaseBinaryFile(tableFile);
            tableBinaryFile.selectRecords(tableMetaData, column_names, condition);
            tableFile.close();
        } catch (IOException exception) {
            System.out.println("! Error selecting columns from table");
        }
    }
    private static SpecialCondition getComplexConditionFromQuery(MetaData tableMetaData, String query) throws Exception {
        if (!query.contains("where")) {
            return null; // No conditions
    }

String whereClause = query.substring(query.indexOf("where") + 6).trim();

        ArrayList<String> tokens = new ArrayList<>(Arrays.asList(whereClause.split("\\s+")));
    
        return parseConditionTree(tableMetaData, tokens);
    }
    
    // Parses a condition tree from tokens
    private static SpecialCondition parseConditionTree(MetaData tableMetaData, ArrayList<String> tokens) throws Exception {
        SpecialCondition currentCondition = null;
        SpecialCondition.LogicalOperator logicalOperator = SpecialCondition.LogicalOperator.NONE;
        boolean negate = false;
        for (int i = 0; i < tokens.size(); i++) {

            String token = tokens.get(i);
            if (token.equalsIgnoreCase("not")) {
                // Toggle negation for the next condition
                negate = true;
            }
            else if (token.equalsIgnoreCase("and") || token.equalsIgnoreCase("or")) {
                logicalOperator = token.equalsIgnoreCase("and")
                        ? SpecialCondition.LogicalOperator.AND
                        : SpecialCondition.LogicalOperator.OR;
            } else if (SpecialCondition.supportedOperators.length > 0 && containsOperator(token)) {
                // Extract individual condition
                String column = tokens.get(i-1);
                String operator = extractOperator(token);
                String value = tokens.get(i+1).replace("'", "").replace("\"", "");
                int columnIndex = tableMetaData.colNames.indexOf(column);
                SpecialCondition condition = new SpecialCondition(tableMetaData.colNameAttrs.get(columnIndex).dType);
                condition.setColumName(column);
                condition.setColumnOrdinal(columnIndex);
                condition.setOp(operator);
                condition.setConditionValue(value);
                if (negate) {
                    condition.setNegation(true); // Apply negation
                    negate = false; // Reset negation flag after applying
                }
    
                if (!tableMetaData.columnExists(new ArrayList<>(Collections.singletonList(column)))) {
                    throw new Exception("! Invalid Table/Column : " + tableMetaData.tabName + " . " + column);
                }
    
                if (currentCondition == null) {
                    currentCondition = condition;
                } else {
                    // Combine with previous condition
                    currentCondition = new SpecialCondition(logicalOperator, currentCondition, condition);
                }
    
                i++; // Skip the value token
            }
        }
    
        return currentCondition;
    }
    
    // Utility to check if a token contains an operator
    private static boolean containsOperator(String token) {
        for (String op : SpecialCondition.supportedOperators) {
            if (token.contains(op)) {
                return true;
            }
        }
        return false;
    }
    
    // Extracts the operator from a token
    private static String extractOperator(String token) {
        for (String op : SpecialCondition.supportedOperators) {
            if (token.contains(op)) {
                return op;
            }
        }
        return "";
    }
    public static void parseCreateIdx(String createIdxStr) 
    {
        ArrayList<String> createIndexTokens = new ArrayList<>(Arrays.asList(createIdxStr.split(" ")));
        try 
        {
            // Validate the basic structure of the command
            if (!createIndexTokens.get(0).equalsIgnoreCase("create") ||
            !createIndexTokens.get(1).equalsIgnoreCase("index") ||
            !createIndexTokens.get(2).matches("[a-zA-Z0-9_]+") || // Index name validation
            !createIndexTokens.get(3).equalsIgnoreCase("on") ||
            !createIdxStr.contains("(") || !createIdxStr.contains(")")) {
            System.out.println("! Syntax Error");
            System.out.println("Expected Syntax: CREATE INDEX index_name ON table_name (column_name);");
            return;
            }

            String indexName = createIndexTokens.get(2); // Extract the index name
            String tableName = createIdxStr
                .substring(createIdxStr.indexOf("on") + 3, createIdxStr.indexOf("(")).trim();
            String columnName = createIdxStr
                .substring(createIdxStr.indexOf("(") + 1, createIdxStr.indexOf(")")).trim();

            // Check if the index already exists
            if (new File(TableUtils.getIndexFilePath(tableName, columnName)).exists()) {
            System.out.println("! Index already exists");
            return;
            }

            RandomAccessFile tableFile = new RandomAccessFile(TableUtils.getTablePath(tableName), "rw");

            MetaData metaData = new MetaData(tableName);

           if (!metaData.tabExists) {
                System.out.println("! Invalid Table name");
                tableFile.close();
                return;
            }

            int columnOrdinal = metaData.colNames.indexOf(columnName);

            if (columnOrdinal < 0) {
                System.out.println("! Invalid column name");
                tableFile.close();
                return;
            }

            // Create the index file with the specified index name
            RandomAccessFile indexFile = new RandomAccessFile("data/" + indexName + ".ndx", "rw");
            Page.addNewPage(indexFile, PageType.LEAFINDEX, -1, -1);

            if (metaData.recCount > 0) {
                BPlusOneTree bPlusOneTree = new BPlusOneTree(tableFile, metaData.rootPgNo, metaData.tabName);
                for(int pageNo : bPlusOneTree.getAllLeaves()) {
                    Page page = new Page(tableFile, pageNo);
                    BTree bTree = new BTree(indexFile);
                    for (TableRecord record : page.getPageRecords()) {
                        bTree.insertRow(record.getAttributes().get(columnOrdinal), record.rId);
                        }
                }
            }

            System.out.println("* Index \"" + indexName + "\" created on the column: " + columnName);
            indexFile.close();
            tableFile.close();

    } catch (IOException e) {
        System.out.println("! Error on creating Index");
        System.out.println(e.getMessage());
    }
}
    public static void dropIndex(String dropIndexStr) {
        ArrayList <String> dropIndxTokens = new ArrayList<>(Arrays.asList(dropIndexStr.split(" ")));

        // make sure the command is in the right format
        if (dropIndxTokens.size() != 5 || !dropIndxTokens.get(0).equalsIgnoreCase("drop") || !dropIndxTokens.get(1).equalsIgnoreCase("index") || !dropIndxTokens.get(3).equalsIgnoreCase("on")){
            System.out.println("! Invalid drop index command");
            return;
        } 

        String indxName = dropIndxTokens.get(2);
        String tableName = dropIndxTokens.get(4);

        try {
            if (!new File(TableUtils.getIndexFilePath(tableName, indxName)).exists()) {
                System.out.println("! Index " + indxName + " does not exists.");
                return;
            }

            // delete the indx file
            File indexFile = new File(TableUtils.getIndexFilePath(tableName, indxName));
            if (indexFile.delete()) {
                System.out.println("* Index file " + indxName + " deleted.");
            } else {
                System.out.println("! Failed to delete index file " + indxName + ".");
                return;
            }

            RandomAccessFile colCatalog = new RandomAccessFile(TableUtils.getTablePath(DavisBaseBinaryFile.systemColumnsFile), "rw");
            BPlusOneTree colTree = new BPlusOneTree(colCatalog, DavisBaseBinaryFile.getRootPage(colCatalog), DavisBaseBinaryFile.systemColumnsFile);

            SpecialCondition  cond = new SpecialCondition(DataTypes.TEXT);
            cond.setColumName("index_name");
            cond.setConditionValue(indxName);
            cond.setOp("=");

            boolean indxMetaDataRmv = false;
            for (Integer pageNo : colTree.getAllLeaves(cond)) {
                Page page = new Page(colCatalog, pageNo);
                List<TableRecord> recs = page.getPageRecords();

                for(TableRecord record : recs) {
                    if(record.getAttributes().get(0).fldVal.equals(indxName)) {
                        page.deleteTableRecord(DavisBaseBinaryFile.systemColumnsFile, record.pgHeaderIndx);
                        indxMetaDataRmv = true;
                        System.out.println("* Metadata for index " + indxName + " removed from system columns table.");
                    }
                }

                if(indxMetaDataRmv)
                    break;
            }

            if(!indxMetaDataRmv) {
                System.out.println("! Index metadata for " + indxName + "not found in system columns table");
            }
            colCatalog.close();
        } catch (IOException e){
            System.out.println("! Error while dropping index " + indxName + ": " + e.getMessage()); 
        }
    }
    public static void parseUpdateTable(String updateString) {
        ArrayList<String> updateTokens = new ArrayList<String>(Arrays.asList(updateString.split(" ")));

        String table_name = updateTokens.get(1);
        List<String> columnsToUpdate = new ArrayList<>();
        List<String> valueToUpdate = new ArrayList<>();

        if (!updateTokens.get(2).equals("set") || !updateTokens.contains("=")) {
            System.out.println("! Syntax error");
            System.out.println(
                    "Expected Syntax: UPDATE [table_name] SET [Column_name] = val1 where [column_name] = val2; ");
            return;
        }

        String updateColInfoString = updateString.split("set")[1].split("where")[0];

        List<String> column_newValueSet = Arrays.asList(updateColInfoString.split(","));

        for (String item : column_newValueSet) {
            columnsToUpdate.add(item.split("=")[0].trim());
            valueToUpdate.add(item.split("=")[1].trim().replace("\"", "").replace("'", ""));
        }

        MetaData metadata = new MetaData(table_name);

        if (!metadata.tabExists) {
            System.out.println("! Invalid Table name");
            return;
        }

        if (!metadata.columnExists(columnsToUpdate)) {
            System.out.println("! Invalid column name(s)");
            return;
        }

        SpecialCondition condition = null;
        try {

            condition = getConditionFromQuery(metadata, updateString);

        } catch (Exception e) {
            System.out.println(e.getMessage());
            return;

        }




        try {
            RandomAccessFile file = new RandomAccessFile(TableUtils.getTablePath(table_name), "rw");
            DavisBaseBinaryFile binaryFile = new DavisBaseBinaryFile(file);
            int noOfRecordsupdated = binaryFile.updateRecords(metadata, condition, columnsToUpdate, valueToUpdate);

            if(noOfRecordsupdated > 0)
            {
                List<Integer> allRowids = new ArrayList<>();
                for(ColumnInformation colInfo : metadata.colNameAttrs)
                {
                    for(int i=0;i<columnsToUpdate.size();i++)
                        if(colInfo.colName.equals(columnsToUpdate.get(i)) &&  colInfo.hasIdx)
                        {

                            // when there is no condition, All rows in the column gets updated the index value point to all rowids
                            if(condition == null)
                            {
                                //Delete the index file. TODO

                                if(allRowids.size() == 0)
                                {
                                    BPlusOneTree bPlusOneTree = new BPlusOneTree(file, metadata.rootPgNo, metadata.tabName);
                                    for (int pageNo : bPlusOneTree.getAllLeaves()) {
                                        Page currentPage = new Page(file, pageNo);
                                        for (TableRecord record : currentPage.getPageRecords()) {
                                            allRowids.add(record.rId);
                                        }
                                    }
                                }
                                //create a new index value and insert 1 index value with all rowids
                                RandomAccessFile indexFile = new RandomAccessFile(TableUtils.getIndexFilePath(table_name, columnsToUpdate.get(i)),
                                        "rw");
                                Page.addNewPage(indexFile, PageType.LEAFINDEX, -1, -1);
                                BTree bTree = new BTree(indexFile);
                                bTree.insertRow(new TableAttribute(colInfo.dType,valueToUpdate.get(i)), allRowids);
                            }
                        }
                }
            }

            file.close();

        } catch (Exception e) {
            out.println("Unable to update the " + table_name + " file");
            out.println(e);

        }


    }

    public static void parseInsertTable(String queryString) {
    // INSERT INTO TABLE table_name VALUES (value1, value2, value3, ...);
    ArrayList<String> insertTokens = new ArrayList<String>(Arrays.asList(queryString.split(" ")));

    // Check if the format is correct
    if (insertTokens.size() < 6 || !insertTokens.get(1).equalsIgnoreCase("INTO") || !insertTokens.get(2).equalsIgnoreCase("TABLE") || !insertTokens.get(4).equalsIgnoreCase("VALUES")) {
        System.out.println("! Syntax error");
        System.out.println("Please ensure there is a space after \" VALUES\"");
        System.out.println("Expected Syntax: INSERT INTO TABLE table_name VALUES (value1, value2, value3, ...);");
        return;
    }

    try {
        // Extract table name
        String tableName = insertTokens.get(3).trim();
        if (tableName.length() == 0) {
            System.out.println("! Table name cannot be empty");
            return;
        }

        // Fetch metadata to ensure the table exists
        MetaData dstMetaData = new MetaData(tableName);

        if (!dstMetaData.tabExists) {
            System.out.println("! Table does not exist.");
            return;
        }

        // Extract values from the query string
        String valuesString = queryString.substring(queryString.indexOf("VALUES") + 6).trim();
        valuesString = valuesString.substring(valuesString.indexOf("(") + 1, valuesString.indexOf(")")).trim();

        // Split the values string by commas, which will separate individual values
        ArrayList<String> valueTokens = new ArrayList<String>(Arrays.asList(valuesString.split(",")));

         // Ensure that the number of values matches the number of columns in the table
        if (valueTokens.size() != dstMetaData.colNameAttrs.size()) {
            System.out.println("! Number of values does not match the number of columns in the table.");
            return;
        }

        // Clean up extra spaces or quotes around values
        for (int i = 0; i < valueTokens.size(); i++) {
            valueTokens.set(i, valueTokens.get(i).trim());

            // If the column is TEXT, we expect the value to be surrounded by quotes.
            ColumnInformation colInfo = dstMetaData.colNameAttrs.get(i);
            if (colInfo.dType == DataTypes.TEXT) {
                // Ensure that the value is surrounded by quotes for TEXT columns
                if (!valueTokens.get(i).startsWith("\"") || !valueTokens.get(i).endsWith("\"")) {
                    System.out.println("! Error: Value for column " + colInfo.colName + " must be enclosed in quotes (TEXT type).");
                    return;
                }
                // Remove quotes for TEXT columns (value is valid, just clean it for insertion)
                valueTokens.set(i, valueTokens.get(i).substring(1, valueTokens.get(i).length() - 1));
            }
            // For other types, no modification is needed (INT, FLOAT, etc.)
        }

       

        // Prepare attributes to insert
        List<TableAttribute> attributeToInsert = new ArrayList<>();

        for (int i = 0; i < dstMetaData.colNameAttrs.size(); i++) {
            ColumnInformation colInfo = dstMetaData.colNameAttrs.get(i);
            String value = valueTokens.get(i);

            // Handle NULL values
            if (value.equals("null")) {
                if (!colInfo.isNullable) {
                    System.out.println("! Cannot insert NULL into non-nullable column: " + colInfo.colName);
                    return;
                }
                value = "NULL"; // represent NULL explicitly
            }

            try {
                // Validate data type using the isValidDataType function
                if (!isValidDataType(value, colInfo)) {
                    System.out.println("! Invalid data type for column " + colInfo.colName + ". Expected " + colInfo.dType + " but got: " + value);
                    return;
                }

                // Create a TableAttribute object with the column's data type and value
                TableAttribute attr = new TableAttribute(colInfo.dType, value);
                attributeToInsert.add(attr);
            } catch (Exception e) {
                System.out.println("! Invalid data format for column: " + colInfo.colName);
                return;
            }
        }

        // Perform the insertion into the table
        RandomAccessFile dstTable = new RandomAccessFile(TableUtils.getTablePath(tableName), "rw");
        int dstPageNo = BPlusOneTree.getPgNoForInsert(dstTable, dstMetaData.rootPgNo);
        Page dstPage = new Page(dstTable, dstPageNo);

        int rowNo = dstPage.addTbRows(tableName, attributeToInsert);

        // Update indexes if necessary
        if (rowNo != -1) {
            for (int i = 0; i < dstMetaData.colNameAttrs.size(); i++) {
                ColumnInformation col = dstMetaData.colNameAttrs.get(i);

                if (col.hasIdx) {
                    RandomAccessFile indexFile = new RandomAccessFile(TableUtils.getIndexFilePath(tableName, col.colName), "rw");
                    BTree bTree = new BTree(indexFile);
                    bTree.insertRow(attributeToInsert.get(i), rowNo);
                }
            }
        }

        dstTable.close();

        // Confirm successful insertion
        if (rowNo != -1)
            System.out.println("* Record Inserted");

    } catch (Exception ex) {
        System.out.println("! Error while inserting record");
        System.out.println(ex);
    }
}


public static boolean isValidDataType(String value, ColumnInformation colInfo) {
    try {
        switch (colInfo.dType) {
            case INT: // Change to DataTypes.INTEGER
                Integer.parseInt(value); // Validates if value is an integer
                return true; // Return true for valid integer
            case TEXT: // Change to DataTypes.TEXT
                return true; // Return true for valid text (no need for further validation)
            case FLOAT: // Change to DataTypes.FLOAT
                Float.parseFloat(value); // Validates if value is a float
                return true; // Return true for valid float
            default:
                return false; // Unknown data type, return false
        }
    } catch (NumberFormatException e) {
        return false; // If there's a parsing error, the value doesn't match the expected data type
    }
}

    private static void parseDeleteTable(String deleteTableString) {
        ArrayList<String> deleteTableTokens = new ArrayList<String>(Arrays.asList(deleteTableString.split(" ")));

        String tableName = "";

        try {

            if (!deleteTableTokens.get(1).equals("from") || !deleteTableTokens.get(2).equals("table")) {
                System.out.println("! Syntax Error");
                return;
            }

            tableName = deleteTableTokens.get(3);

            MetaData metaData = new MetaData(tableName);
            SpecialCondition condition = null;
            try {
                condition = getConditionFromQuery(metaData, deleteTableString);

            } catch (Exception e) {
                System.out.println(e);
                return;
            }
            RandomAccessFile tableFile = new RandomAccessFile(TableUtils.getTablePath(tableName), "rw");

            BPlusOneTree tree = new BPlusOneTree(tableFile, metaData.rootPgNo, metaData.tabName);
            List<TableRecord> deletedRecords = new ArrayList<TableRecord>();
            int count = 0;
            for (int pageNo : tree.getAllLeaves(condition)) {
                short deleteCountPerPage = 0;
                Page page = new Page(tableFile, pageNo);
                for (TableRecord record : page.getPageRecords()) {
                    if (condition != null) {
                        if (!condition.chkCondt(record.getAttributes().get(condition.columnOrdinal).fldVal))
                            continue;
                    }

                    deletedRecords.add(record);
                    page.deleteTableRecord(tableName,
                            Integer.valueOf(record.pgHeaderIndx - deleteCountPerPage).shortValue());
                    deleteCountPerPage++;
                    count++;
                }
            }

            // update Index

            // if there is no condition, all the rows will be deleted.
            // so just delete the existing index files on the table and create new ones
            if (condition == null) {
                // TODO delete exisitng index files for the table
                //and create new ones;




            } else {
                for (int i = 0; i < metaData.colNameAttrs.size(); i++) {
                    if (metaData.colNameAttrs.get(i).hasIdx) {
                        RandomAccessFile indexFile = new RandomAccessFile(TableUtils.getIndexFilePath(tableName, metaData.colNameAttrs.get(i).colName), "rw");
                        BTree bTree = new BTree(indexFile);
                        for (TableRecord record : deletedRecords) {
                            bTree.deleteRow(record.getAttributes().get(i),record.rId);
                        }
                    }
                }
            }

            System.out.println();
            tableFile.close();
            System.out.println(count + " record(s) deleted!");

        } catch (Exception e) {
            System.out.println("! Error on deleting rows in table : " + tableName);
            System.out.println(e.getMessage());
        }

    }

    public static void test() {
        Scanner scan = new Scanner(System.in);
        parseUserCommand("create table test (id int, name text)");
        scan.nextLine();
        parseUserCommand("create index on test (name)");
        scan.nextLine();
        for (int i = 1; i < 35; i++)
        {
            //   System.out.println(i);
            parseUserCommand("insert into test (id , name) values (" + (i) + ", "+ i + "'arun' )");

            //scan.nextLine();
        }
        parseUserCommand("show tables");

        scan.nextLine();

    }

    private static SpecialCondition getConditionFromQuery(MetaData tableMetaData, String query) throws Exception {
        if (query.contains("where")) {
            SpecialCondition condition = new SpecialCondition(DataTypes.TEXT);
            String whereClause = query.substring(query.indexOf("where") + 6, query.length());
            ArrayList<String> whereClauseTokens = new ArrayList<String>(Arrays.asList(whereClause.split(" ")));

            // WHERE NOT column operator value
            if (whereClauseTokens.get(0).equalsIgnoreCase("not")) {
                condition.setNegation(true);
            }


            for (int i = 0; i < SpecialCondition.supportedOperators.length; i++) {
                if (whereClause.contains(SpecialCondition.supportedOperators[i])) {
                    whereClauseTokens = new ArrayList<String>(
                            Arrays.asList(whereClause.split(SpecialCondition.supportedOperators[i])));
                    {	condition.setOp(SpecialCondition.supportedOperators[i]);
                        condition.setConditionValue(whereClauseTokens.get(1).trim());
                        condition.setColumName(whereClauseTokens.get(0).trim());
                        break;
                    }

                }
            }


            if (tableMetaData.tabExists
                    && tableMetaData.columnExists(new ArrayList<String>(Arrays.asList(condition.columnName)))) {
                condition.columnOrdinal = tableMetaData.colNames.indexOf(condition.columnName);
                condition.dataType = tableMetaData.colNameAttrs.get(condition.columnOrdinal).dType;
            } else {
                throw new Exception(
                        "! Invalid Table/Column : " + tableMetaData.tabName + " . " + condition.columnName);
            }
            return condition;
        } else
            return null;
    }


    public static void help() {
        out.println(TableUtils.printSeparator("*", 80));
        out.println("SUPPORTED COMMANDS\n");
        out.println("All commands below are case insensitive\n");

        out.println("SHOW TABLES;");
        out.println("\tDisplay the names of all tables.\n");

        out.println("CREATE TABLE <table_name> (<column_name> <data_type> <not_null> <unique>);");
        out.println("\tCreates a table with the given columns.\n");

        out.println("DROP TABLE <table_name>;");
        out.println("\tRemove table data (i.e. all records) and its schema.\n");

        out.println("UPDATE TABLE <table_name> SET <column_name> = <value> [WHERE <condition>];");
        out.println("\tModify records data whose optional <condition>");
        out.println("\tis <column_name> = <value>.\n");

        out.println("INSERT INTO <table_name> (<column_list>) VALUES (<values_list>);");
        out.println("\tInserts a new record into the table with the given values for the given columns.\n");

        out.println("SELECT <column_list> FROM <table_name> [WHERE <condition>];");
        out.println("\tDisplay table records whose optional <condition>");
        out.println("\tis <column_name> = <value>.\n");

        out.println("VERSION;");
        out.println("\tDisplay the program version.\n");

        out.println("HELP;");
        out.println("\tDisplay this help information.\n");

        out.println("EXIT;");
        out.println("\tExit the program.\n");

        out.println(TableUtils.printSeparator("*", 80));
    }

    public static void dropTable(String dropTableString) {
        System.out.println("STUB: This is the dropTable method.");
        System.out.println("\tParsing the string:\"" + dropTableString + "\"");

        String[] tokens = dropTableString.split(" ");
        if(!(tokens[0].trim().equalsIgnoreCase("DROP") && tokens[1].trim().equalsIgnoreCase("TABLE"))) {
            System.out.println("Error");
            return;
        }

        ArrayList<String> dropTableTokens = new ArrayList<String>(Arrays.asList(dropTableString.split(" ")));
        String tableName = dropTableTokens.get(2);


        parseDeleteTable("delete from table "+ DavisBaseBinaryFile.systemTablesFile + " where table_name = '"+tableName+"' ");
        parseDeleteTable("delete from table "+ DavisBaseBinaryFile.systemColumnsFile + " where table_name = '"+tableName+"' ");
        File tableFile = new File("data/"+tableName+".tbl");
        if(tableFile.delete()){
            System.out.println("table deleted");
        }else System.out.println("table doesn't exist");


        File f = new File("data/");
        File[] matchingFiles = f.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith(tableName) && name.endsWith("ndx");
            }
        });
        boolean iFlag = false;
        for (File file : matchingFiles) {
            if(file.delete()){
                iFlag = true;
                System.out.println("index deleted");
            }
        }
        if(iFlag)
            System.out.println("drop "+tableName);
        else
            System.out.println("index doesn't exist");





        //page.DeleteTableRecord(dropTableTokens.get(1) ,record.pageHeaderIndex);
    }

    public static void parseCreateTable(String createTableString) {

        ArrayList<String> createTableTokens = new ArrayList<String>(Arrays.asList(createTableString.split(" ")));
        // table and () check
        if (!createTableTokens.get(1).equals("table")) {
            System.out.println("! Syntax Error");
            return;
        }
        String tableName = createTableTokens.get(2);
        if (tableName.trim().length() == 0) {
            System.out.println("! Tablename cannot be empty");
            return;
        }
        try {

            if (tableName.indexOf("(") > -1) {
                tableName = tableName.substring(0, tableName.indexOf("("));
            }

            List<ColumnInformation> lstcolumnInformation = new ArrayList<>();
            ArrayList<String> columnTokens = new ArrayList<String>(Arrays.asList(createTableString
                    .substring(createTableString.indexOf("(") + 1, createTableString.length() - 1).split(",")));

            short ordinalPosition = 1;

            String primaryKeyColumn = "";

            for (String columnToken : columnTokens) {

                ArrayList<String> colInfoToken = new ArrayList<String>(Arrays.asList(columnToken.trim().split(" ")));
                ColumnInformation colInfo = new ColumnInformation();
                colInfo.tblName = tableName;
                colInfo.colName = colInfoToken.get(0);
                colInfo.isNullable = true;
                colInfo.dType = DataTypes.get(colInfoToken.get(1).toUpperCase());
                for (int i = 0; i < colInfoToken.size(); i++) {

                    if ((colInfoToken.get(i).equals("null"))) {
                        colInfo.isNullable = true;
                    }
                    if (colInfoToken.get(i).contains("not") && (colInfoToken.get(i + 1).contains("null"))) {
                        colInfo.isNullable = false;
                        i++;
                    }

                    if ((colInfoToken.get(i).equals("unique"))) {
                        colInfo.unique = true;
                    } else if (colInfoToken.get(i).contains("primary") && (colInfoToken.get(i + 1).contains("key"))) {
                        colInfo.isPrimKey = true;
                        colInfo.unique = true;
                        colInfo.isNullable = false;
                        primaryKeyColumn = colInfo.colName;
                        i++;
                    }

                }
                colInfo.ordPos = ordinalPosition++;
                lstcolumnInformation.add(colInfo);

            }

            // update sys file
            RandomAccessFile davisbaseTablesCatalog = new RandomAccessFile(
                    TableUtils.getTablePath(DavisBaseBinaryFile.systemTablesFile), "rw");
            MetaData davisbaseTableMetaData = new MetaData(DavisBaseBinaryFile.systemTablesFile);

            int pageNo = BPlusOneTree.getPgNoForInsert(davisbaseTablesCatalog, davisbaseTableMetaData.rootPgNo);

            Page page = new Page(davisbaseTablesCatalog, pageNo);

            int rowNo = page.addTbRows(DavisBaseBinaryFile.systemTablesFile,
                    Arrays.asList(new TableAttribute[] { new TableAttribute(DataTypes.TEXT, tableName), // DavisBaseBinaryFile.systemTablesFile->test
                            new TableAttribute(DataTypes.INT, "0"), new TableAttribute(DataTypes.SMALLINT, "0"),
                            new TableAttribute(DataTypes.SMALLINT, "0") }));
            davisbaseTablesCatalog.close();

            if (rowNo == -1) {
                System.out.println("! Duplicate table Name");
                return;
            }
            RandomAccessFile tableFile = new RandomAccessFile(TableUtils.getTablePath(tableName), "rw");
            Page.addNewPage(tableFile, PageType.LEAF, -1, -1);
            tableFile.close();

            RandomAccessFile davisbaseColumnsCatalog = new RandomAccessFile(
                    TableUtils.getTablePath(DavisBaseBinaryFile.systemColumnsFile), "rw");
            MetaData davisbaseColumnsMetaData = new MetaData(DavisBaseBinaryFile.systemColumnsFile);
            pageNo = BPlusOneTree.getPgNoForInsert(davisbaseColumnsCatalog, davisbaseColumnsMetaData.rootPgNo);

            Page page1 = new Page(davisbaseColumnsCatalog, pageNo);

            for (ColumnInformation column : lstcolumnInformation) {
                page1.addNewCols(column);
            }

            davisbaseColumnsCatalog.close();

            System.out.println("* Table created");

            if (primaryKeyColumn.length() > 0) {
                parseCreateIdx("create index on " + tableName + "(" + primaryKeyColumn + ")");
            }
        } catch (Exception e) {

            System.out.println("! Error on creating Table");
            System.out.println(e.getMessage());
            parseDeleteTable("delete from table " + DavisBaseBinaryFile.systemTablesFile + " where table_name = '" + tableName
                    + "' ");
            parseDeleteTable("delete from table " + DavisBaseBinaryFile.systemColumnsFile + " where table_name = '" + tableName
                    + "' ");
        }

    }

    public static void displayVersion() {
        System.out.println("SQVeryLite Version " + Settings.getVersion());
        System.out.println(Settings.getCopyright());
    }
}
