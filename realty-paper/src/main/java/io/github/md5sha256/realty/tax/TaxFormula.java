package io.github.md5sha256.realty.tax;

/**
 * A compiled property-tax formula over a single variable, {@code <plots>}.
 *
 * <p>Supports {@code + - * /}, exponentiation {@code ^} (right-associative,
 * binds tighter than multiplication), unary minus, parentheses, decimal
 * literals, and the variable token {@code <plots>} (also accepted bare as
 * {@code plots}). Example: {@code "0.25 * 1.16^<plots> + 0.3 * <plots>^2 + 2.5 * <plots> - 25"}.
 *
 * <p>Compile once with {@link #compile(String)} (throws {@link TaxFormulaException}
 * on malformed input), then evaluate cheaply many times with {@link #evaluate(double)}.
 */
public final class TaxFormula {

    @FunctionalInterface
    private interface Node {
        double eval(double plots);
    }

    private final Node root;
    private final String source;

    private TaxFormula(Node root, String source) {
        this.root = root;
        this.source = source;
    }

    /** Compiles a formula string, or throws {@link TaxFormulaException}. */
    public static TaxFormula compile(String input) {
        if (input == null) {
            throw new TaxFormulaException("Formula is null");
        }
        return new Parser(input).parseFull();
    }

    /** Evaluates the formula for the given {@code <plots>} value. */
    public double evaluate(double plots) {
        return root.eval(plots);
    }

    /** The original formula text. */
    public String source() {
        return source;
    }

    @Override
    public String toString() {
        return "TaxFormula[" + source + "]";
    }

    /** Recursive-descent parser. Grammar (lowest → highest precedence):
     *  expression = term (('+' | '-') term)*
     *  term       = unary (('*' | '/') unary)*
     *  unary      = ('-' | '+') unary | power
     *  power      = primary ('^' unary)?
     *  primary    = number | '<plots>' | 'plots' | '(' expression ')'
     */
    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        TaxFormula parseFull() {
            Node node = parseExpression();
            skipWs();
            if (pos < s.length()) {
                throw err("Unexpected trailing input");
            }
            return new TaxFormula(node, s.trim());
        }

        private Node parseExpression() {
            Node left = parseTerm();
            while (true) {
                skipWs();
                char c = peek();
                if (c == '+') {
                    pos++;
                    Node l = left;
                    Node r = parseTerm();
                    left = p -> l.eval(p) + r.eval(p);
                } else if (c == '-') {
                    pos++;
                    Node l = left;
                    Node r = parseTerm();
                    left = p -> l.eval(p) - r.eval(p);
                } else {
                    return left;
                }
            }
        }

        private Node parseTerm() {
            Node left = parseUnary();
            while (true) {
                skipWs();
                char c = peek();
                if (c == '*') {
                    pos++;
                    Node l = left;
                    Node r = parseUnary();
                    left = p -> l.eval(p) * r.eval(p);
                } else if (c == '/') {
                    pos++;
                    Node l = left;
                    Node r = parseUnary();
                    left = p -> l.eval(p) / r.eval(p);
                } else {
                    return left;
                }
            }
        }

        private Node parseUnary() {
            skipWs();
            char c = peek();
            if (c == '-') {
                pos++;
                Node operand = parseUnary();
                return p -> -operand.eval(p);
            }
            if (c == '+') {
                pos++;
                return parseUnary();
            }
            return parsePower();
        }

        private Node parsePower() {
            Node base = parsePrimary();
            skipWs();
            if (peek() == '^') {
                pos++;
                Node exponent = parseUnary(); // right-associative
                return p -> Math.pow(base.eval(p), exponent.eval(p));
            }
            return base;
        }

        private Node parsePrimary() {
            skipWs();
            char c = peek();
            if (c == '(') {
                pos++;
                Node node = parseExpression();
                skipWs();
                if (peek() != ')') {
                    throw err("Expected ')'");
                }
                pos++;
                return node;
            }
            if (c == '<') {
                if (s.startsWith("<plots>", pos)) {
                    pos += "<plots>".length();
                    return p -> p;
                }
                throw err("Unknown variable (expected <plots>)");
            }
            if (Character.isLetter(c)) {
                int start = pos;
                while (pos < s.length() && Character.isLetter(s.charAt(pos))) {
                    pos++;
                }
                String id = s.substring(start, pos);
                if (id.equals("plots")) {
                    return p -> p;
                }
                throw err("Unknown identifier '" + id + "'");
            }
            if (Character.isDigit(c) || c == '.') {
                int start = pos;
                boolean dot = false;
                while (pos < s.length()) {
                    char d = s.charAt(pos);
                    if (Character.isDigit(d)) {
                        pos++;
                    } else if (d == '.' && !dot) {
                        dot = true;
                        pos++;
                    } else {
                        break;
                    }
                }
                double value;
                try {
                    value = Double.parseDouble(s.substring(start, pos));
                } catch (NumberFormatException e) {
                    throw err("Invalid number");
                }
                return p -> value;
            }
            throw err(pos >= s.length() ? "Unexpected end of formula" : "Unexpected character '" + c + "'");
        }

        private char peek() {
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        private TaxFormulaException err(String message) {
            return new TaxFormulaException(message + " (at index " + pos + " in \"" + s + "\")");
        }
    }
}
