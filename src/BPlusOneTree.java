import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.RandomAccessFile;
import java.io.File;
import java.io.IOException;

//B + 1 tree implementation for traversing table files
public class BPlusOneTree {

    RandomAccessFile binaryFile;
    int rootPageNum;
    String tableName;

    public BPlusOneTree(RandomAccessFile file, int rootPageNum, String tableName) {
        this.binaryFile = file;
        this.rootPageNum = rootPageNum;
        this.tableName = tableName;
    }

    // This method does a traversal on the B+1 tree and returns the leaf pages in seqquential order
    public List<Integer> getAllLeaves() throws IOException {

        List<Integer> leafPages = new ArrayList<>();
        binaryFile.seek(rootPageNum * DavisBaseBinaryFile.pageSize);
        // if root is a leaf page, read directly and return. No traversal is required
        PageType rootPgType = PageType.get(binaryFile.readByte());
        if (rootPgType == PageType.LEAF) {
            if (!leafPages.contains(rootPageNum))
                leafPages.add(rootPageNum);
        } else {
            addLeaves(rootPageNum, leafPages);
        }

        return leafPages;

    }

    // recursively adds leaves
    private void addLeaves(int intPageNo, List<Integer> leafPage) throws IOException {
        Page intPage = new Page(binaryFile, intPageNo);
        for (InteriorRecord leftPage : intPage.leftChildren) {
            if (Page.getPageType(binaryFile, leftPage.leftChildPgNo) == PageType.LEAF) {
                if (!leafPage.contains(leftPage.leftChildPgNo))
                leafPage.add(leftPage.leftChildPgNo);
            } else {
                addLeaves(leftPage.leftChildPgNo, leafPage);
            }
        }

        if (Page.getPageType(binaryFile, intPage.rightPage) == PageType.LEAF) {
            if (!leafPage.contains(intPage.rightPage))
            leafPage.add(intPage.rightPage);
        } else {
            addLeaves(intPage.rightPage, leafPage);
        }

    }

    public List<Integer> getAllLeaves(SpecialCondition condition) throws IOException {

        if (condition == null || condition.getOperation() == SpecialCondition.OperatorType.NOTEQUAL
                || !(new File(TableUtils.getIndexFilePath(tableName, condition.columnName)).exists())) {
            // Since there is no index, use brute force algorithm to trverse through all leaves
            return getAllLeaves();
        } else {

            RandomAccessFile indexFile = new RandomAccessFile(
                    TableUtils.getIndexFilePath(tableName, condition.columnName), "r");
            BTree bTree = new BTree(indexFile);

            // Binary search on the btree
            List<Integer> rowIds = bTree.getRowIds(condition);
            Set<Integer> hash_Set = new HashSet<>();
           
            for (int rowId : rowIds) {
                hash_Set.add(gtPgNo(rowId, new Page(binaryFile, rootPageNum)));
            }

            
            System.out.print(" count : " + rowIds.size() + " ---> ");
            for (int rowId : rowIds) {
                System.out.print(" " + rowId + " ");
            }

            System.out.println();
            System.out.println(" leaves: " + hash_Set);
            System.out.println();

            indexFile.close();

            return Arrays.asList(hash_Set.toArray(new Integer[hash_Set.size()]));
        }

    }

    // Returns the page(right most) for inserting new records
    public static int getPgNoForInsert(RandomAccessFile file, int rootPageNum) {
        Page rootPage = new Page(file, rootPageNum);
        if (rootPage.pageType != PageType.LEAF && rootPage.pageType != PageType.LEAFINDEX)
            return getPgNoForInsert(file, rootPage.rightPage);
        else
            return rootPageNum;

    }

    // perform binary search on Bplus one tree and find the rowids
    public int gtPgNo(int rowId, Page pg) {
        if (pg.pageType == PageType.LEAF)
            return pg.pageNo;

        int index = binarySearch(pg.leftChildren, rowId, 0, pg.noOfCells - 1);

        if (rowId < pg.leftChildren.get(index).rId) {   //Recursion
            return gtPgNo(rowId, new Page(binaryFile, pg.leftChildren.get(index).leftChildPgNo));
        } else {
        if( index+1 < pg.leftChildren.size())
            return gtPgNo(rowId, new Page(binaryFile, pg.leftChildren.get(index+1).leftChildPgNo));
        else
           return gtPgNo(rowId, new Page(binaryFile, pg.rightPage));


        }
    }

    private int binarySearch(List<InteriorRecord> vals, int searchValue, int start, int end) {   //Binary search algo

        if(end - start <= 2)
        {
            int i =start;
            for(i=start;i <end;i++){
                if(vals.get(i).rId < searchValue)
                    continue;
                else
                    break;
            }
            return i;
        }
        else{
            
                int mid = (end - start) / 2 + start;
                if (vals.get(mid).rId == searchValue)
                    return mid;

                if (vals.get(mid).rId < searchValue)
                    return binarySearch(vals, searchValue, mid + 1, end);
                else
                    return binarySearch(vals, searchValue, start, mid - 1);
            
        }

    }

}
