/**
 * COMP 520
 * Object creation and field reference
 */
class MainClass {
   public static void main (String [] args) {

       FirstClass f = new FirstClass ();
	   f.n = 48;
       int tstvar = 5 + f.n;

       System.out.println(tstvar);
   }
}

class FirstClass
{
   int n;

}



