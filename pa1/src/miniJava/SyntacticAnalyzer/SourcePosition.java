package miniJava.SyntacticAnalyzer;

public class SourcePosition {
    private int _lineNumber;
    private int _columnNumber;

    public SourcePosition(int lineNumber, int columnNumber) {
        this._lineNumber = lineNumber;
        this._columnNumber = columnNumber;
    }

    public String toString() {
        // Return the token text
        return "Line " + this._lineNumber + " Column " + this._columnNumber;
    }
}
