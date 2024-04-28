/**
 * COMP 520
 * Object Allocation
 */
class MainClass {
    public static void main (String [] args) {
        A a = new A();
        a.x = 3;
        System.out.println(a.x + 48);

        int[] arr = new int[5];
        arr[0] = 4;
        arr[1] = 5;
        System.out.println(arr[0] + 48);
        System.out.println(arr[1] + 48);
    }
}

class A {
    int x;
}


