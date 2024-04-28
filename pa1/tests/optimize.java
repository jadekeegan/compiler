/**
 * COMP 520
 * Optimization Example
 * Comment out the "optimizer.optimize()" method call in
 * CodeGenerator.java to compare unoptimized vs optimized outputs.
 */
class MainClass {
    public static void main (String [] args) {
        A a = new A();
        a.x = 48;
        System.out.println(a.x + 3);
    }
}

class A {
    int x;
}


