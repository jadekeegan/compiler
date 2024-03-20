package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.Declaration;

import java.util.HashMap;

public class IDTable {
    private HashMap<String, Declaration> idTable = new HashMap<>();

    IDTable() { }

    public void put(String key, Declaration decl) {
        this.idTable.put(key, decl);
    }

    public void remove(String key) {
        this.idTable.remove(key);
    }

    public Declaration get(String key) {
        return this.idTable.get(key);
    }

    public boolean containsKey(String key) {
        return this.idTable.containsKey(key);
    }
}
