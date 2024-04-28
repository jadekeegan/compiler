package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.Declaration;

import java.util.ArrayList;
import java.util.HashMap;

public class IDTable {
    private HashMap<String, ArrayList<Declaration>> idTable = new HashMap<>();

    IDTable() { }

    public void put(String key, Declaration decl) {
        if (this.idTable.containsKey(key)) {
            this.idTable.get(key).add(decl);
        } else {
            ArrayList<Declaration> declList = new ArrayList<>();
            declList.add(decl);
            this.idTable.put(key, declList);
        }
    }

    public void remove(String key) {
        this.idTable.remove(key);
    }

    public ArrayList<Declaration> get(String key) {
        return this.idTable.get(key);
    }

    public boolean containsKey(String key) {
        return this.idTable.containsKey(key);
    }
}
