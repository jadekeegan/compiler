/**
 * COMP 520
 * simple int value and printing
 */
class MainClass {
    public static void main (String [] args) {
        A a = new A();
        a.b = new B();
        a.b.x = 1;
//        System.out.println(a.b.x);
    }
}

class A {
    int y;
    B b;
}

class B {
    int x;
}
