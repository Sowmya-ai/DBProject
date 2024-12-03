import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class Page {

  public PageType pageType;
  short noOfCells = 0;
  public int pageNo;
  short startOffset;
  public int rightPage;
  public int parentPageNo;
  private List<TableRecord> records;
  boolean refreshTableRecords = false;
  long pageStart;
  int lastRowId;
  int availableSpace;
  RandomAccessFile binaryFile;
  List<InteriorRecord> leftChildren;

  public DataTypes indexValueDataType;
  public TreeSet<Long> lIndexValues;
  public TreeSet<String> sIndexValues;
  public HashMap<String,IndexRecord> indexValuePointer;
  private Map<Integer,TableRecord> recordsMap;

    /**
     * Load a page from a file
     * Reads the page header from the page and fills the attributes
     * @param file
     * @param pageNo
     */
  public Page(RandomAccessFile file, int pageNo) {
    try 
    {
      this.pageNo = pageNo;
      indexValueDataType = null;
      lIndexValues = new TreeSet<>();
      sIndexValues = new TreeSet<>();
      indexValuePointer = new HashMap<String,IndexRecord>();
      recordsMap = new HashMap<>();

      this.binaryFile = file;
      lastRowId = 0;
      pageStart = DavisBaseBinaryFile.pageSize * pageNo;
      binaryFile.seek(pageStart);
      pageType = PageType.get(binaryFile.readByte()); // pagetype
      binaryFile.readByte(); // unused
      noOfCells = binaryFile.readShort();
      startOffset = binaryFile.readShort();
      availableSpace = startOffset - 0x10 - (noOfCells *2);

      rightPage = binaryFile.readInt();
      parentPageNo = binaryFile.readInt();
      binaryFile.readShort();// 2 unused bytes

      //Load the table records
      if (pageType == PageType.LEAF)
        fillTableRecords();
      if(pageType == PageType.INTERIOR)
          fillLeftChild();
      if(pageType == PageType.INTERIORINDEX || pageType == PageType.LEAFINDEX)
          fillIdxRecord();
    
    } catch (IOException ex) {
      System.out.println("! Error while reading the page " + ex.getMessage());
    }
  }

    /**
     * This method is used to get the index values
     * @return list of string indices
     */
  public List<String> getIdxVals()
  {
      List<String> strIndexValues = new ArrayList<>();

      if(sIndexValues.size() > 0)
        strIndexValues.addAll(Arrays.asList(sIndexValues.toArray(new String[sIndexValues.size()])));
       if(lIndexValues.size() > 0)
      {
        Long[] lArray = lIndexValues.toArray(new Long[lIndexValues.size()]);
                for(int i=0;i<lArray.length;i++)
        {
          strIndexValues.add(lArray[i].toString());
        }
            }
              return strIndexValues;
  }

    /**
     * This method is used to get the Page Type as interior or leaf by searching in the file using pageNo
     * @param file
     * @param pageNo
     * @return page type
     * @throws IOException
     */
  public static PageType getPageType(RandomAccessFile file,int pageNo) throws IOException
  {
    try 
    {
      int pageStart = DavisBaseBinaryFile.pageSize * pageNo;
      file.seek(pageStart);
      return  PageType.get(file.readByte()); 
    } catch (IOException ex) {
      System.out.println("! Error while getting the page type " + ex.getMessage());
      throw ex;
    }
  }

    /**
     * This method is used to update records with new values Byte array for a given ordinal position
     * @param rec
     * @param ordPos
     * @param newVal
     * @throws IOException
     */
  public void updateRecords(TableRecord rec,int ordPos,Byte[] newVal) throws IOException
  {
    binaryFile.seek(pageStart + rec.recOffst + 7);
    int valueOffset = 0;
    for(int i=0;i<ordPos;i++)
    {
      valueOffset+= DataTypes.getLength((byte)binaryFile.readByte());
    }
    binaryFile.seek(pageStart + rec.recOffst + 7 + rec.colDt.length + valueOffset);
    binaryFile.write(ByteConvertor.Bytestobytes(newVal));
      
  }

    /**
     * This method is used to add new columns
     * @param colInfo
     * @throws IOException
     */
  public void addNewCols(ColumnInformation colInfo) throws IOException
  {
    try {
      addTbRows(DavisBaseBinaryFile.systemColumnsFile, Arrays.asList(new TableAttribute[] {
        new TableAttribute(DataTypes.TEXT, colInfo.tblName),
        new TableAttribute(DataTypes.TEXT, colInfo.colName),
        new TableAttribute(DataTypes.TEXT, colInfo.dType.toString()),
        new TableAttribute(DataTypes.SMALLINT, colInfo.ordPos.toString()),
        new TableAttribute(DataTypes.TEXT, colInfo.isNullable ? "YES":"NO"),
        colInfo .isPrimKey ?
        new TableAttribute(DataTypes.TEXT, "PRI") : new TableAttribute(DataTypes.NULL, "NULL") ,
        new TableAttribute(DataTypes.TEXT, colInfo.unique ? "YES": "NO")
       })); 
    } catch (Exception e) {
      System.out.println("! Could not add column");
    }
  }

    /**
     * adds a table row - this method converts the attributes into byte array and calls addNewPageRecord
     * @param tbName
     * @param attr
     * @return last row id
     * @throws IOException
     */
public int addTbRows(String tbName,List<TableAttribute> attr) throws IOException
  {
      List<Byte> colDataTypes = new ArrayList<Byte>();
      List<Byte> recordBody = new ArrayList<Byte>();

      MetaData metaData  = null;
      if(DavisBaseBinaryFile.isSystemInitialized)
      {
        metaData = new MetaData(tbName);
        if(!metaData.validateInsert(attr))
            return -1;
      }

      for(TableAttribute attribute : attr)
      {
        //add value for the record body
        recordBody.addAll(Arrays.asList(attribute.fldValByte));
       
        //Fill column Datatype for every attribute in the row
        if(attribute.dt == DataTypes.TEXT)
          {
             colDataTypes.add(Integer.valueOf(DataTypes.TEXT.getVal() + (new String(attribute.fldVal).length())).byteValue());
          }
        else
          {
              colDataTypes.add(attribute.dt.getVal());
          }
        }

        lastRowId++;

        //calculate pay load size
        short payLoadSize = Integer.valueOf(recordBody.size() + 
                                  colDataTypes.size() + 1).shortValue();
       
        //create record header
        List<Byte> recordHeader = new ArrayList<>();

        recordHeader.addAll(Arrays.asList(ByteConvertor.shortToBytes(payLoadSize)));  //payloadSize
        recordHeader.addAll(Arrays.asList(ByteConvertor.intToBytes(lastRowId))); //rowid
        recordHeader.add(Integer.valueOf(colDataTypes.size()).byteValue()); //number of columns
        recordHeader.addAll(colDataTypes); //column data types

         addNewPageRecord(recordHeader.toArray(new Byte[recordHeader.size()]), 
                               recordBody.toArray(new Byte[recordBody.size()])
                                );

         refreshTableRecords = true;
         if(DavisBaseBinaryFile.isSystemInitialized)
         {
            metaData.recCount++;
            metaData.updateMetaData();
         }
           return lastRowId;
  }

    /**
     * This method is used to get Page records from the table
     * @return records
     */
    public List<TableRecord> getPageRecords(){
        if(refreshTableRecords)
           fillTableRecords();

           refreshTableRecords = false;

           return records;
    }

    public static int addNewPage(RandomAccessFile file,PageType pageType, int rightPage, int parentPageNo)
    {
        try
        {
            int pageNo = Long.valueOf((file.length()/DavisBaseBinaryFile.pageSize)).intValue();
            file.setLength(file.length() + DavisBaseBinaryFile.pageSize);
            file.seek(DavisBaseBinaryFile.pageSize * pageNo);
            file.write(pageType.getVal());
            file.write(0x00); //unused
            file.writeShort(0); // no of cells
            file.writeShort((short)(DavisBaseBinaryFile.pageSize)); // cell start offset

            file.writeInt(rightPage);

            file.writeInt(parentPageNo);

            return pageNo;
        }
        catch (IOException ex)
        {
            System.out.println("! Error while adding new page" + ex.getMessage());
            return -1;
        }
    }

    /**
     * This method is used to delete a page record of given record index
      * @param recIdx
     */
  private void deletePageRecord(short recIdx)
    {
        try{
            for (int i = recIdx + 1; i < noOfCells; i++) {
                binaryFile.seek(pageStart + 0x10 + (i *2) );
                short cellStart = binaryFile.readShort();

                if(cellStart == 0)
                    continue;

                binaryFile.seek(pageStart + 0x10 + ((i-1) *2));
                binaryFile.writeShort(cellStart);
            }
            noOfCells--;
            binaryFile.seek(pageStart + 2);
            binaryFile.writeShort(noOfCells);
        }
        catch(IOException e){
            System.out.println("! Error while deleting record at "+ recIdx + "in page " + pageNo);
        }
    }

  public void deleteTableRecord(String tbName, short recIdx)
  {
    deletePageRecord(recIdx);
    MetaData metaData = new MetaData(tbName);
    metaData.recCount --;
    metaData.updateMetaData();
    refreshTableRecords = true;

  }

    /**
     * adds a new record and updates the page header accordingly
     * @param recordHeader
     * @param recordBody
     * @throws IOException
     */
  private void addNewPageRecord(Byte[] recordHeader, Byte[] recordBody) throws IOException
  {
        //if there is no space in the current page
      if(recordHeader.length + recordBody.length + 4 > availableSpace)
      {
        try{
          if(pageType == PageType.LEAF || pageType == PageType.INTERIOR)
           {
              handleTableOverFlow();
           }
          else
          {  
            handleIdxOverflow();
            return;
          }
        }
        catch(IOException e){
          System.out.println("! Error while handleTableOverFlow");
        }
      }
    
    short cellStart =  startOffset;

    short newCellStart  = Integer.valueOf((cellStart - recordBody.length  - recordHeader.length - 2)).shortValue();
    binaryFile.seek(pageNo * DavisBaseBinaryFile.pageSize + newCellStart);
  
    //record head
    binaryFile.write(ByteConvertor.Bytestobytes(recordHeader)); // datatypes

    //record body
    binaryFile.write(ByteConvertor.Bytestobytes(recordBody));

    binaryFile.seek(pageStart + 0x10 + (noOfCells * 2));
    binaryFile.writeShort(newCellStart);
    
    startOffset = newCellStart;
    
    binaryFile.seek(pageStart + 4); binaryFile.writeShort(startOffset);

    noOfCells++;
    binaryFile.seek(pageStart + 2); binaryFile.writeShort(noOfCells);
    
    availableSpace = startOffset - 0x10 - (noOfCells *2);
    }
    
    private boolean idxPageCleaned;

    private void handleIdxOverflow() throws IOException
    {
     if(pageType == PageType.LEAFINDEX)
     {
       //if currrent page is root
       if(parentPageNo == -1)
       {
        //create a new interior Parent root Page
        parentPageNo = addNewPage(binaryFile, PageType.INTERIORINDEX, pageNo , -1);
       }
       //create a new left Page 
       int newLeftLeafPageNo = addNewPage(binaryFile, PageType.LEAFINDEX,pageNo, parentPageNo);

       //set the new parentPage as parent for the current page 
       setParent(parentPageNo);
      
       IndexNode incomingInsertTemp = this.incomingInsert;
       //Split the index records
     
       // Insert half the items into leftchild page
       Page leftLeafPage = new Page(binaryFile, newLeftLeafPageNo);
       //call the split method
       IndexNode toInsertParentIndexNode = splitIdxRecordsBtwPages(leftLeafPage);
      
       //Insert Middle record to the parent page with left page No

       Page parentPage = new Page(binaryFile,parentPageNo);
     
       //shift page based on the incoming index value
       int comparisonResult= SpecialCondition.compare(incomingInsertTemp.indValue.fldVal,toInsertParentIndexNode.indValue.fldVal,incomingInsert.indValue.dt);
       
       if(comparisonResult == 0)
       {
          toInsertParentIndexNode.rowId.addAll(incomingInsertTemp.rowId);
          parentPage.addIdx(toInsertParentIndexNode,newLeftLeafPageNo);
          movePage(parentPage);
          return;
       }
      else if(comparisonResult < 0)
      {
          leftLeafPage.addIdx(incomingInsertTemp);
          movePage(leftLeafPage);
       }
       else
       {
           addIdx(incomingInsertTemp);
       }

       parentPage.addIdx(toInsertParentIndexNode,newLeftLeafPageNo);

      }
     
     else{
      //multilevel split - split on interior page
       //create a new interior Parent root Page
       
       if(noOfCells < 3 && !idxPageCleaned)
       {
          idxPageCleaned = true;
          String[] indexValuesTemp = getIdxVals().toArray(new String[getIdxVals().size()]);
          HashMap<String, IndexRecord> indexValuePointerTemp = (HashMap<String, IndexRecord>) indexValuePointer.clone();
          IndexNode incomingInsertTemp = this.incomingInsert;
           clearPage();
          for (int i = 0; i < indexValuesTemp.length; i++) {

              addIdx(indexValuePointerTemp.get(indexValuesTemp[i]).getIndxNd(),indexValuePointerTemp.get(indexValuesTemp[i]).leftPgNo);
          }

           addIdx(incomingInsertTemp);
          return;
       }

       if(idxPageCleaned)
       {
         System.out.println("! Page overflow, increase the page size. Reached Max number of rows for an Index value");
         return;
       }

      
       
       if(parentPageNo == -1)
       {
        parentPageNo = addNewPage(binaryFile, PageType.INTERIORINDEX, pageNo , -1);
       }
       //create a new Interior Page 
       int newLeftInteriorPageNo = addNewPage(binaryFile, PageType.INTERIORINDEX, pageNo, parentPageNo );

       
       //set the new parentPage as parent for the current page 
       setParent(parentPageNo);

       IndexNode incomingInsertTemp = this.incomingInsert;
       //Split the index records
     
        //Insert half the items into leftchild page
        Page leftInteriorPage = new Page(binaryFile, newLeftInteriorPageNo);
        
        IndexNode toInsertParentIndexNode = splitIdxRecordsBtwPages(leftInteriorPage);

        Page parentPage = new Page(binaryFile,parentPageNo);
       //shift page based on the incoming index value
       int comparisonResult= SpecialCondition.compare(incomingInsertTemp.indValue.fldVal,toInsertParentIndexNode.indValue.fldVal,incomingInsert.indValue.dt);
       

       //add the middle Orphan to the left page
       Page middleOrphan = new Page(binaryFile,toInsertParentIndexNode.leftPgNo);
       middleOrphan.setParent(parentPageNo);
       leftInteriorPage.setRightPageNo(middleOrphan.pageNo);
   
       if(comparisonResult == 0)
       {
          toInsertParentIndexNode.rowId.addAll(incomingInsertTemp.rowId);
          parentPage.addIdx(toInsertParentIndexNode,newLeftInteriorPageNo);
          movePage(parentPage);
          return;
       }
       else if(comparisonResult < 0)
       {
        leftInteriorPage.addIdx(incomingInsertTemp);
        movePage(leftInteriorPage);
       }
       else
       {
           addIdx(incomingInsertTemp);
       }     
  
       parentPage.addIdx(toInsertParentIndexNode,newLeftInteriorPageNo);

     }
    }

    /**
     * This method is used to clear the current(rightleaf) page by resetting the page offsets and no of records
     * @throws IOException
     */
    private void clearPage() throws IOException {
      noOfCells = 0;
      startOffset = Long.valueOf(DavisBaseBinaryFile.pageSize).shortValue();
      availableSpace = startOffset - 0x10 - (noOfCells * 2); // this page will now be treated as a new page
      byte[] emptybytes = new byte[512-16];
      Arrays.fill(emptybytes, (byte) 0 );
      binaryFile.seek(pageStart + 16);
      binaryFile.write(emptybytes);
      binaryFile.seek(pageStart + 2);
      binaryFile.writeShort(noOfCells);
      binaryFile.seek(pageStart + 4);
      binaryFile.writeShort(startOffset);
      lIndexValues = new TreeSet<>();
      sIndexValues = new TreeSet<>();
      indexValuePointer = new HashMap<>();

    }

    /**
     * This method copies half to left page and rewrite the current right page with remaining half records
     * @param newleftPg
     * @return middle index Node which should be added to the parent
     * @throws IOException
     */
  private IndexNode splitIdxRecordsBtwPages(Page newleftPg) throws IOException {

    try{
    int mid = getIdxVals().size() / 2;
    String[] indexValuesTemp = getIdxVals().toArray(new String[getIdxVals().size()]);

    IndexNode toInsertParentIndexNode = indexValuePointer.get(indexValuesTemp[mid]).getIndxNd();
    toInsertParentIndexNode.leftPgNo = indexValuePointer.get(indexValuesTemp[mid]).leftPgNo;
  
    HashMap<String, IndexRecord> indexValuePointerTemp = (HashMap<String, IndexRecord>) indexValuePointer.clone();

    for (int i = 0; i < mid; i++) {

      newleftPg.addIdx(indexValuePointerTemp.get(indexValuesTemp[i]).getIndxNd(),indexValuePointerTemp.get(indexValuesTemp[i]).leftPgNo);
    }

    clearPage();
    sIndexValues = new TreeSet<>();
    lIndexValues = new TreeSet<>();
    indexValuePointer = new HashMap<String, IndexRecord>();

    //Insert the other half into right child page
    for(int i=mid+1;i<indexValuesTemp.length;i++)

    {
        addIdx(indexValuePointerTemp.get(indexValuesTemp[i]).getIndxNd(),indexValuePointerTemp.get(indexValuesTemp[i]).leftPgNo);
    }
  
    return toInsertParentIndexNode;
  }
  catch(IOException e)
  {
    System.out.println("! Insert into Index File failed. Error while splitting index pages");
    throw e;
  }

  }

    /**
     * This method is used to handle table overflow
     * @throws IOException
     */
  private void handleTableOverFlow() throws IOException
  {
    if(pageType == PageType.LEAF)
      {
         //create a new leaf page
        int newRightLeafPageNo = addNewPage(binaryFile,pageType,-1,-1);

        //if the current leaf page is root
        if(parentPageNo == -1){
        
          //create new parent page
           
           int newParentPageNo = addNewPage(binaryFile, PageType.INTERIOR,
            newRightLeafPageNo, -1);

          //set the new leaf page as right sibling to the current page
          setRightPageNo(newRightLeafPageNo);
          //set the newly created parent page as parent to the current page
          setParent(newParentPageNo);

          //Add the current page as left child for the parent
          Page newParentPage = new Page(binaryFile,newParentPageNo);
          newParentPageNo = newParentPage.addLeftTableChild(pageNo,lastRowId);
          //add the newly created leaf page as rightmost child of the parent
          newParentPage.setRightPageNo(newRightLeafPageNo);


          //add the newly created parent page as parent to newly created right page
          Page newLeafPage = new Page(binaryFile,newRightLeafPageNo);
          newLeafPage.setParent(newParentPageNo);

          //make the current page as newly created page for further operations
          movePage(newLeafPage);
        }
        else
        {
          //Add the current page as left child for the parent
          Page parentPage = new Page(binaryFile,parentPageNo);
          parentPageNo = parentPage.addLeftTableChild(pageNo,lastRowId);

          //add the newly created leaf page as rightmost child of the parent
          parentPage.setRightPageNo(newRightLeafPageNo);

          //set the new leaf page as right sibling to the current page
          setRightPageNo(newRightLeafPageNo);

          //add the parent page as parent to newly created right page
          Page newLeafPage = new Page(binaryFile,newRightLeafPageNo);
          newLeafPage.setParent(parentPageNo);

          //make the current page as newly created page for further operations
          movePage(newLeafPage);
        }
      }
      else {
        //create a new leaf page
        int newRightLeafPageNo = addNewPage(binaryFile,pageType,-1,-1);

        //create new parent page
           
           int newParentPageNo = addNewPage(binaryFile, PageType.INTERIOR,
            newRightLeafPageNo, -1);
            
             //set the new leaf page as right sibling to the current page
          setRightPageNo(newRightLeafPageNo);
          
           //set the newly created parent page as parent to the current page
          setParent(newParentPageNo);
          
          //Add the current page as left child for the parent
          Page newParentPage = new Page(binaryFile,newParentPageNo);
          newParentPageNo = newParentPage.addLeftTableChild(pageNo,lastRowId);
          //add the newly created leaf page as rightmost child of the parent
          newParentPage.setRightPageNo(newRightLeafPageNo);


          //add the newly created parent page as parent to newly created right page
          Page newLeafPage = new Page(binaryFile,newRightLeafPageNo);
          newLeafPage.setParent(newParentPageNo);

          //make the current page as newly created page for further operations
          movePage(newLeafPage);
      }
  }


    /**
     * This methods is used to add left child for the current page
      * @param leftChildPageNo
     * @param rowId
     * @return
     * @throws IOException
     */
  private int addLeftTableChild(int leftChildPageNo,int rowId) throws IOException
  {
    for( InteriorRecord intRecord: leftChildren)
    {
      if(intRecord.rId == rowId)
        return pageNo;
    }
    if(pageType == PageType.INTERIOR)
    {
      List<Byte> recordHeader= new ArrayList<>();
      List<Byte> recordBody= new ArrayList<>();

      recordHeader.addAll(Arrays.asList(ByteConvertor.intToBytes(leftChildPageNo)));
      recordBody.addAll(Arrays.asList(ByteConvertor.intToBytes(rowId)));

      addNewPageRecord(recordHeader.toArray(new Byte[recordHeader.size()]),
                                        recordBody.toArray(new Byte[recordBody.size()]));
    }
   return pageNo;

  }


    /**
     * This method is used to copy all the members from the new page to the current page
     * @param newPage
     */
  private void movePage(Page newPage)
  {
    pageType = newPage.pageType;
    noOfCells = newPage.noOfCells;
    pageNo = newPage.pageNo;
    startOffset = newPage.startOffset;
    rightPage = newPage.rightPage;
    parentPageNo = newPage.parentPageNo;
    leftChildren = newPage.leftChildren;
    sIndexValues = newPage.sIndexValues;
    lIndexValues = newPage.lIndexValues;
    indexValuePointer = newPage.indexValuePointer;
    records = newPage.records;
    pageStart = newPage.pageStart;
    availableSpace = newPage.availableSpace;
  }


    /**
     * This method sets the parentPageNo as parent for the current page
     * @param parentPageNo
     * @throws IOException
     */
 public void setParent(int parentPageNo) throws IOException
 {
    binaryFile.seek(DavisBaseBinaryFile.pageSize * pageNo + 0x0A);
    binaryFile.writeInt(parentPageNo);
    this.parentPageNo = parentPageNo;
 }

    /**
     * This method sets the rightPageNo as rightPageNo (right sibling or right most child) for the current page
     * @param rightPageNo
     * @throws IOException
     */
 public void setRightPageNo(int rightPageNo) throws IOException
 {
    binaryFile.seek(DavisBaseBinaryFile.pageSize * pageNo + 0x06);
    binaryFile.writeInt(rightPageNo);
    this.rightPage = rightPageNo;
 }

    /**
     * This method is used to delete the index node
     * @param node
     * @throws IOException
     */
 public void deleteIdx(IndexNode node) throws IOException
 {
    deletePageRecord(indexValuePointer.get(node.indValue.fldVal).pgHeaderIndex);
     fillIdxRecord();
    refreshHeaderOffset();
 }

    /**
     * This method is used to add index node
     * @param node
     * @throws IOException
     */
 public void addIdx(IndexNode node) throws IOException
 {
     addIdx(node,-1);
 }

 private IndexNode incomingInsert;
 public void addIdx(IndexNode node,int leftPageNo) throws IOException
 {
  incomingInsert = node;
  incomingInsert.leftPgNo = leftPageNo;
  List<Integer> rowIds = new ArrayList<>();
  
  //If index already exists, delete the old one, add the new rowid to the array and insert
  List<String> ixValues = getIdxVals();
  if(getIdxVals().contains(node.indValue.fldVal))
  {
      leftPageNo = indexValuePointer.get(node.indValue.fldVal).leftPgNo;
      incomingInsert.leftPgNo = leftPageNo;
      rowIds = indexValuePointer.get(node.indValue.fldVal).rowId;
      rowIds.addAll(incomingInsert.rowId);
      incomingInsert.rowId = rowIds;
      deletePageRecord(indexValuePointer.get(node.indValue.fldVal).pgHeaderIndex);
      if(indexValueDataType == DataTypes.TEXT || indexValueDataType == null)
        sIndexValues.remove(node.indValue.fldVal);
      else
        lIndexValues.remove(Long.parseLong(node.indValue.fldVal));
  }

     rowIds.addAll(node.rowId);

     rowIds = new ArrayList<>(new HashSet<>(rowIds));

    List<Byte> recordHead = new ArrayList<>(); 
    List<Byte> recordBody = new ArrayList<>();

    //no of row ids
    recordBody.addAll(Arrays.asList(Integer.valueOf(rowIds.size()).byteValue()));

    //index data type

    if(node.indValue.dt == DataTypes.TEXT)
      recordBody.add(Integer.valueOf(node.indValue.dt.getVal() 
                                + node.indValue.fldVal.length()).byteValue());
    else
      recordBody.add(node.indValue.dt.getVal());
 
    //index value
    recordBody.addAll(Arrays.asList(node.indValue.fldValByte));


    //list of rowids
    for(int i=0;i<rowIds.size();i++)
    {
      recordBody.addAll(Arrays.asList(ByteConvertor.intToBytes(rowIds.get(i))));
    } 

    short payload = Integer.valueOf(recordBody.size()).shortValue();
    if(pageType == PageType.INTERIORINDEX)
        recordHead.addAll(Arrays.asList(ByteConvertor.intToBytes(leftPageNo)));

    recordHead.addAll(Arrays.asList(ByteConvertor.shortToBytes(payload)));

    addNewPageRecord(recordHead.toArray(new Byte[recordHead.size()]), 
                                                  recordBody.toArray(new Byte[recordBody.size()])
     );

     fillIdxRecord();
    refreshHeaderOffset();
    
 }

    /**
     * This method is used to fill the index records
     */
    private void fillIdxRecord(){
        try {
            lIndexValues = new TreeSet<>();
            sIndexValues = new TreeSet<>();
            indexValuePointer = new HashMap<>();

            int leftPageNo = -1;
            byte noOfRowIds = 0;
            byte dataType = 0;
            for (short i = 0; i < noOfCells; i++) {
                binaryFile.seek(pageStart + 0x10 + (i *2) );
                short cellStart = binaryFile.readShort();
                if(cellStart == 0)//ignore deleted cells
                    continue;
                binaryFile.seek(pageStart + cellStart);

                if(pageType == PageType.INTERIORINDEX)
                    leftPageNo = binaryFile.readInt();

                short payload = binaryFile.readShort(); // payload

                noOfRowIds = binaryFile.readByte();
                dataType = binaryFile.readByte();

                if(indexValueDataType == null && DataTypes.get(dataType) != DataTypes.NULL)
                    indexValueDataType = DataTypes.get(dataType);

                byte[] indexValue = new byte[DataTypes.getLength(dataType)];
                binaryFile.read(indexValue);

                List<Integer> lstRowIds = new ArrayList<>();
                for(int j=0;j<noOfRowIds;j++)
                {
                    lstRowIds.add(binaryFile.readInt());
                }

                IndexRecord record = new IndexRecord(i, DataTypes.get(dataType),noOfRowIds, indexValue
                        , lstRowIds,leftPageNo,rightPage,pageNo,cellStart);

                if(indexValueDataType == DataTypes.TEXT || indexValueDataType == null)
                    sIndexValues.add(record.getIndxNd().indValue.fldVal);
                else
                    lIndexValues.add(Long.parseLong(record.getIndxNd().indValue.fldVal));

                indexValuePointer.put(record.getIndxNd().indValue.fldVal, record);

            }
        } catch (IOException ex) {
            System.out.println("! Error while filling records from the page " + ex.getMessage());
        }
    }

    private void refreshHeaderOffset()
{
  try {
  binaryFile.seek(pageStart + 0x10);
  for(String indexVal : getIdxVals())
  {
    binaryFile.writeShort(indexValuePointer.get(indexVal).pgOffset);
  }

} catch (IOException ex) {
  System.out.println("! Error while refrshing header offset " + ex.getMessage());
}
}


    /**
     * This method fills the list of rows in the page into a list object
     */
  private void fillTableRecords() {
    short payLoadSize = 0;
    byte noOfcolumns = 0;
    records = new ArrayList<TableRecord>();
    recordsMap =  new HashMap<>();
    try {
      for (short i = 0; i < noOfCells; i++) {
        binaryFile.seek(pageStart + 0x10 + (i *2) );
        short cellStart = binaryFile.readShort();
        if(cellStart == 0)
          continue;
        binaryFile.seek(pageStart + cellStart);

        payLoadSize = binaryFile.readShort();
        int rowId = binaryFile.readInt();
        noOfcolumns = binaryFile.readByte();
        
        if(lastRowId < rowId) lastRowId = rowId;
        
        byte[] colDatatypes = new byte[noOfcolumns];
        byte[] recordBody = new byte[payLoadSize - noOfcolumns - 1];

        binaryFile.read(colDatatypes);
        binaryFile.read(recordBody);

        TableRecord record = new TableRecord(i, rowId, cellStart
                                              , colDatatypes, recordBody);
        records.add(record);
        recordsMap.put(rowId, record);
      }
    } catch (IOException ex) {
      System.out.println("! Error while filling records from the page " + ex.getMessage());
    }
  }

    /**
     * In case of Interior page fill the left children of the current page into a list of Integers
     */
    private void fillLeftChild(){
  try {
    leftChildren = new ArrayList<>();
  
    int leftChildPageNo = 0;
    int rowId =0;
    for (int i = 0; i < noOfCells; i++) {
      binaryFile.seek(pageStart + 0x10 + (i *2) );
      short cellStart = binaryFile.readShort();
      if(cellStart == 0)//ignore deleted cells
        continue;
      binaryFile.seek(pageStart + cellStart);

      leftChildPageNo = binaryFile.readInt();
      rowId = binaryFile.readInt();
      leftChildren.add(new InteriorRecord(rowId, leftChildPageNo));
    }
  } catch (IOException ex) {
    System.out.println("! Error while filling records from the page " + ex.getMessage());
  }

}

}
