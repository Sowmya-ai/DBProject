/* This class is used to read and change the table's meta data (davisbase tables and davisbas columns).
 We must ensure that the meta data 
 - Record count 
 - root page is updated. 
 When a Record is inserted or deleted, the table's number is incremented.
 */


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;

 
public class MetaData{

    public int recCount; //total Record
    public List<TableRecord> colData; //column data for table (Table Record object)
    public List<ColumnInformation> colNameAttrs; //column name attributes
    public List<String> colNames; //column name
    public String tabName; //table name
    public boolean tabExists; // to verfy if table exists or not
    public int rootPgNo; //root page number
    public int lstRowId; // last row id of table


    public MetaData(String tabName)
    {
        this.tabName = tabName;
        tabExists = false;
        try {

            RandomAccessFile dbtabCatalog = new RandomAccessFile(
            TableUtils.getTablePath(DavisBaseBinaryFile.systemTablesFile), "r");
            
            //get the root page of the table
            int rootPgNo = DavisBaseBinaryFile.getRootPage(dbtabCatalog);
           
            BPlusOneTree bplusOneTree = new BPlusOneTree(dbtabCatalog, rootPgNo,tabName);
            //search through all leaf papges in davisbase_tables
            for (Integer pageNo : bplusOneTree.getAllLeaves()) {
               Page page = new Page(dbtabCatalog, pageNo);
               //search theough all the records in each page
               for (TableRecord Record : page.getPageRecords()) {
                   //if the Record with table is found, get the root page No and Record count; break the loop
                  if (new String(Record.getAttributes().get(0).fldVal).equals(tabName)) {
                    this.rootPgNo = Integer.parseInt(Record.getAttributes().get(3).fldVal);
                    recCount = Integer.parseInt(Record.getAttributes().get(1).fldVal);
                    tabExists = true;
                     break;
                  }
               }
               if(tabExists)
                break;
            }
   
            dbtabCatalog.close();
            if(tabExists)
            {
               loadColumnData();
            } else {
               throw new Exception("Table does not exist.");
            }
            
         } catch (Exception e) {
           // System.out.println("! Error while checking Table " + tabName + " exists.");
            //debug: System.out.println(e);
         }
    }

    public List<Integer> getOrdinalPostions(List<String> columns){
				List<Integer> ordPostions = new ArrayList<>();
				for(String column :columns)
				{
					ordPostions.add(colNames.indexOf(column));
                }
                return ordPostions;
    }

    //loads the column information for thr table
    private void loadColumnData() {
        try {
  
           RandomAccessFile dbColumnsCatalog = new RandomAccessFile(
            TableUtils.getTablePath(DavisBaseBinaryFile.systemColumnsFile), "r");
           int rootPgNo = DavisBaseBinaryFile.getRootPage(dbColumnsCatalog);
  
           colData = new ArrayList<>();
           colNameAttrs = new ArrayList<>();
           colNames = new ArrayList<>();
           BPlusOneTree bPlusOneTree = new BPlusOneTree(dbColumnsCatalog, rootPgNo,tabName);
         
           /* Get all columns from the davisbase_columns, loop through all the leaf pages 
           and find the records with the table name */
           for (Integer pageNo : bPlusOneTree.getAllLeaves()) {
           
             Page page = new Page(dbColumnsCatalog, pageNo);
              
              for (TableRecord Record : page.getPageRecords()) {
                  
                 if (Record.getAttributes().get(0).fldVal.equals(tabName)) {
                    {
                     //set column information in the data members of the class
                       colData.add(Record);
                       colNames.add(Record.getAttributes().get(1).fldVal);
                       ColumnInformation columnInfo = new ColumnInformation(
                                          tabName  
                                        , DataTypes.get(Record.getAttributes().get(2).fldVal)
                                        , Record.getAttributes().get(1).fldVal
                                        , Record.getAttributes().get(6).fldVal.equals("YES")
                                        , Record.getAttributes().get(4).fldVal.equals("YES")
                                        , Short.parseShort(Record.getAttributes().get(3).fldVal)
                                        );
                                          
                    if(Record.getAttributes().get(5).fldVal.equals("PRI"))
                          columnInfo.setAsPrimaryKey();
                        
                     colNameAttrs.add(columnInfo);                      
                    }
                 }
              }
           }
  
           dbColumnsCatalog.close();
        } catch (Exception e) {
           System.out.println("! Error while getting column data for " + tabName);
        }
     }

     // Method to check if the columns exists for the table
   public boolean columnExists(List<String> columns) {

   // return true if column does not exists
    if(columns.size() == 0)
       return true;       

     List<String> lColumns =new ArrayList<>(columns);

      for (ColumnInformation column_name_attr : colNameAttrs) {
         if (lColumns.contains(column_name_attr.colName))
            lColumns.remove(column_name_attr.colName);
      }

      return lColumns.isEmpty();
   }    

// Update table data
 public void updateMetaData()
 {

   //update root page in the tables catalog
   try{
         RandomAccessFile tableFile = new RandomAccessFile(TableUtils.getTablePath(tabName), "r");
   
         Integer rootPageNo = DavisBaseBinaryFile.getRootPage(tableFile);
         tableFile.close();
         // initialise davisbase catelog                   
         RandomAccessFile dbtabCatalog = new RandomAccessFile(TableUtils.getTablePath(DavisBaseBinaryFile.systemTablesFile), "rw");       
         DavisBaseBinaryFile tablesBinaryFile = new DavisBaseBinaryFile(dbtabCatalog);
         MetaData tablesMetaData = new MetaData(DavisBaseBinaryFile.systemTablesFile);         
         SpecialCondition cdtn = new SpecialCondition(DataTypes.TEXT);
         cdtn.setColumName("table_name");
         cdtn.columnOrdinal = 0;
         cdtn.setConditionValue(tabName);
         cdtn.setOp("=");

         List<String> columns = Arrays.asList("record_count","root_page");
         List<String> newValues = new ArrayList<>();

         newValues.add(Integer.valueOf(recCount).toString());
         newValues.add(Integer.valueOf(rootPgNo).toString());

         tablesBinaryFile.updateRecords(tablesMetaData,cdtn,columns,newValues);                                              
         dbtabCatalog.close();
   }
   catch(IOException e){
      System.out.println("! Error updating meta data for " + tabName);
   }   
 }

// validate column before adding whether that column exists or not
 public boolean validateInsert(List<TableAttribute> row) throws IOException
 {
   RandomAccessFile tableFile = new RandomAccessFile(TableUtils.getTablePath(tabName), "r");
   DavisBaseBinaryFile file = new DavisBaseBinaryFile(tableFile);                  
      for(int i=0;i<colNameAttrs.size();i++)
      {      
         SpecialCondition cdtn = new SpecialCondition(colNameAttrs.get(i).dType);
         cdtn.columnName = colNameAttrs.get(i).colName;
         cdtn.columnOrdinal = i;
         cdtn.setOp("=");

         if(colNameAttrs.get(i).unique)
         {
               cdtn.setConditionValue(row.get(i).fldVal);
               
               if(file.recordExists(this, Arrays.asList(colNameAttrs.get(i).colName), cdtn)){
                  // trying to add column name that already exists
                  System.out.println("! Insert failed: Column "+ colNameAttrs.get(i).colName + " should be unique." );
                  tableFile.close();
                  return false;
               }      
            }
         }
         tableFile.close();
         return true;
      }
   }
