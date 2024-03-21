package miniJava.SyntacticAnalyzer;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;

import javax.xml.transform.Source;

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
	public Package parse() {
		try {
			// The first thing we need to parse is the Program symbol
			return parseProgram();
		} catch( SyntaxError e ) { }
		return null;
	}
	
	// Program ::= (ClassDeclaration)* eot
	private Package parseProgram() throws SyntaxError {
		// Keep parsing class declarations until eot
		ClassDeclList classDeclList = new ClassDeclList();
		while (this._currentToken.getTokenType() != TokenType.EOT) {
			classDeclList.add(parseClassDeclaration());
		}
		this.accept(TokenType.EOT);
		return new Package(classDeclList, this._currentToken.getTokenPosition());
	}
	
	// ClassDeclaration ::= class identifier { (FieldDeclaration|MethodDeclaration)* } (ClassDecl)
	private ClassDecl parseClassDeclaration() throws SyntaxError {
		// Take in a "class" token (check by the TokenType)
		//  What should be done if the first token isn't "class"?
		this.accept(TokenType.Class);
		
		// Take in an identifier token
		String cn = this._currentToken.getTokenText();
		this.accept(TokenType.Identifier);
		
		// Take in a {
		this.accept(TokenType.LCurly);
		
		// Parse either a FieldDeclaration or MethodDeclaration
		FieldDeclList fieldDeclList = new FieldDeclList();
		MethodDeclList methodDeclList = new MethodDeclList();
		while (this._currentToken.getTokenType() != TokenType.RCurly) {
			boolean isPrivate = false;
			boolean isStatic = false;

			if (this._currentToken.getTokenType() == TokenType.Visibility) {
				if (this._currentToken.getTokenText().equals("private")) {
					isPrivate = true;
				}
				this.accept(TokenType.Visibility);	// Visibility
			}

			if (this._currentToken.getTokenType() == TokenType.Access) {
				isStatic = true;
				this.accept(TokenType.Access);		// Access
			}

			// Take in a void or type
			if (this._currentToken.getTokenType() == TokenType.Void) { // Void
				this.accept(TokenType.Void); 		// Void

				String name = this._currentToken.getTokenText();
				this.accept(TokenType.Identifier); 	// Identifier

				FieldDecl field = this.parseFieldDeclaration(isPrivate, isStatic,
						new BaseType(TypeKind.VOID, this._currentToken.getTokenPosition()), name);
				methodDeclList.add(this.parseMethodDeclaration(field));
			} else {
				TypeDenoter type = this.parseType();					// Type

				String name = this._currentToken.getTokenText();
				this.accept(TokenType.Identifier);	// Identifier

				// Take in a ';' for field declaration or continue if method declaration
				FieldDecl field = this.parseFieldDeclaration(isPrivate, isStatic, type, name);
				if (this._currentToken.getTokenType() == TokenType.Semicolon) {
					this.accept(TokenType.Semicolon);
					fieldDeclList.add(field);
					continue; // end of field declaration
				}

				methodDeclList.add(this.parseMethodDeclaration(field));
			}
		}
		
		// Take in a }
		this.accept(TokenType.RCurly);
		return new ClassDecl(cn, fieldDeclList, methodDeclList, this._currentToken.getTokenPosition());
	}
	
	// FieldDeclaration ::= Visibility Access Type id; (FieldDecl)
	private FieldDecl parseFieldDeclaration(boolean isPrivate, boolean isStatic, TypeDenoter type, String name) {
		return new FieldDecl(isPrivate, isStatic, type, name, this._currentToken.getTokenPosition());
	}

	// MethodDeclaration ::= Visibility Access (Type|void) id (ParameterList?) { Statement* } (MethodDecl)
	private MethodDecl parseMethodDeclaration(FieldDecl field) {
		// Take in a (
		this.accept(TokenType.LParen);

		// Parse an optional Parameter List
		ParameterDeclList parameterDeclList = new ParameterDeclList();
		if (this._currentToken.getTokenType() != TokenType.RParen) {
			parameterDeclList = this.parseParameterList();
		}

		// Take in a )
		this.accept(TokenType.RParen);

		// Take in a {
		this.accept(TokenType.LCurly);

		// Parse a Statement
		StatementList statementList = new StatementList();
		while (this._currentToken.getTokenType() != TokenType.RCurly) {
			statementList.add(this.parseStatement());
		}

		// Take in a }
		this.accept(TokenType.RCurly);
		return new MethodDecl(field, parameterDeclList, statementList, this._currentToken.getTokenPosition());
	}

	// Type ::= int | boolean | id | (int|id)[] (TypeDenoter)
	private TypeDenoter parseType() {
		// Take in a Type
		SourcePosition position = this._currentToken.getTokenPosition();
		TypeDenoter type;
		switch (this._currentToken.getTokenType()) {
		case IntType: // int | int[]
			this.accept(this._currentToken.getTokenType());
			type = new BaseType(TypeKind.INT, position);
			if (this._currentToken.getTokenType() == TokenType.Brackets) {
				this.accept(TokenType.Brackets);
				return new ArrayType(type, this._currentToken.getTokenPosition());
			}
			return type;
		case Identifier:
			// id | id[]
			Token id = this._currentToken;
			this.accept(this._currentToken.getTokenType());
			type = new ClassType(new Identifier(id), position);
			if (this._currentToken.getTokenType() == TokenType.Brackets) {
				this.accept(TokenType.Brackets);
				return new ArrayType(type, this._currentToken.getTokenPosition());
			}
			return type;
		case BoolType:
			// boolean
			this.accept(TokenType.BoolType);
			return new BaseType(TypeKind.BOOLEAN, position);
		default:
		}
		return null;
	}

	// ParameterList ::= Type id (, Type id)* (ParameterDeclList)
	private ParameterDeclList parseParameterList() {
		ParameterDeclList parameterDeclList = new ParameterDeclList();
		TypeDenoter type = this.parseType();
		String cn = this._currentToken.getTokenText();
		this.accept(TokenType.Identifier);
		parameterDeclList.add(new ParameterDecl(type, cn, this._currentToken.getTokenPosition()));

		while (this._currentToken.getTokenType() != TokenType.RParen) {
			this.accept(TokenType.Comma);
			type = this.parseType();
			cn = this._currentToken.getTokenText();
			this.accept(TokenType.Identifier);
			parameterDeclList.add(new ParameterDecl(type, cn, this._currentToken.getTokenPosition()));
		}
		return parameterDeclList;
	}

	// ( ArgumentList ::= Expression (, Expression)* ) (ExprList)
	private ExprList parseArgumentList() {
		this.accept(TokenType.LParen);

		ExprList exprList = new ExprList();
		if (this._currentToken.getTokenType() != TokenType.RParen) {
			exprList.add(this.parseExpression());
			while (this._currentToken.getTokenType() == TokenType.Comma) {
				this.accept(TokenType.Comma);
				exprList.add(this.parseExpression());
			}
		}
		this.accept(TokenType.RParen);
		return exprList;
	}

	// Reference ::= id | this | Reference.id (Reference)
	private Reference parseReference(Reference reference) {
		// this | id
		switch (this._currentToken.getTokenType()) {
			case Identifier: 		// IdRef
				Token id = this._currentToken;
				this.accept(this._currentToken.getTokenType());
				reference = new IdRef(new Identifier(id), id.getTokenPosition());
				break;
			case This:				// ThisRef
				this.accept(this._currentToken.getTokenType());
				reference = new ThisRef(this._currentToken.getTokenPosition());
				break;
			default:
		}

		// Reference.id				// QualRef
		while (this._currentToken.getTokenType() == TokenType.Dot) {
			this.accept(TokenType.Dot);
			Token id = this._currentToken;
			this.accept(TokenType.Identifier);
			reference = new QualRef(reference, new Identifier(id), id.getTokenPosition());
		}
		return reference;
	}

	// Statement ::= {Statement*} | Type id = Expression; | Reference = Expression; | Reference[Expression] = Expression; |
	// Reference(ArgumentList?); | return Expression?; | if (Expression) Statement (else Statement)? |
	// while (Expression) Statement
	private Statement parseStatement() {
		TypeDenoter type;
		Expression expression, expression1, expression2;
		VarDecl varDecl;
		String name;
		Reference reference;
		Statement statement, statement1;
		switch (this._currentToken.getTokenType()) {
		case LCurly:
			// { Statement* } (BlockStmt)
			this.accept(TokenType.LCurly);
			StatementList statementList = new StatementList();
			while (this._currentToken.getTokenType() != TokenType.RCurly) {
				statementList.add(parseStatement());
			}
			this.accept(TokenType.RCurly);
			return new BlockStmt(statementList, this._currentToken.getTokenPosition());
		case IntType: case BoolType:
			// Type id = Expression (VarDeclStmt)
			type = this.parseType();
			name = this._currentToken.getTokenText();
			this.accept(TokenType.Identifier);
			varDecl = new VarDecl(type, name, this._currentToken.getTokenPosition());
			this.accept(TokenType.Assignment);
			expression = this.parseExpression();
			this.accept(TokenType.Semicolon);
			return new VarDeclStmt(varDecl, expression, this._currentToken.getTokenPosition());
		case Identifier:
			// Type vs. Reference ID
			Token id = this._currentToken;
			this.accept(TokenType.Identifier);
			type = new ClassType(new Identifier(id), id.getTokenPosition());
			reference = new IdRef(new Identifier(id), id.getTokenPosition());
			switch (this._currentToken.getTokenType()) {
				case Identifier:
					// Type id = Expression; (VarDeclStmt)
					name = this._currentToken.getTokenText();
					this.accept(TokenType.Identifier);
					varDecl = new VarDecl(type, name, this._currentToken.getTokenPosition());
					this.accept(TokenType.Assignment);
					expression = this.parseExpression();
					this.accept(TokenType.Semicolon);
					return new VarDeclStmt(varDecl, expression, this._currentToken.getTokenPosition());
				case Brackets:
					// Type id = Expression; (VarDeclStmt)
					// id[] = Expression;
					this.accept(TokenType.Brackets);
					name = this._currentToken.getTokenText();
					this.accept(TokenType.Identifier);
					varDecl = new VarDecl(new ArrayType(type, this._currentToken.getTokenPosition()), name, this._currentToken.getTokenPosition());
					this.accept(TokenType.Assignment);
					expression = this.parseExpression();
					this.accept(TokenType.Semicolon);
					return new VarDeclStmt(varDecl, expression, this._currentToken.getTokenPosition());
				case Dot: // Reference.id (QualRef)
					reference = this.parseReference(reference);
					switch (this._currentToken.getTokenType()) {
						case LParen:
							// Reference( ArgumentList?); (CallStmt)
							ExprList expressionList = this.parseArgumentList();
							this.accept(TokenType.Semicolon);
							return new CallStmt(reference, expressionList, this._currentToken.getTokenPosition());
						case LSqBrack:
							// Reference[Expression] = Expression; (IxAssignStmt)
							this.accept(TokenType.LSqBrack);
							expression1 = this.parseExpression();
							this.accept(TokenType.RSqBrack);
							this.accept(TokenType.Assignment);
							expression2 = this.parseExpression();
							this.accept(TokenType.Semicolon);
							return new IxAssignStmt(reference, expression1, expression2, this._currentToken.getTokenPosition());
						case Assignment:
							// Reference = Expression; (Assign Stmt)
							this.accept(TokenType.Assignment);
							expression = this.parseExpression();
							this.accept(TokenType.Semicolon);
							return new AssignStmt(reference, expression, this._currentToken.getTokenPosition());
						default:
							break;
					}
					break;
				case LSqBrack:
					// Reference[Expression] = Expression; (IxAssignStmt)
					this.accept(TokenType.LSqBrack);
					expression1 = this.parseExpression();
					this.accept(TokenType.RSqBrack);
					this.accept(TokenType.Assignment);
					expression2 = this.parseExpression();
					this.accept(TokenType.Semicolon);
					return new IxAssignStmt(reference, expression1, expression2, this._currentToken.getTokenPosition());
				case Assignment:
					// Reference = Expression; (AssignStmt)
					this.accept(TokenType.Assignment);
					expression = this.parseExpression();
					this.accept(TokenType.Semicolon);
					return new AssignStmt(reference, expression, this._currentToken.getTokenPosition());
				case LParen:
					// Reference(ArgumentList?) (CallStmt)
					ExprList exprList = this.parseArgumentList();
					this.accept(TokenType.Semicolon);
					return new CallStmt(reference, exprList, this._currentToken.getTokenPosition());
				default:
			}
			break;
		case This: // this (ThisRef)
			reference = this.parseReference(null);
			switch (this._currentToken.getTokenType()) {
				case LSqBrack:
					// Reference[Expression] = Expression; (IxAssignStmt)
					this.accept(TokenType.LSqBrack);
					expression1 = this.parseExpression();
					this.accept(TokenType.RSqBrack);
					this.accept(TokenType.Assignment);
					expression2 = this.parseExpression();
					this.accept(TokenType.Semicolon);
					return new IxAssignStmt(reference, expression1, expression2, this._currentToken.getTokenPosition());
				case Assignment:
					// Reference = Expression; (AssignStmt)
					this.accept(TokenType.Assignment);
					expression = this.parseExpression();
					this.accept(TokenType.Semicolon);
					return new AssignStmt(reference, expression, this._currentToken.getTokenPosition());
				case LParen:
					// Reference(ArgumentList?) (CallStmt)
					ExprList exprList = this.parseArgumentList();
					this.accept(TokenType.Semicolon);
					return new CallStmt(reference, exprList, this._currentToken.getTokenPosition());
				default:
			}
			break;
		case Return:
			// return (Expression)?; (ReturnStmt)
			this.accept(TokenType.Return);
			expression = null;
			if (this._currentToken.getTokenType() != TokenType.Semicolon) {
				expression = this.parseExpression();
			}
			this.accept(TokenType.Semicolon);
			return new ReturnStmt(expression, this._currentToken.getTokenPosition());
		case If:
			// if (Expression) Statement (else Statement)? (IfStmt)
			this.accept(TokenType.If);
			this.accept(TokenType.LParen);
			expression = this.parseExpression();
			this.accept(TokenType.RParen);
			statement = this.parseStatement();
			if (this._currentToken.getTokenType() == TokenType.Else) {
				this.accept(TokenType.Else);
				statement1 = this.parseStatement();
				return new IfStmt(expression, statement, statement1, this._currentToken.getTokenPosition());
			}
			return new IfStmt(expression, statement, this._currentToken.getTokenPosition());
		case While:
			// while (Expression) Statement (WhileStmt)
			this.accept(TokenType.While);
			expression = this.parseExpression();
			statement = this.parseStatement();
			return new WhileStmt(expression, statement, this._currentToken.getTokenPosition());
		default:
			this.reject(this._currentToken.getTokenType());
		}
		return null;
	}
	
	// Expression ::= Reference | Reference[Expression] | Reference (ArgumentList?); | unop Expression 
	// | Expression binop Expression | ( Expression ) | num | true | false 
	// | new( id() | int[Expression] | id[Expression] )
	private Expression parseExpression() {
		return parseDisjunction();
	}

	private Expression parseDisjunction() {
		Expression e1 = this.parseConjunction();
		while (this._currentToken.getTokenText().equals("||")) {
			Operator op = new Operator(this._currentToken);
			this.accept(this._currentToken.getTokenType());
			Expression e2 = parseConjunction();
			e1 = new BinaryExpr(op, e1, e2, this._currentToken.getTokenPosition());
		}
		return e1;
	}

	private Expression parseConjunction() {
		Expression e1 = this.parseEquality();
		while (this._currentToken.getTokenText().equals("&&")) {
			Operator op = new Operator(this._currentToken);
			this.accept(this._currentToken.getTokenType());
			Expression e2 = parseEquality();
			e1 = new BinaryExpr(op, e1, e2, this._currentToken.getTokenPosition());
		}
		return e1;
	}

	private Expression parseEquality() {
		Expression e1 = this.parseRelational();
		while (this._currentToken.getTokenText().equals("==") || this._currentToken.getTokenText().equals("!=")) {
			Operator op = new Operator(this._currentToken);
			this.accept(this._currentToken.getTokenType());
			Expression e2 = parseRelational();
			e1 = new BinaryExpr(op, e1, e2, this._currentToken.getTokenPosition());
		}
		return e1;
	}

	private Expression parseRelational() {
		Expression e1 = this.parseAdditive();
		while (this._currentToken.getTokenText().equals("<=") || this._currentToken.getTokenText().equals("<")
				|| this._currentToken.getTokenText().equals(">") || this._currentToken.getTokenText().equals(">=")) {
			Operator op = new Operator(this._currentToken);
			this.accept(this._currentToken.getTokenType());
			Expression e2 = parseAdditive();
			e1 = new BinaryExpr(op, e1, e2, this._currentToken.getTokenPosition());
		}
		return e1;
	}

	private Expression parseAdditive() {
		Expression e1 = this.parseMultiplicative();
		while (this._currentToken.getTokenText().equals("+") || this._currentToken.getTokenText().equals("-")) {
			Operator op = new Operator(this._currentToken);
			this.accept(this._currentToken.getTokenType());
			Expression e2 = parseMultiplicative();
			e1 = new BinaryExpr(op, e1, e2, this._currentToken.getTokenPosition());
		}
		return e1;
	}

	private Expression parseMultiplicative() {
		Expression e1 = this.parseUnary();
		while (this._currentToken.getTokenText().equals("*") || this._currentToken.getTokenText().equals("/")) {
			Operator op = new Operator(this._currentToken);
			this.accept(this._currentToken.getTokenType());
			Expression e2 = parseUnary();
			e1 = new BinaryExpr(op, e1, e2, this._currentToken.getTokenPosition());
		}
		return e1;
	}

	private Expression parseUnary() {
		if (this._currentToken.getTokenType() == TokenType.UnOp || this._currentToken.getTokenType() == TokenType.Negation) {
			Operator op = new Operator(this._currentToken);
			this.accept(this._currentToken.getTokenType());
			return new UnaryExpr(op, this.parseUnary(), this._currentToken.getTokenPosition());
		}
		return this.parseParentheses();
	}

	private Expression parseParentheses() {
		if (this._currentToken.getTokenType() == TokenType.LParen) {
			this.accept(TokenType.LParen);
			Expression e = parseDisjunction();
			this.accept(TokenType.RParen);
			return e;
		} else if (this._currentToken.getTokenType() == TokenType.IntLiteral) {
			IntLiteral terminal = new IntLiteral(this._currentToken);
			this.accept(TokenType.IntLiteral);
			return new LiteralExpr(terminal, this._currentToken.getTokenPosition());
		} else if (this._currentToken.getTokenType() == TokenType.BoolLiteral) {
			// true | false (LiteralExpr, BooleanLiteral)
			BooleanLiteral terminal = new BooleanLiteral(this._currentToken);
			this.accept(TokenType.BoolLiteral);
			return new LiteralExpr(terminal, this._currentToken.getTokenPosition());
		} else if (this._currentToken.getTokenType() == TokenType.NullLiteral) {
			NullLiteral terminal = new NullLiteral(this._currentToken);
			this.accept(TokenType.NullLiteral);
			return new LiteralExpr(terminal, this._currentToken.getTokenPosition());
		} else if (this._currentToken.getTokenType() == TokenType.Identifier || this._currentToken.getTokenType() == TokenType.This) {
			Reference reference = this.parseReference(null);
			Expression e1 = new RefExpr(reference, this._currentToken.getTokenPosition());
			switch (this._currentToken.getTokenType()) {
				case LSqBrack:
					// Reference[Expression] (IxExpr)
					this.accept(TokenType.LSqBrack);
					e1 = new IxExpr(reference, this.parseDisjunction(), this._currentToken.getTokenPosition());
					this.accept(TokenType.RSqBrack);
					break;
				case LParen:
					// Reference(ArgumentList?) (CallExpr)
					e1 = new CallExpr(reference, this.parseArgumentList(), this._currentToken.getTokenPosition());
					break;
				default:
			}
			return e1;
		} else if (this._currentToken.getTokenType() == TokenType.New) {
			// new (id() | int[Expression] | id[Expression])
			Expression e1;
			this.accept(TokenType.New);
			switch (this._currentToken.getTokenType()) {
				case Identifier:
					// id
					Token id = this._currentToken;
					this.accept(TokenType.Identifier);
					ClassType type = new ClassType(new Identifier(id), id.getTokenPosition());
					if (this._currentToken.getTokenType() == TokenType.LParen) {
						// new id() (NewObjectExpr)
						this.accept(TokenType.LParen);
						this.accept(TokenType.RParen);
						return new NewObjectExpr(type, this._currentToken.getTokenPosition());
					} else if (this._currentToken.getTokenType() == TokenType.LSqBrack) {
						// new id[Expression] (NewArrayExpr)
						this.accept(TokenType.LSqBrack);
						e1 = new NewArrayExpr(type, this.parseDisjunction(), this._currentToken.getTokenPosition());
						this.accept(TokenType.RSqBrack);
						return e1;
					} else {
						this.reject(this._currentToken.getTokenType());
					}
					break;
				case IntType:
					// new int[Expression] (NewArrayExpr)
					this.accept(TokenType.IntType);
					this.accept(TokenType.LSqBrack);
					e1 = new NewArrayExpr(new BaseType(TypeKind.INT, this._currentToken.getTokenPosition()), this.parseDisjunction(), this._currentToken.getTokenPosition());
					this.accept(TokenType.RSqBrack);
					return e1;
				default:
			}
		} else {
			this.reject(this._currentToken.getTokenType());
		}
		return null;
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
