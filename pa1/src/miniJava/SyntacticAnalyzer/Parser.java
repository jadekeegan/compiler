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
	
	// ClassDeclaration ::= class identifier { (FieldDeclaration|MethodDeclaration)* }
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
			this.parseClassBody();
		}
		
		// Take in a }
		this.accept(TokenType.RCurly);
	}
	
	// FieldDeclaration ::= Visibility Access Type id;
	// MethodDeclaration ::= Visibility Access (Type|void) id (ParameterList?) { Statement* }
	private void parseClassBody() {
		if (this._currentToken.getTokenType() == TokenType.Visibility) {
			this.accept(TokenType.Visibility);	// Visibility
		}

		if (this._currentToken.getTokenType() == TokenType.Access) {
			this.accept(TokenType.Access);		// Access
		}
		
		// Take in a type or void
		if (this._currentToken.getTokenType() == TokenType.Void) {
			this.accept(TokenType.Void);

			// Take in an id
			this.accept(TokenType.Identifier);

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
		} else {
			this.parseType();

			// Take in an id
			this.accept(TokenType.Identifier);

			// Take in a ';' for field declaration or continue if method declaration
			if (this._currentToken.getTokenType() == TokenType.Semicolon) {
				this.accept(TokenType.Semicolon);
				return; // end of field declaration
			}

			if (this._currentToken.getTokenType() == TokenType.LParen) {
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
		}
	}
	
	// ParameterList ::= Type id (, Type id)*
	private void parseParameterList() {
		this.parseType();
		this.accept(TokenType.Identifier);
		
		while (this._currentToken.getTokenType() != TokenType.RParen) {
			this.accept(TokenType.Comma);
			this.parseType();
			this.accept(TokenType.Identifier);
		}
	}
	
	private void parseType() {
		// Take in a Type
		switch (this._currentToken.getTokenType()) {
		case IntType:
		case Identifier:
			// int | int[] | id | id[]
			this.accept(this._currentToken.getTokenType());
			this.handleArrayBrackets();
			break;
		case BoolType:
			// boolean
			this.accept(TokenType.BoolType);
			break;
		default:
		}
	}
	
	// Handle []
	private void handleArrayBrackets() {
		if (this._currentToken.getTokenType() == TokenType.Brackets) {
			this.accept(TokenType.Brackets);
		}
	}
	
	// Statement ::= {Statement*} | Type id = Expression; | Reference = Expression; | Reference[Expression] = Expression; |
	// Reference(ArgumentList?); | return Expression?; | if (Expression) Statement (else Statement)? |
	// while (Expression) Statement
	private void parseStatement() {
		switch (this._currentToken.getTokenType()) {
		case LCurly:
			// {Statement*}
			this.accept(TokenType.LCurly);
			while (this._currentToken.getTokenType() != TokenType.RCurly) {
				parseStatement();
			}
			this.accept(TokenType.RCurly);
			break;
		case IntType: case BoolType:
			// Type id = Expression
			this.parseType();
			this.parseTypeAssignment();
			break;
		case This:
			// Reference = Expression; | Reference[Expression] | Reference(ArgumentList?)
			this.parseReference();
			parseReferenceStatement();
			break;
		case Identifier:
			// Type vs. Reference ID
			this.accept(TokenType.Identifier);
			switch (this._currentToken.getTokenType()) {
				case Identifier:
					// id = Expression;
					this.parseTypeAssignment();
					break;
				case Dot:
					// Reference.id
					this.parseReference();
					switch (this._currentToken.getTokenType()) {
						case LParen:
							this.parseArgList();
							this.accept(TokenType.Semicolon);
							break;
						case LSqBrack:
							this.parseArrayReference();
							this.parseExpressionAssignment();
							break;
						case Assignment:
							this.parseExpressionAssignment();
							break;
						default:
							break;
					}
					break;
				case Brackets:
					this.accept(TokenType.Brackets);
					this.parseTypeAssignment();
					break;
				case LSqBrack:
					this.accept(TokenType.LSqBrack);
					if (this._currentToken.getTokenType() != TokenType.RSqBrack) {
						// foo[1] = Expression
						this.parseExpression();
						this.accept(TokenType.RSqBrack);
						this.parseExpressionAssignment();
					}
					break;
				default:
					this.parseReferenceStatement();
					break;
			}
			break;
		case Return:
			// return Expression?
			this.accept(TokenType.Return);
			if (this._currentToken.getTokenType() != TokenType.Semicolon) {
				this.parseExpression();
			}
			this.accept(TokenType.Semicolon);
			break;
		case If:
			// if (Expression) Statement (else Statement)?
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
			// while (Expression) Statement
			this.accept(TokenType.While);
			this.parseExpression();
			this.parseStatement();
			break;
		default:
			this.reject(this._currentToken.getTokenType());
		}
		
	}
	
	// Type id = Expression;
	private void parseTypeAssignment() {
		this.accept(TokenType.Identifier);
		this.parseExpressionAssignment();
	}
	
	// Reference = Expression;
	private void parseReferenceStatement() {
		switch (this._currentToken.getTokenType()) {
		case LSqBrack:
			// Reference[Expression]
			this.parseArrayReference();
			this.accept(TokenType.Assignment);
			this.parseExpression();
			this.accept(TokenType.Semicolon);
			break;
		case Assignment:
			// = Expression;
			this.parseExpressionAssignment();
			break;
		case LParen:
			// Reference(ArgumentList?)
			this.parseArgList();
			this.accept(TokenType.Semicolon);
			break;
		default:
		}		
	}
	
	// = Expression;
	private void parseExpressionAssignment() {
		this.accept(TokenType.Assignment);
		this.parseExpression();
		this.accept(TokenType.Semicolon);
	}
	
	// ( ArgumentList ::= Expression (, Expression)* )
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
	
	// [Expression]
	private void parseArrayReference() {
		this.accept(TokenType.LSqBrack);
		this.parseExpression();
		this.accept(TokenType.RSqBrack);
	}
	
	// Expression ::= Reference | Reference[Expression] | Reference (ArgumentList?); | unop Expression 
	// | Expression binop Expression | ( Expression ) | num | true | false 
	// | new( id() | int[Expression] | id[Expression] ) 
	private void parseExpression() {
		switch (this._currentToken.getTokenType()) {
		case Negation:
			this.accept(TokenType.Negation);
			this.parseExpression();
			break;
		case Identifier:
		case This:
			// Reference
			this.parseReference();
			switch (this._currentToken.getTokenType()) {
			case LSqBrack:
				// [ Expression ]
				this.parseArrayReference();
				break;
			case LParen:
				// (ArgumentList?)
				this.parseArgList();
				break;
			default:
			}
			break;
		case UnOp:
			// unop Expression
			this.accept(TokenType.UnOp);
			this.parseExpression();
			break;
		case LParen:
			// ( Expression )
			this.accept(TokenType.LParen);
			this.parseExpression();
			this.accept(TokenType.RParen);
			break;
		case IntLiteral:
			// num
			this.accept(TokenType.IntLiteral);
			break;
		case BoolLiteral:
			// true | false
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
					// id()
					this.accept(TokenType.LParen);
					this.accept(TokenType.RParen);
				} else if (this._currentToken.getTokenType() == TokenType.LSqBrack) {
					// id[Expression]
					this.parseArrayReference();
				}
				break;
			case IntType:
				// int[Expression]
				this.accept(TokenType.IntType);
				this.parseArrayReference();
				break;
			default:
			}
			break;
		default:
			this.reject(this._currentToken.getTokenType());
			break;
		}
		// Ensure expression parsed before Expression binop Expression
		if (this._currentToken.getTokenType() == TokenType.BinOp || this._currentToken.getTokenType() == TokenType.Negation) {
			this.accept(this._currentToken.getTokenType());
			this.parseExpression();
		}
	}
	
	// Reference ::= id | this | Reference.id
	private void parseReference() {
		// this | id
		switch (this._currentToken.getTokenType()) {
		case Identifier:
		case This:
			this.accept(this._currentToken.getTokenType());
		default:
		}

		// Reference.id
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
