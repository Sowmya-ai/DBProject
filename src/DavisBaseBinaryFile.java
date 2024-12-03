
import java.io.RandomAccessFile;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import java.util.ArrayList;
import java.util.Arrays;
import static java.lang.System.out;
import java.util.List;
import java.util.Map;

public class DavisBaseBinaryFile {
   public static String systemColumnsFile = "davisbase_columns";
   public static String systemTablesFile = "davisbase_tables";
   public static boolean showRowId = false;
   public static boolean isSystemInitialized = false;

   RandomAccessFile file;

   public DavisBaseBinaryFile(RandomAccessFile file) {
      this.file = file;
   }

   /* This static variable controls page size. */
   static int pageSizePower = 9;
   /* the page size is always a power of 2. */
   static int pageSize = (int) Math.pow(2, pageSizePower);

   public boolean recordExists(MetaData tablemetaData, List<String> columNames, SpecialCondition condition) throws IOException{

   BPlusOneTree bPlusOneTree = new BPlusOneTree(file, tablemetaData.rootPgNo, tablemetaData.tabName);
   for(Integer pageNo :  bPlusOneTree.getAllLeaves(condition))
   {
         Page page = new Page(file,pageNo);
         for(TableRecord record : page.getPageRecords())
         {
            if(condition!=null)
            {
               if(!condition.chkCondt(record.getAttributes().get(condition.columnOrdinal).fldVal))
                  continue;
            }
           return true;
         }
   }
   return false;

   }

   /**
    * This method is used to update records with list of new Values passed as an argument
    * @param tablemetaData
    * @param condition
    * @param colNames
    * @param newVal
    * @return
    * @throws IOException
    */
   public int updateRecords(MetaData tablemetaData,SpecialCondition condition, 
                  List<String> colNames, List<String> newVal) throws IOException
   {
      int count = 0;
      List<Integer> ordinalPostions = tablemetaData.getOrdinalPostions(colNames);

      //map new values to column ordinal position
      int k=0;
      Map<Integer,TableAttribute> newValueMap = new HashMap<>();

      for(String strnewValue:newVal){
           int index = ordinalPostions.get(k);

         try{
                newValueMap.put(index,
                      new TableAttribute(tablemetaData.colNameAttrs.get(index).dType,strnewValue));
                      }
                      catch (Exception e) {
							System.out.println("! Invalid data format for " + tablemetaData.colNames.get(index) + " values: "
									+ strnewValue);
							return count;
						}

         k++;
      }
      BPlusOneTree bPlusOneTree = new BPlusOneTree(file, tablemetaData.rootPgNo,tablemetaData.tabName);
      for(Integer pageNo :  bPlusOneTree.getAllLeaves(condition))
      {
            short deleteCountPerPage = 0;
            Page page = new Page(file,pageNo);
            for(TableRecord record : page.getPageRecords())
            {
               if(condition!=null)
               {
                  if(!condition.chkCondt(record.getAttributes().get(condition.columnOrdinal).fldVal))
                     continue;
               }
               count++;
               for(int i :newValueMap.keySet())
               {
                  TableAttribute oldValue = record.getAttributes().get(i);
                  int rowId = record.rId;
                  if((record.getAttributes().get(i).dt == DataTypes.TEXT
                   && record.getAttributes().get(i).fldVal.length() == newValueMap.get(i).fldVal.length())
                     || (record.getAttributes().get(i).dt != DataTypes.NULL && record.getAttributes().get(i).dt != DataTypes.TEXT)
                  ){
                     page.updateRecords(record,i,newValueMap.get(i).fldValByte);
                  }
                  else{
                   //Delete the record and insert a new one, update indexes
                     page.deleteTableRecord(tablemetaData.tabName ,
                     Integer.valueOf(record.pgHeaderIndx - deleteCountPerPage).shortValue());
                     deleteCountPerPage++;
                     List<TableAttribute> attrs = record.getAttributes();
                     TableAttribute attr = attrs.get(i);
                     attrs.remove(i);
                     attr = newValueMap.get(i);
                     attrs.add(i, attr);
                    rowId =  page.addTbRows(tablemetaData.tabName , attrs);
                }
                
                if(tablemetaData.colNameAttrs.get(i).hasIdx && condition!=null){
                  RandomAccessFile indexFile = new RandomAccessFile(TableUtils.getIndexFilePath(tablemetaData.colNameAttrs.get(i).tblName, tablemetaData.colNameAttrs.get(i).colName), "rw");
                  BTree bTree = new BTree(indexFile);
                  bTree.deleteRow(oldValue,record.rId);
                  bTree.insertRow(newValueMap.get(i), rowId);
                  indexFile.close();
                }
                
               }
             }
      }
    
      if(!tablemetaData.tabName.equals(systemTablesFile) && !tablemetaData.tabName.equals(systemColumnsFile))
          System.out.println("* " + count+" record(s) updated.");
          
         return count;

   }

   /**
    * This static method creates the DavisBase data storage container and then
    * initializes two .tbl files to implement the two system tables,
    * davisbase_tables and davisbase_columns
    */
   public static void initializeSystemTables() {

      /** Create data directory at the current OS location to hold */
      try {
         File dataDir = new File("data");
         dataDir.mkdir();
         String[] oldTableFiles;
         oldTableFiles = dataDir.list();
         for (int i = 0; i < oldTableFiles.length; i++) {
            File anOldFile = new File(dataDir, oldTableFiles[i]);
            anOldFile.delete();
         }
      } catch (SecurityException se) {
         out.println("Unable to create data container directory");
         out.println(se);
      }

      /** Create davisbase_tables system catalog */
      try {

         int currentPageNo = 0;

         RandomAccessFile davisbaseTablesCatalog = new RandomAccessFile(
                 TableUtils.getTablePath(systemTablesFile), "rw");
         Page.addNewPage(davisbaseTablesCatalog, PageType.LEAF, -1, -1);
         Page page = new Page(davisbaseTablesCatalog,currentPageNo);

         page.addTbRows(systemTablesFile,Arrays.asList(new TableAttribute[] {
                 new TableAttribute(DataTypes.TEXT, DavisBaseBinaryFile.systemTablesFile),
                 new TableAttribute(DataTypes.INT, "2"),
                 new TableAttribute(DataTypes.SMALLINT, "0"),
                 new TableAttribute(DataTypes.SMALLINT, "0")
         }));

         page.addTbRows(systemTablesFile,Arrays.asList(new TableAttribute[] {
                 new TableAttribute(DataTypes.TEXT, DavisBaseBinaryFile.systemColumnsFile),
                 new TableAttribute(DataTypes.INT, "11"),
                 new TableAttribute(DataTypes.SMALLINT, "0"),
                 new TableAttribute(DataTypes.SMALLINT, "2") }));

         davisbaseTablesCatalog.close();
      } catch (Exception e) {
         out.println("Unable to create the database_tables file");
         out.println(e);


      }

      /** Create davisbase_columns systems catalog */
      try {
         RandomAccessFile davisbaseColumnsCatalog = new RandomAccessFile(
                 TableUtils.getTablePath(systemColumnsFile), "rw");
         Page.addNewPage(davisbaseColumnsCatalog, PageType.LEAF, -1, -1);
         Page page = new Page(davisbaseColumnsCatalog, 0);

         short ordinal_position = 1;

         //Add new columns to davisbase_tables
         page.addNewCols(new ColumnInformation(systemTablesFile,DataTypes.TEXT, "table_name", true, false, ordinal_position++));
         page.addNewCols(new ColumnInformation(systemTablesFile,DataTypes.INT, "record_count", false, false, ordinal_position++));
         page.addNewCols(new ColumnInformation(systemTablesFile,DataTypes.SMALLINT, "avg_length", false, false, ordinal_position++));
         page.addNewCols(new ColumnInformation(systemTablesFile,DataTypes.SMALLINT, "root_page", false, false, ordinal_position++));

         //Add new columns to davisbase_columns

         ordinal_position = 1;

         page.addNewCols(new ColumnInformation(systemColumnsFile,DataTypes.TEXT, "table_name", false, false, ordinal_position++));
         page.addNewCols(new ColumnInformation(systemColumnsFile,DataTypes.TEXT, "column_name", false, false, ordinal_position++));
         page.addNewCols(new ColumnInformation(systemColumnsFile,DataTypes.SMALLINT, "data_type", false, false, ordinal_position++));
         page.addNewCols(new ColumnInformation(systemColumnsFile,DataTypes.SMALLINT, "ordinal_position", false, false, ordinal_position++));
         page.addNewCols(new ColumnInformation(systemColumnsFile,DataTypes.TEXT, "is_nullable", false, false, ordinal_position++));
         page.addNewCols(new ColumnInformation(systemColumnsFile,DataTypes.SMALLINT, "column_key", false, true, ordinal_position++));
         page.addNewCols(new ColumnInformation(systemColumnsFile,DataTypes.SMALLINT, "is_unique", false, false, ordinal_position++));

         davisbaseColumnsCatalog.close();
         isSystemInitialized = true;
      } catch (Exception e) {
         out.println("Unable to create the database_columns file");
         out.println(e);
      }
   }

   /**
    * This method is used to find the root page manually
    * @param binaryFile
    * @return
    */
   public static int getRootPage(RandomAccessFile binaryFile) {
     int rootpage = 0;
      try {   
         for (int i = 0; i < binaryFile.length() / DavisBaseBinaryFile.pageSize; i++) {
            binaryFile.seek(i * DavisBaseBinaryFile.pageSize + 0x0A);
            int a =binaryFile.readInt();
          
            if (a == -1) {
               return i;
            }
         }
         return rootpage;
      } catch (Exception e) {
         out.println("error while getting root page no ");
         out.println(e);
      }
      return -1;
   }

   /**
    * This method is used to select the records from the table
    * @param tablemetaData
    * @param columNames
    * @param condition
    * @throws IOException
    */
      public void selectRecords(MetaData tableMetaData, List<String> columnNames, SpecialCondition condition) throws IOException {
  
      // The select order might be different from the table ordinal position
      List<Integer> ordinalPositions = tableMetaData.getOrdinalPostions(columnNames);
  
      List<Integer> printPosition = new ArrayList<>();
      int columnPrintLength = 0;
      printPosition.add(columnPrintLength);
      int totalTablePrintLength = 0;
  
      if (showRowId) {
          //System.out.println("[DEBUG] Row ID column is included.");
          System.out.print("rowid");
          System.out.print(TableUtils.printSeparator(" ", 5));
          printPosition.add(10);
          totalTablePrintLength += 10;
      }
  
      for (int i : ordinalPositions) {
          String columnName = tableMetaData.colNameAttrs.get(i).colName;
          columnPrintLength = Math.max(
              columnName.length(),
              tableMetaData.colNameAttrs.get(i).dType.getPrintOffset()
          ) + 5;
          printPosition.add(columnPrintLength);
          System.out.print(columnName);
          System.out.print(TableUtils.printSeparator(" ", columnPrintLength - columnName.length()));
          totalTablePrintLength += columnPrintLength;
  
          //System.out.println("[DEBUG] Column: " + columnName + ", Print Length: " + columnPrintLength);
      }
      System.out.println();
      System.out.println(TableUtils.printSeparator("-", totalTablePrintLength));
      //System.out.println("[DEBUG] Table header printed.");
  
      BPlusOneTree bPlusOneTree = new BPlusOneTree(file, tableMetaData.rootPgNo, tableMetaData.tabName);
      //System.out.println("[DEBUG] BPlusOneTree initialized.");
  
      String currentValue = "";
      for (Integer pageNo : bPlusOneTree.getAllLeaves(condition)) {
          //System.out.println("[DEBUG] Processing page number: " + pageNo);
  
          Page page = new Page(file, pageNo);
          for (TableRecord record : page.getPageRecords()) {
              //System.out.println("[DEBUG] Checking record with Row ID: " + record.rowId);
  
              if (condition != null) {
                  boolean matchesCondition = evaluateConditionTree(condition, record);
                  //System.out.println("[DEBUG] Record matches condition: " + matchesCondition);
                  if (!matchesCondition) {
                      continue;
                  }
              }
  
              int columnCount = 0;
              if (showRowId) {
                  currentValue = Integer.valueOf(record.rId).toString();
                  System.out.print(currentValue);
                  System.out.print(TableUtils.printSeparator(" ", printPosition.get(++columnCount) - currentValue.length()));
                  //System.out.println("[DEBUG] Printed Row ID: " + currentValue);
              }
  
              for (int i : ordinalPositions) {
                  currentValue = record.getAttributes().get(i).fldVal;
                  System.out.print(currentValue);
                  System.out.print(TableUtils.printSeparator(" ", printPosition.get(++columnCount) - currentValue.length()));
                  //System.out.println("[DEBUG] Printed Column Value: " + currentValue + " for Column Index: " + i);
              }
              //System.out.println("[DEBUG] Finished printing record with Row ID: " + record.rowId);
              System.out.println();
          }
      }
      //System.out.println("[DEBUG] Finished processing all records.");
      System.out.println();
  }

private boolean evaluateConditionTree(SpecialCondition condition, TableRecord record) {
   if (!condition.isCompound) {
       // Evaluate a simple condition
       String currentValue = record.getAttributes().get(condition.columnOrdinal).fldVal;
       return condition.chkCondt(currentValue);
   }

   // Evaluate compound condition recursively
   boolean leftResult = evaluateConditionTree(condition.leftCondition, record);
   boolean rightResult = evaluateConditionTree(condition.rightCondition, record);

   if (condition.logicalOperator == SpecialCondition.LogicalOperator.AND) {
       return leftResult && rightResult;
   } else if (condition.logicalOperator == SpecialCondition.LogicalOperator.OR) {
       return leftResult || rightResult;
   }

   return false; // Default for unrecognized logical operator
}


  
}


