package miniJava.SyntacticAnalyzer;

import miniJava.ErrorReporter;

public class Parser {
	private Scanner _scanner;
	private ErrorReporter _errors;
	private Token _currentToken;
	
	public Parser( Scanner scanner, ErrorReporter errors ) {
		this._scanner = scanner;
		this._errors = errors;
		this._currentToken = this._scanner.scan();
	}
	
	class SyntaxError extends Error {
		private static final long serialVersionUID = -6461942006097999362L;
	}

	// (Package)
	public void parse() {
		try {
			// The first thing we need to parse is the Program symbol
			parseProgram();
		} catch( SyntaxError e ) { }
	}
	
	// Program ::= (ClassDeclaration)* eot
	private void parseProgram() throws SyntaxError {
		// Keep parsing class declarations until eot
		while (this._currentToken.getTokenType() != TokenType.EOT) {
			parseClassDeclaration();
		}
		this.accept(TokenType.EOT);
	}
	
	// ClassDeclaration ::= class identifier { (FieldDeclaration|MethodDeclaration)* } (ClassDecl)
	private void parseClassDeclaration() throws SyntaxError {
		// Take in a "class" token (check by the TokenType)
		//  What should be done if the first token isn't "class"?
		this.accept(TokenType.Class);
		
		// Take in an identifier token
		this.accept(TokenType.Identifier);
		
		// Take in a {
		this.accept(TokenType.LCurly);
		
		// Parse either a FieldDeclaration or MethodDeclaration
		while (this._currentToken.getTokenType() != TokenType.RCurly) {
			if (this._currentToken.getTokenType() == TokenType.Visibility) {
				this.accept(TokenType.Visibility);	// Visibility
			}

			if (this._currentToken.getTokenType() == TokenType.Access) {
				this.accept(TokenType.Access);		// Access
			}

			// Take in a void or type
			if (this._currentToken.getTokenType() == TokenType.Void) { // Void
				this.accept(TokenType.Void); 		// Void
				this.accept(TokenType.Identifier); 	// Identifier

				this.parseMethodDeclaration();
			} else {
				this.parseType();					// Type
				this.accept(TokenType.Identifier);	// Identifier

				// Take in a ';' for field declaration or continue if method declaration
				if (this._currentToken.getTokenType() == TokenType.Semicolon) {
					this.parseFieldDeclaration();
					break; // end of field declaration
				}

				this.parseMethodDeclaration();
			}
		}
		
		// Take in a }
		this.accept(TokenType.RCurly);
	}
	
	// FieldDeclaration ::= Visibility Access Type id; (FieldDecl)
	private void parseFieldDeclaration() {
		this.accept(TokenType.Semicolon);
	}

	// MethodDeclaration ::= Visibility Access (Type|void) id (ParameterList?) { Statement* } (MethodDecl)
	private void parseMethodDeclaration() {
		// Take in a (
		this.accept(TokenType.LParen);

		// Parse an optional Parameter List
		if (this._currentToken.getTokenType() != TokenType.RParen) {
			this.parseParameterList();
		}

		// Take in a )
		this.accept(TokenType.RParen);

		// Take in a {
		this.accept(TokenType.LCurly);

		// Parse a Statement
		while (this._currentToken.getTokenType() != TokenType.RCurly) {
			this.parseStatement();
		}

		// Take in a }
		this.accept(TokenType.RCurly);
	}
	
	// ParameterList ::= Type id (, Type id)* (ParameterDeclList)
	private void parseParameterList() {
		this.parseType();
		this.accept(TokenType.Identifier);
		
		while (this._currentToken.getTokenType() != TokenType.RParen) {
			this.accept(TokenType.Comma);
			this.parseType();
			this.accept(TokenType.Identifier);
		}
	}

	// Type ::= int | boolean | id | (int|id)[] (TypeDenoter)
	private void parseType() {
		// Take in a Type
		switch (this._currentToken.getTokenType()) {
		case IntType:
		case Identifier:
			// int | int[] | id | id[]
			this.accept(this._currentToken.getTokenType());
			if (this._currentToken.getTokenType() == TokenType.Brackets) {
				this.accept(TokenType.Brackets);
			}
			break;
		case BoolType:
			// boolean
			this.accept(TokenType.BoolType);
			break;
		default:
		}
	}
	
	// Statement ::= {Statement*} | Type id = Expression; | Reference = Expression; | Reference[Expression] = Expression; |
	// Reference(ArgumentList?); | return Expression?; | if (Expression) Statement (else Statement)? |
	// while (Expression) Statement
	private void parseStatement() {
		switch (this._currentToken.getTokenType()) {
		case LCurly:
			// { Statement* } (BlockStmt)
			this.accept(TokenType.LCurly);
			while (this._currentToken.getTokenType() != TokenType.RCurly) {
				parseStatement();
			}
			this.accept(TokenType.RCurly);
			break;
		case IntType: case BoolType:
			// Type id = Expression (VarDeclStmt)
			this.parseType();
			this.accept(TokenType.Identifier);
			this.accept(TokenType.Assignment);
			this.parseExpression();
			this.accept(TokenType.Semicolon);
			break;
		case Identifier:
			// Type vs. Reference ID
			this.accept(TokenType.Identifier);
			switch (this._currentToken.getTokenType()) {
				case Identifier:
					// Type id = Expression; (VarDeclStmt)
					this.accept(TokenType.Identifier);
					this.accept(TokenType.Assignment);
					this.parseExpression();
					this.accept(TokenType.Semicolon);
					break;
				case Brackets:
					// Type id = Expression; (VarDeclStmt)
					// id[] = Expression;
					this.accept(TokenType.Brackets);
					this.accept(TokenType.Identifier);
					this.accept(TokenType.Assignment);
					this.parseExpression();
					this.accept(TokenType.Semicolon);
					break;
				case Dot: // Reference.id (QualRef)
					this.parseReference();
					switch (this._currentToken.getTokenType()) {
						case LParen:
							// Reference( ArgumentList?); (CallStmt)
							this.parseArgList();
							this.accept(TokenType.Semicolon);
							break;
						case LSqBrack:
							// Reference[Expression] = Expression; (IxAssignStmt)
							this.accept(TokenType.LSqBrack);
							this.parseExpression();
							this.accept(TokenType.RSqBrack);
							this.accept(TokenType.Assignment);
							this.parseExpression();
							this.accept(TokenType.Semicolon);
							break;
						case Assignment:
							// Reference = Expression; (Assign Stmt)
							this.accept(TokenType.Assignment);
							this.parseExpression();
							this.accept(TokenType.Semicolon);
							break;
						default:
							break;
					}
					break;
				case LSqBrack:
					// Reference[Expression] = Expression; (IxAssignStmt)
					this.accept(TokenType.LSqBrack);
					this.parseExpression();
					this.accept(TokenType.RSqBrack);
					this.accept(TokenType.Assignment);
					this.parseExpression();
					this.accept(TokenType.Semicolon);
					break;
				case Assignment:
					// Reference = Expression; (AssignStmt)
					this.accept(TokenType.Assignment);
					this.parseExpression();
					this.accept(TokenType.Semicolon);
					break;
				case LParen:
					// Reference(ArgumentList?) (CallStmt)
					this.parseArgList();
					this.accept(TokenType.Semicolon);
					break;
				default:
			}
			break;
		case This: // this (ThisRef)
			this.parseReference();
			switch (this._currentToken.getTokenType()) {
				case LSqBrack:
					// Reference[Expression] = Expression; (IxAssignStmt)
					this.accept(TokenType.LSqBrack);
					this.parseExpression();
					this.accept(TokenType.RSqBrack);
					this.accept(TokenType.Assignment);
					this.parseExpression();
					this.accept(TokenType.Semicolon);
					break;
				case Assignment:
					// Reference = Expression; (AssignStmt)
					// ThisRef =
					this.accept(TokenType.Assignment);
					this.parseExpression();
					this.accept(TokenType.Semicolon);
					break;
				case LParen:
					// Reference(ArgumentList?) (CallStmt)
					this.parseArgList();
					this.accept(TokenType.Semicolon);
					break;
				default:
			}
			break;
		case Return:
			// return (Expression)?; (ReturnStmt)
			this.accept(TokenType.Return);
			if (this._currentToken.getTokenType() != TokenType.Semicolon) {
				this.parseExpression();
			}
			this.accept(TokenType.Semicolon);
			break;
		case If:
			// if (Expression) Statement (else Statement)? (IfStmt)
			this.accept(TokenType.If);
			this.accept(TokenType.LParen);
			this.parseExpression();
			this.accept(TokenType.RParen);
			this.parseStatement();
			if (this._currentToken.getTokenType() == TokenType.Else) {
				this.accept(TokenType.Else);
				this.parseStatement();
			}
			break;
		case While:
			// while (Expression) Statement (WhileStmt)
			this.accept(TokenType.While);
			this.parseExpression();
			this.parseStatement();
			break;
		default:
			this.reject(this._currentToken.getTokenType());
		}
		
	}

	// ( ArgumentList ::= Expression (, Expression)* ) (ExprList)
	private void parseArgList() {
		this.accept(TokenType.LParen);
		if (this._currentToken.getTokenType() != TokenType.RParen) {
			this.parseExpression();
			while (this._currentToken.getTokenType() == TokenType.Comma) {
				this.accept(TokenType.Comma);
				this.parseExpression();
			}
		}
		this.accept(TokenType.RParen);
	}
	
	// Expression ::= Reference | Reference[Expression] | Reference (ArgumentList?); | unop Expression 
	// | Expression binop Expression | ( Expression ) | num | true | false 
	// | new( id() | int[Expression] | id[Expression] ) 
	private void parseExpression() {
		switch (this._currentToken.getTokenType()) {
		case Negation: // unop Expression (UnaryExpr)
			this.accept(TokenType.Negation);
			this.parseExpression();
			break;
		case Identifier:
		case This:
			// Reference
			this.parseReference();
			switch (this._currentToken.getTokenType()) {
			case LSqBrack:
				// Reference[Expression] (IxExpr)
				this.accept(TokenType.LSqBrack);
				this.parseExpression();
				this.accept(TokenType.RSqBrack);
				break;
			case LParen:
				// Reference(ArgumentList?) (CallExpr)
				this.parseArgList();
				break;
			default:
			}
			break;
		case UnOp:
			// unop Expression (UnaryExpr)
			this.accept(TokenType.UnOp);
			this.parseExpression();
			break;
		case LParen:
			// ( Expression ) (Expression)
			this.accept(TokenType.LParen);
			this.parseExpression();
			this.accept(TokenType.RParen);
			break;
		case IntLiteral:
			// num (LiteralExpr, IntLiteral)
			this.accept(TokenType.IntLiteral);
			break;
		case BoolLiteral:
			// true | false (LiteralExpr, BooleanLiteral)
			this.accept(TokenType.BoolLiteral);
			break;
		case New:
			// new (id() | int[Expression] | id[Expression])
			this.accept(TokenType.New);
			switch (this._currentToken.getTokenType()) {
			case Identifier:
				// id
				this.accept(TokenType.Identifier);
				if (this._currentToken.getTokenType() == TokenType.LParen) {
					// new id() (NewObjectExpr)
					this.accept(TokenType.LParen);
					this.accept(TokenType.RParen);
				} else if (this._currentToken.getTokenType() == TokenType.LSqBrack) {
					// new id[Expression] (NewArrayExpr)
					this.accept(TokenType.LSqBrack);
					this.parseExpression();
					this.accept(TokenType.RSqBrack);
				}
				break;
			case IntType:
				// new int[Expression] (NewArrayExpr)
				this.accept(TokenType.IntType);
				this.accept(TokenType.LSqBrack);
				this.parseExpression();
				this.accept(TokenType.RSqBrack);
				break;
			default:
			}
			break;
		default:
			this.reject(this._currentToken.getTokenType());
			break;
		}
		// Ensure expression parsed before Expression binop Expression
		// Expression binop Expression (BinaryExpr)
		if (this._currentToken.getTokenType() == TokenType.BinOp || this._currentToken.getTokenType() == TokenType.Negation) {
			this.accept(this._currentToken.getTokenType());
			this.parseExpression();
		}
	}
	
	// Reference ::= id | this | Reference.id
	private void parseReference() {
		// this | id
		switch (this._currentToken.getTokenType()) {
		case Identifier: 		// IdRef
		case This:				// ThisRef
			this.accept(this._currentToken.getTokenType());
		default:
		}

		// Reference.id			// QualRef
		while (this._currentToken.getTokenType() == TokenType.Dot) {
			this.accept(TokenType.Dot);
			this.accept(TokenType.Identifier);
		}
	}	
	
	// This method will accept the token and retrieve the next token.
	//  Can be useful if you want to error check and accept all-in-one.
	private void accept(TokenType expectedType) throws SyntaxError {
		if( _currentToken.getTokenType() == expectedType ) {
			_currentToken = _scanner.scan();
			return;
		}
		
		// Report an error here.
		// "Expected token X, but got Y"
		this._errors.reportError(_currentToken.getTokenPosition(), "Invalid token - expecting " + expectedType + " but found "
				+ this._currentToken.getTokenType());
		throw new SyntaxError();
	}

	private void reject(TokenType tokType) throws SyntaxError {
		// Report an error here.
		// "Expected token X, but got Y"
		this._errors.reportError(_currentToken.getTokenPosition(), "Invalid token - found " + tokType);
		throw new SyntaxError();
	}
}
