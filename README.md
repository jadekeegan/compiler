# COMP 520 Compiler Guide

The guide below describes how various parts of this compiler were handled. Also, please be sure to switch to the PA5 branch to see the full compiler inside the repository!

## Syntactic Analysis
I used recursive descent in my compiler. Most characters had their own token type, but I did group Binary Operators (excluding `-` as it is also a Unary Operator), Visibility (`public`, `private`), and Boolean Literals (`true`, `false`). I also had a token for empty brackets (`[]`) separate from a left square bracket and a right square bracket to make array declaration simpler.

## AST Generation
Please see [ASTChanges.txt](https://github.com/jadekeegan/compiler/blob/df008d63ea4a7d31f9c5ae6de2e34fbddf81aaf1/pa1/src/miniJava/ContextualAnalysis/ASTChanges.txt) for other AST modifications.

Aside from the PA3 changes, in PA4 I also decorated declarations in my AST with offsets and other fields to help with calculating/assigning offsets.

## Contextual Analysis
I used one traversal for Identification and Type Checking in my `ContextualAnalysis` class. I created a separate `ScopedIdentification` class to store my IDTable stack and various operations (such as adding and finding declarations) on it. I also created a separate `IDTable` class as the input into my SI stack, but it has the exact same functionality as a normal HashMap, just specific to the String key and Declaration value.

## Code Generation
The main optimization I did during was code generation was reducing the number of pushes/pops to the stack by pushing values into RAX rather than pushing them onto the stack (Literals). I only push to the stack when absolutely necessary. Thus, the only values on the stack are old BP addresses, return addresses, static variables, and local variables. The exception, however, is evaluating binary expressions. I was not able to remove pushing and popping from evaluating those due to potential register clobbering.

## Developer Concerns
- I made everything, including integers, 64-bit, but I didn't do any extra handling for math expressions.
- I did not modify `makeMalloc()`, so it allocates a full page rather than just the size of the class. This would be a quick fix though, as I do still calculate the size of my classes regardless. I would just need to move that size value into RSI.
- There are definitely cases for identification where I allowed things to just raise a Java NullPointerException on failing test cases where a declaration is not found, particularly because I don't immediately stop identification when I find an error.

# Extra Credit
1. Used a single traversal for Identification and Type-Checking. (1pt)
2. Reduced the number of pushes/pops in my compiler. (2pts?)
3. Extended ModRMSIB to use mod=00 and mod=01 properly. This is done through if statements at the bottom of my Make() methods. (1pt)
4. Implement method overloading. See the `/tests` directory for the tests. (2pts)
5. Report line and column numbers for errors. See the `/tests` directory for examples for parsing and contextual analysis error reporting. (1pt?)
