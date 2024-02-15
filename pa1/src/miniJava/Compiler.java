package miniJava;

import java.io.FileInputStream;

import miniJava.SyntacticAnalyzer.Parser;
import miniJava.SyntacticAnalyzer.Scanner;

public class Compiler {
	// Main function, the file to compile will be an argument.
	public static void main(String[] args) {
		// Instantiate the ErrorReporter object
		ErrorReporter _errors = new ErrorReporter();
		
		// Check to make sure a file path is given in args
		try {
			String filePath = args[0];
			
			// Create the inputStream using new FileInputStream
			FileInputStream inputStream = new FileInputStream(filePath);
			
			// Instantiate the scanner with the input stream and error object
			Scanner scanner = new Scanner(inputStream, _errors);
			
			// Instantiate the parser with the scanner and error object
			Parser parser = new Parser(scanner, _errors);
			
			// Call the parser's parse function
			parser.parse();
			
			// Check if any errors exist, if so, println("Error")
			//  then output the errors
			if (_errors.hasErrors()) {
				System.out.println("Error");
				_errors.outputErrors();
			} else {
				// If there are no errors, println("Success")
				System.out.println("Success");
			}
		} catch (Exception e) {
			System.out.println("Error: File Not Found");
		}
	}
}
