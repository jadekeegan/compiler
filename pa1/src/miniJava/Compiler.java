package miniJava;

import java.io.FileInputStream;

import miniJava.AbstractSyntaxTrees.ASTDisplay;
import miniJava.ContextualAnalysis.Identification;
import miniJava.ContextualAnalysis.TypeChecking;
import miniJava.SyntacticAnalyzer.Parser;
import miniJava.SyntacticAnalyzer.Scanner;
import miniJava.AbstractSyntaxTrees.Package;

public class Compiler {
	// Main function, the file to compile will be an argument.
	public static void main(String[] args) {
		// Instantiate the ErrorReporter object
		ErrorReporter _errors = new ErrorReporter();
		
		// Check to make sure a file path is given in args
		FileInputStream inputStream = null;
		try {
			String filePath = args[0];

			// Create the inputStream using new FileInputStream
			inputStream = new FileInputStream(filePath);
		} catch (Exception e) {
			System.out.println("Error: File Not Found");
			System.exit(1);
		}

		// Instantiate the scanner with the input stream and error object
		Scanner scanner = new Scanner(inputStream, _errors);

		// Instantiate the parser with the scanner and error object
		Parser parser = new Parser(scanner, _errors);

		// Call the parser's parse function
		Package ASTPackage = parser.parse();

		// Check if any errors exist, if so, println("Error")
		//  then output the errors
		if (_errors.hasErrors()) {
			System.out.println("Error");
			_errors.outputErrors();
		}

		Identification identification = new Identification(_errors);
		identification.parse(ASTPackage);

		if (_errors.hasErrors()) {
			System.out.println("Error");
			_errors.outputErrors();
		}

		TypeChecking typeChecking = new TypeChecking(_errors);
		typeChecking.parse(ASTPackage);

		if (_errors.hasErrors()) {
			System.out.println("Error");
			_errors.outputErrors();
		}

		System.out.println("Success");
	}
}
