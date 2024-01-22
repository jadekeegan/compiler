package miniJava.SyntacticAnalyzer;

public class Token {
	private TokenType _type;
	private String _text;
	
	public Token(TokenType type, String text) {
		// Store the token's type and text
		this._type = type;
		this._text = text;
	}
	
	public TokenType getTokenType() {
		// Return the token type
		return _type;
	}
	
	public String getTokenText() {
		// Return the token text
		return _text;
	}
}
