import java.util.List;

public class IndexNode{
    public TableAttribute indValue; //index  value of row
    public List<Integer> rowId; // row IDS
    public boolean isInteriorNode; // is the node interior
    public int leftPgNo; // left page no of node

    // parameterized constructor of index node
    public IndexNode(TableAttribute indVal,List<Integer> rIds)
    {
        this.indValue = indVal;
        this.rowId = rIds;
    }

}
