
/* SpecialCondition class 

This class handles logic for WHERE clause and checks the conditions */
import java.util.Objects;

public class SpecialCondition {

    // Enum for logical operators
    public enum LogicalOperator { AND, OR, NONE }

    // Enum for operator types
    public enum OperatorType {
        GREATERTHAN, LESSTHAN, EQUALTO, GREATERTHANOREQUAL,
        LESSTHANOREQUAL, NOTEQUAL, INVALID
    }

    // Simple condition fields
    String columnName; // Name of the column
    private OperatorType operator; // Operator type: greater than, less than, equal to, etc.
    String comparisonValue; 
    boolean negation; 
    public int columnOrdinal; 
    public DataTypes dataType;

    // Compound condition fields
    public boolean isCompound = false; // Flag to indicate compound condition
    public LogicalOperator logicalOperator = LogicalOperator.NONE; // Logical operator for compound conditions
    public SpecialCondition leftCondition; // Left condition for compound
    public SpecialCondition rightCondition; // Right condition for compound

    // Supported operators for comparisons
    public static String[] supportedOperators = { "<=", ">=", "<>", ">", "<", "=" };

    // Constructor for simple condition
    public SpecialCondition(DataTypes dataType) {
        this.dataType = dataType;
    }

    // Constructor for compound condition
    public SpecialCondition(LogicalOperator logicalOperator, SpecialCondition leftCondition, SpecialCondition rightCondition) {
        this.isCompound = true;
        this.logicalOperator = logicalOperator;
        this.leftCondition = leftCondition;
        this.rightCondition = rightCondition;
    }

    // Converts operator string to OperatorType
    public static OperatorType getOpType(String strOp) {
        switch (strOp) {
            case ">": return OperatorType.GREATERTHAN;
            case "<": return OperatorType.LESSTHAN;
            case "=": return OperatorType.EQUALTO;
            case ">=": return OperatorType.GREATERTHANOREQUAL;
            case "<=": return OperatorType.LESSTHANOREQUAL;
            case "<>": return OperatorType.NOTEQUAL;
            default:
                System.out.println("! Invalid operator \"" + strOp + "\"");
                return OperatorType.INVALID;
        }
    }

    // Compares two values based on their data types
    public static int compare(String one, String two, DataTypes dataType) {
        if (dataType == DataTypes.TEXT)
            return one.toLowerCase().compareTo(two);
        else if (dataType == DataTypes.NULL) {
            if (Objects.equals(one, two)) return 0;
            else if (one.equalsIgnoreCase("null")) return 1;
            else return -1;
        } else {
            return Long.valueOf(Long.parseLong(one) - Long.parseLong(two)).intValue();
        }
    }

    // Performs operation on a comparison difference
    private boolean doOpOnDiff(OperatorType op, int diff) {
        switch (op) {
            case LESSTHANOREQUAL: return diff <= 0;
            case GREATERTHANOREQUAL: return diff >= 0;
            case NOTEQUAL: return diff != 0;
            case LESSTHAN: return diff < 0;
            case GREATERTHAN: return diff > 0;
            case EQUALTO: return diff == 0;
            default: return false;
        }
    }

    // Performs string comparison based on the operator
    private boolean doStrCom(String currVal, OperatorType op) {
        return doOpOnDiff(op, currVal.toLowerCase().compareTo(comparisonValue));
    }

    // Checks condition on a current value
    public boolean chkCondt(String currVal) {
        if (isCompound) {
            // Evaluate compound condition recursively
            boolean leftResult = leftCondition.chkCondt(currVal);
            boolean rightResult = rightCondition.chkCondt(currVal);

            if (logicalOperator == LogicalOperator.AND) {
                return leftResult && rightResult;
            } else if (logicalOperator == LogicalOperator.OR) {
                return leftResult || rightResult;
            }
        }

        OperatorType operation = getOperation();
        if (currVal.equalsIgnoreCase("null") || comparisonValue.equalsIgnoreCase("null"))
            return doOpOnDiff(operation, compare(currVal, comparisonValue, DataTypes.NULL));

        if (dataType == DataTypes.TEXT || dataType == DataTypes.NULL)
            return doStrCom(currVal, operation);
        else {
            switch (operation) {
                case LESSTHANOREQUAL: return Long.parseLong(currVal) <= Long.parseLong(comparisonValue);
                case GREATERTHANOREQUAL: return Long.parseLong(currVal) >= Long.parseLong(comparisonValue);
                case NOTEQUAL: return Long.parseLong(currVal) != Long.parseLong(comparisonValue);
                case LESSTHAN: return Long.parseLong(currVal) < Long.parseLong(comparisonValue);
                case GREATERTHAN: return Long.parseLong(currVal) > Long.parseLong(comparisonValue);
                case EQUALTO: return Long.parseLong(currVal) == Long.parseLong(comparisonValue);
                default: return false;
            }
        }
    }

    // Sets the comparison value
    public void setConditionValue(String conditionValue) {
        this.comparisonValue = conditionValue.replace("'", "").replace("\"", "");
    }

    // Sets the column name
    public void setColumName(String colName) {
        this.columnName = colName;
    }

    public void setColumnOrdinal(int colOrdinal) {
        this.columnOrdinal = colOrdinal;
    }

    // Sets the operator
    public void setOp(String op) {
        this.operator = getOpType(op);
    }

    // Sets negation
    public void setNegation(boolean negate) {
        this.negation = negate;
    }

    // Gets the operation type, considering negation
    public OperatorType getOperation() {
        if (!negation)
            return this.operator;
        else
            return negateOperator();
    }

    // Inverts the operator for NOT
    private OperatorType negateOperator() {
        switch (this.operator) {
            case LESSTHANOREQUAL: return OperatorType.GREATERTHAN;
            case GREATERTHANOREQUAL: return OperatorType.LESSTHAN;
            case NOTEQUAL: return OperatorType.EQUALTO;
            case LESSTHAN: return OperatorType.GREATERTHANOREQUAL;
            case GREATERTHAN: return OperatorType.LESSTHANOREQUAL;
            case EQUALTO: return OperatorType.NOTEQUAL;
            default:
                System.out.println("! Invalid operator \"" + this.operator + "\"");
                return OperatorType.INVALID;
        }
    }
}
