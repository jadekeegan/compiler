/**
 * COMP 520
 * Method invocation
 */
class MainClass {
   public static void main (String [] args) {

      FirstClass f = new FirstClass ();
      f.testme ();
   }
}

class FirstClass
{
   int n;

   public void testme ()
   {
      int tstvar = 49;
      System.out.println(tstvar);
	  tstvar = 48;
	  System.out.println(tstvar);
   }
}
