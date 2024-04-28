/**
 * COMP 520
 * Method Overloading
 */
class MainClass {
    public static void main (String [] args) {
        System.out.println(myMethod(0, 1));
        System.out.println(myMethod(0, true));
    }

    public static int myMethod(int m1, int m2) {
        return 48;
    }

    public static int myMethod(int x, boolean z) {
        return 49;
    }
}


