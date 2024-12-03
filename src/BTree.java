import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BTree {
    Page rt;
    RandomAccessFile binFile;

    public BTree(RandomAccessFile file) {
        this.binFile = file;
        this.rt = new Page(binFile, DavisBaseBinaryFile.getRootPage(binFile));
    }

    /**
     * This method does binary search recursively using the given value and find the right pageNo to insert the index value
     * @param page This is the page for which a nearest page number is to be found
     * @param value The index value of the page page
     */
    private int getClosestPgNo(Page page, String value) {
        if (page.pageType == PageType.LEAFINDEX) {
            return page.pageNo;
        } else {
            if (SpecialCondition.compare(value , page.getIdxVals().get(0),page.indexValueDataType) < 0)
                return getClosestPgNo
                    (new Page(binFile,page.indexValuePointer.get(page.getIdxVals().get(0)).leftPgNo),
                        value);
            else if(SpecialCondition.compare(value,page.getIdxVals().get(page.getIdxVals().size()-1),page.indexValueDataType) > 0)
                return getClosestPgNo(
                    new Page(binFile,page.rightPage),
                        value);
            else{
                //perform binary search 
                String closestValue = binarySearch(page.getIdxVals().toArray(new String[page.getIdxVals().size()]),value,0,page.getIdxVals().size() -1,page.indexValueDataType);
                int i = page.getIdxVals().indexOf(closestValue);
                List<String> indexValues = page.getIdxVals();
                if(closestValue.compareTo(value) < 0 && i+1 < indexValues.size())
                {
                    return page.indexValuePointer.get(indexValues.get(i+1)).leftPgNo;
                }
                else if(closestValue.compareTo(value) > 0)
                {
                    return page.indexValuePointer.get(closestValue).leftPgNo;
                }
                else{
                    return page.pageNo;
                }
            }
        }
    }

    /**
     * This method is used to get the row Ids for the left of given node
     * @param pageNo
     * @param indexVal
     * @return rowId
     */
    private List<Integer> getLeftRowId(int pageNo, String indexVal)
    {
        List<Integer> rowId = new ArrayList<>();
        if(pageNo == -1)
            return rowId;
        Page page = new Page(this.binFile,pageNo);
        List<String> indexValues = Arrays.asList(page.getIdxVals().toArray(new String[page.getIdxVals().size()]));

        for(int i=0;i< indexValues.size() && SpecialCondition.compare(indexValues.get(i), indexVal, page.indexValueDataType) < 0 ;i++)
        {
            rowId.addAll(page.indexValuePointer.get(indexValues.get(i)).getIndxNd().rowId);
            addChildRowIds(page.indexValuePointer.get(indexValues.get(i)).leftPgNo, rowId);
        }

        if(page.indexValuePointer.get(indexVal)!= null)
            addChildRowIds(page.indexValuePointer.get(indexVal).leftPgNo, rowId);

        return rowId;
    }

    /**
     * This method is used to get the row Ids which are satisfying a given condition
     * @param condition
     * @return rowId
     */
    public List<Integer> getRowIds(SpecialCondition condition)
    {
        List<Integer> rowId = new ArrayList<>();

        //get to the closest page number satisfying the condition
        Page page = new Page(binFile,getClosestPgNo(rt, condition.comparisonValue));
    
        //get the index values for that page
        String[] indexValues= page.getIdxVals().toArray(new String[page.getIdxVals().size()]);
        
        SpecialCondition.OperatorType operationType = condition.getOperation();
        
        //store the rowids if the indexvalue is equal to the closest value
        for(int i=0;i < indexValues.length;i++)
        {
            if(condition.chkCondt(page.indexValuePointer.get(indexValues[i]).getIndxNd().indValue.fldVal))
                rowId.addAll(page.indexValuePointer.get(indexValues[i]).rowId);
        }    

        //to store all the rowids from the left side of the node recursivesly
        if(operationType == SpecialCondition.OperatorType.LESSTHAN || operationType == SpecialCondition.OperatorType.LESSTHANOREQUAL)
        {
           if(page.pageType == PageType.LEAFINDEX)
               rowId.addAll(getLeftRowId(page.parentPageNo,indexValues[0]));
           else 
                rowId.addAll(getLeftRowId(page.pageNo,condition.comparisonValue));
        }

         //to store all the rowids from the right side of the node recursively
        if(operationType == SpecialCondition.OperatorType.GREATERTHAN || operationType == SpecialCondition.OperatorType.GREATERTHANOREQUAL)
        {
         if(page.pageType == PageType.LEAFINDEX)
            rowId.addAll(getRightRowId(page.parentPageNo,indexValues[indexValues.length - 1]));
            else 
              rowId.addAll(getRightRowId(page.pageNo,condition.comparisonValue));
        }
        return rowId;
    }

    /**
     * This method is used to get the rowids that are right to given node
     * @param pgNo
     * @param indexVal
     * @return rowId
     */
    private List<Integer> getRightRowId(int pgNo, String indexVal)
    {
        List<Integer> rowId = new ArrayList<>();

        if(pgNo == -1)
            return rowId;
        Page page = new Page(this.binFile,pgNo);
        List<String> indexValues = Arrays.asList(page.getIdxVals().toArray(new String[page.getIdxVals().size()]));
        for(int i=indexValues.size() - 1; i >= 0 && SpecialCondition.compare(indexValues.get(i), indexVal, page.indexValueDataType) > 0; i--)
        {
               rowId.addAll(page.indexValuePointer.get(indexValues.get(i)).getIndxNd().rowId);
                addChildRowIds(page.rightPage, rowId);
         }

        if(page.indexValuePointer.get(indexVal)!= null)
           addChildRowIds(page.indexValuePointer.get(indexVal).rightPgNo, rowId);

        return rowId;
    }

    /**
     * This method is used to add childRows for a given pageNo
     * @param pageNo
     * @param rowId
     */
    private void addChildRowIds(int pageNo,List<Integer> rowId)
    {
        if(pageNo == -1)
            return;
        Page page = new Page(this.binFile, pageNo);
            for (IndexRecord record :page.indexValuePointer.values())
            {
                rowId.addAll(record.rowId);
                if(page.pageType == PageType.INTERIORINDEX)
                 {
                    addChildRowIds(record.leftPgNo, rowId);
                    addChildRowIds(record.rightPgNo, rowId);
                 }
            }  
    }

    /**
     * Inserts index value into the index page
     * @param attr
     * @param rowId
     */
    public void insertRow(TableAttribute attr,int rowId)
    {
        insertRow(attr,Arrays.asList(rowId));
    }

    /**
     * This method is used to insert rows for a given list of rowId and attribute into the index page
     * @param attr
     * @param rowId
     */
    public void insertRow(TableAttribute attr,List<Integer> rowId)
    {
        try{
            int pageNo = getClosestPgNo(rt, attr.fldVal) ;
            Page page = new Page(binFile, pageNo);
            page.addIdx(new IndexNode(attr,rowId));
            }
            catch(IOException e)
            {
                 System.out.println("! Error while insering " + attr.fldVal +" into index file");
            }
    }

    /**
     * This method is used to delete a row
     * @param attr
     * @param rowId
     */
    public void deleteRow(TableAttribute attr, int rowId)
    {
        try{
            int pageNo = getClosestPgNo(rt, attr.fldVal) ;
            Page page = new Page(binFile, pageNo);
            IndexNode tempNode = page.indexValuePointer.get(attr.fldVal).getIndxNd();
            //remove the rowid from the index value
            tempNode.rowId.remove(tempNode.rowId.indexOf(rowId));
            page.deleteIdx(tempNode);
            if(tempNode.rowId.size() !=0)
               page.addIdx(tempNode);

            }
            catch(IOException e)
            {
                 System.out.println("! Error while deleting " + attr.fldVal +" from index file");
            }
    }

    /**
     * This method is used to search for a value among the given string array using binary search
     * @param vals
     * @param searchVal
     * @param start
     * @param end
     * @param dType
     * @return
     */
    private String binarySearch(String[] vals,String searchVal,int start, int end , DataTypes dType)
    {
        if(end - start <= 3)
        {
            int i =start;
            for(i=start;i <end;i++){
                if(SpecialCondition.compare(vals[i], searchVal, dType) < 0)
                    continue;
                else
                    break;
            }
            return vals[i];
        }
        else{
            
                int mid = (end - start) / 2 + start;
                if(vals[mid].equals(searchVal))
                    return vals[mid];

                    if(SpecialCondition.compare(vals[mid], searchVal, dType) < 0)
                    return binarySearch(vals,searchVal,mid + 1,end,dType);
                else 
                    return binarySearch(vals,searchVal,start,mid - 1,dType);
            
        }
    }
}
