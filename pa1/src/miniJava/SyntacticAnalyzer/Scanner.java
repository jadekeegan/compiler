package miniJava.SyntacticAnalyzer;

import java.io.IOException;
import java.io.InputStream;
import miniJava.ErrorReporter;

public class Scanner {
	private InputStream _in;
	private ErrorReporter _errors;
	private StringBuilder _currentText;
	private char _currentChar;
	private boolean eot = false;
	
	public Scanner( InputStream in, ErrorReporter errors ) {
		this._in = in;
		this._errors = errors;
		this._currentText = new StringBuilder();
		
		nextChar();
	}
	
	public Token scan() {
		// This function should check the current char to determine what the token could be.

		// Consider what happens if the current char is whitespace
		while (this.eot != false && this._currentChar == ' ') {
			this.skipIt();
		}
			
		// TODO: Consider what happens if there is a comment (// or /* */)
		// Check current character and previous character
		if (this._currentChar == '/') {
			this.nextChar();
			if (this._currentChar == '/') {
				this.skipInLineComment();
			} else if (this._currentChar == '*') {
				this.skipBlockComment();
			} else {
				this._currentChar = '/';
				return makeToken(TokenType.BinOp); // otherwise it is division
			}
		}
			
		// TODO: What happens if there are no more tokens?
		if (this.eot) {
			return this.makeToken(TokenType.EOT);
		}
		
		// TODO: Determine what the token is. For example, if it is a number
		//  keep calling takeIt() until _currentChar is not a number. Then
		//  create the token via makeToken(TokenType.IntegerLiteral) and return it.
		TokenType tokType = this.scanToken();
		return this.makeToken(tokType);
	}
	
	private TokenType scanToken() {
		if (eot) {
			return TokenType.EOT;
		}
		
		switch (this._currentChar) {
		case'-':
			this.takeIt();
			return TokenType.Negation;
		case '!':
			this.takeIt();
			
			if (this._currentChar == '=') {
				this.takeIt();
				return TokenType.BinOp;
			}
			return TokenType.UnOp;
		case '=':
			this.takeIt();
			
			if (this._currentChar == '=') { // ==
				this.takeIt();
				return TokenType.BinOp;
			}
			return TokenType.Assignment;
		case '+':	case '*':
			return TokenType.BinOp;
		case '>':	
			this.takeIt();
			if (this._currentChar == '=') { // >=
				this.takeIt();
			}
			return TokenType.BinOp;
		case '<':
			this.takeIt();
			if (this._currentChar == '=') { // <=
				this.takeIt();
			}
			return TokenType.BinOp;
		case '&':
			this.takeIt();
			if (this._currentChar != '&') {
				this._errors.reportError("Unrecognized character '&' in input");
			}
			return TokenType.BinOp;
		case '|':
			this.takeIt();
			if (this._currentChar != '|') {
				this._errors.reportError("Unrecognized character '|' in input");
			}
			return TokenType.BinOp;
		case '(':
			this.takeIt();
			return TokenType.LParen;
		case ')':
			this.takeIt();
			return TokenType.RParen;
		case '[':
			this.takeIt();
			return TokenType.LSqBrack;
		case ']':
			this.takeIt();
			return TokenType.RSqBrack;
		case '{':
			this.takeIt();
			return TokenType.LCurly;
		case '}':
			this.takeIt();
			return TokenType.RCurly;
		case '.':
			this.takeIt();
			return TokenType.Dot;
		case ';':
			this.takeIt();
			return TokenType.Semicolon;
		case '0': case '1': case '2': case '3': case '4':
		case '5': case '6': case '7': case '8': case '9': // IntLiteral
			while (!this.eot && this.isDigit()) {
				this.takeIt();
			}
			return TokenType.IntLiteral;
		default:
			if (this.isValidIdentifierChar()) {
				while (this.isValidIdentifierChar()) {
					this.takeIt();
					
					TokenType toktype = this.currentTokType();
					
					if (toktype != TokenType.Identifier) {
						return toktype;
					}
				}
				
				return TokenType.Identifier;
			} else {
				this._errors.reportError("Unrecognized character '" + this._currentChar + "' in input");
				return TokenType.Error;
			}
		}
	}
	
	private boolean isDigit() {
		return this._currentChar >= '0' && this._currentChar <= '9';
	}
	
	private boolean isAlpha() {
		return (this._currentChar >= 'A' && this._currentChar <= 'Z') || (this._currentChar >= 'a' && this._currentChar <= 'z');
	}
	
	private boolean isValidIdentifierChar() {
		return (this._currentChar == '_' || this._currentChar == '$' || this.isDigit() || this.isAlpha());
	}

	private void skipInLineComment() {
		while (this._currentChar != 10) { // 10 = \n
			this.skipIt();									
		}
	}
	
	private void skipBlockComment() {
		while (!this.eot) {
			if (this._currentChar == '*') {
				this.nextChar();
				if (this._currentChar == '/') {
					return;
				}
			}
			this.skipIt();
		}
	}
	
	private TokenType currentTokType() {
		switch (this._currentText.toString()) {
			case "class":
				return TokenType.Class;
			case "while":
				return TokenType.While;
			case "return":
				return TokenType.Return;
			case "if":
				return TokenType.If;
			case "else":
				return TokenType.Else;
			case "public":
			case "private":
				return TokenType.Visibility;
			case "static":
				return TokenType.Access;
			case "void":
				return TokenType.Void;
			case "null":
				return TokenType.Null;
			case "int":
			case "boolean":
				return TokenType.Type;
			case "this":
				return TokenType.Reference;
			case "true":
			case "false":
				return TokenType.BoolLiteral;
			case "new":
				return TokenType.New;
			default:
				return TokenType.Identifier;
		}	
	}
	
	private void takeIt() {
		_currentText.append(_currentChar);
		nextChar();
	}
	
	private void skipIt() {
		nextChar();
	}
	
	private void nextChar() {
		try {
			int c = _in.read();
			_currentChar = (char)c;
			
			// What happens if c == -1?
			if (c == -1) {
				this.eot = true;
			}
			
			// What happens if c is not a regular ASCII character?
			if (c > 127) {
				throw new IOException("The character is not a regular ASCII character.");
			}
			
		} catch( IOException e ) {
			// Report an error here
			this._errors.reportError(e.getMessage());
		}
	}
	
	private Token makeToken( TokenType toktype ) {
		// return a new Token with the appropriate type and text
		//  contained in 
		Token token = new Token(toktype, this._currentText.toString());
		this._currentText.setLength(0); // reset current text
		return token;
	}
}
