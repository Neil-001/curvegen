package dev.curvegen.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Desmos-style expression parser: implicit multiplication, |x|, sin x, sin^2(x), inequalities. */
public final class Expr {
    private Expr() {}

    @FunctionalInterface public interface Fn { double eval(double x, double y); }
    @FunctionalInterface public interface Fn3 { double eval(double x, double y, double z); }

    public static final class ParseException extends Exception {
        private static final long serialVersionUID = 1L;
        public ParseException(String msg) { super(msg); }
    }

    /** Parsed equation: F(x, y) = lhs - rhs, with the relation that was typed ("=", "<", ">", "<=", ">=" or null). */
    public record Equation(Fn f, String rel) {}
    /** The same for a 3D equation: F(x, y, z) = lhs - rhs. */
    public record Equation3(Fn3 f, String rel) {}

    private interface F1 { double ap(double a); }
    private record Func(int arity, F1 one, java.util.function.DoubleBinaryOperator two, boolean variadic) {}

    private static final Map<String, Func> FUNCS = new HashMap<>();
    private static final Map<String, Double> CONSTS = Map.of("pi", Math.PI, "tau", 2 * Math.PI, "e", Math.E);
    private static void f1(String n, F1 f) { FUNCS.put(n, new Func(1, f, null, false)); }
    static {
        f1("sin", Math::sin); f1("cos", Math::cos); f1("tan", Math::tan);
        f1("asin", Math::asin); f1("acos", Math::acos); f1("atan", Math::atan);
        f1("sinh", Math::sinh); f1("cosh", Math::cosh); f1("tanh", Math::tanh);
        f1("asinh", v -> Math.log(v + Math.sqrt(v * v + 1)));
        f1("acosh", v -> Math.log(v + Math.sqrt(v * v - 1)));
        f1("atanh", v -> 0.5 * Math.log((1 + v) / (1 - v)));
        f1("sec", v -> 1 / Math.cos(v)); f1("csc", v -> 1 / Math.sin(v)); f1("cot", v -> 1 / Math.tan(v));
        f1("sqrt", Math::sqrt); f1("cbrt", Math::cbrt); f1("abs", Math::abs); f1("exp", Math::exp);
        f1("ln", Math::log); f1("log", Math::log10); f1("floor", Math::floor); f1("ceil", Math::ceil);
        f1("round", v -> Math.floor(v + 0.5)); f1("sign", Math::signum);
        FUNCS.put("min", new Func(-1, null, Math::min, true));
        FUNCS.put("max", new Func(-1, null, Math::max, true));
        FUNCS.put("mod", new Func(2, null, (a, b) -> ((a % b) + b) % b, false));
    }
    /** The words an equation may use. Only a 3D equation knows z. */
    private static final List<String> NAMES = new ArrayList<>(), NAMES3 = new ArrayList<>();
    static {
        NAMES.addAll(FUNCS.keySet()); NAMES.addAll(CONSTS.keySet()); NAMES.add("x"); NAMES.add("y");
        NAMES.sort(Comparator.comparingInt(String::length).reversed());
        NAMES3.addAll(NAMES); NAMES3.add("z");
        NAMES3.sort(Comparator.comparingInt(String::length).reversed());
    }

    static double pow(double b, double e) {
        if (b >= 0 || e == Math.rint(e)) return Math.pow(b, e);
        for (int q = 3; q <= 15; q += 2) {           // real odd roots of negatives: x^(1/3), x^(2/3)
            double pq = e * q; long p = Math.round(pq);
            if (Math.abs(pq - p) < 1e-9) return ((p & 1) != 0 ? -1 : 1) * Math.pow(-b, e);
        }
        return Double.NaN;
    }

    // ---------- tokens ----------
    private record Tok(char kind, double num, String s) {} // kind: 'n' number, 'i' identifier, 'o' operator

    private static final Pattern NUM = Pattern.compile("^(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    private static List<Tok> tokenize(String src, boolean z) throws ParseException {
        src = src.replaceAll("[−–]", "-").replaceAll("[×·⋅]", "*").replace('÷', '/')
                .replace("≤", "<=").replace("≥", ">=").replace("π", "pi").replace("τ", "tau")
                .replace("²", "^2").replace("³", "^3").replace('[', '(').replace(']', ')');
        List<Tok> out = new ArrayList<>();
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (Character.isDigit(c) || c == '.') {
                Matcher m = NUM.matcher(src.substring(i));
                if (!m.find()) throw new ParseException("\"" + c + "\" isn't a valid number.");
                out.add(new Tok('n', Double.parseDouble(m.group()), null));
                i += m.group().length();
                continue;
            }
            if (Character.isLetter(c)) {
                int j = i;
                while (j < src.length() && Character.isLetter(src.charAt(j))) j++;
                String word = src.substring(i, j).toLowerCase(Locale.ROOT);
                int k = 0;
                while (k < word.length()) {
                    String found = null;
                    for (String n : z ? NAMES3 : NAMES) if (word.startsWith(n, k)) { found = n; break; }
                    if (found == null) throw new ParseException("\"" + word.substring(k) + "\" isn't something I know. Use x, y, " + (z ? "z, " : "") + "pi, e or a function name.");
                    out.add(new Tok('i', 0, found));
                    k += found.length();
                }
                i = j;
                continue;
            }
            if (i + 1 < src.length()) {
                String two = src.substring(i, i + 2);
                if (two.equals("<=") || two.equals(">=")) { out.add(new Tok('o', 0, two)); i += 2; continue; }
            }
            if ("+-*/^(),|=<>".indexOf(c) >= 0) { out.add(new Tok('o', 0, String.valueOf(c))); i++; continue; }
            throw new ParseException("\"" + c + "\" can't be used in an equation.");
        }
        return out;
    }

    // ---------- AST ----------
    private sealed interface Node permits Num, Var, Neg, Bin, Call {}
    private record Num(double v) implements Node {}
    private record Var(char v) implements Node {}
    private record Neg(Node a) implements Node {}
    private record Bin(char op, Node a, Node b) implements Node {}
    private record Call(String f, List<Node> args) implements Node {}

    private static final class Parser {
        final List<Tok> t; int p = 0, absDepth = 0;
        Parser(List<Tok> t) { this.t = t; }
        Tok peek() { return p < t.size() ? t.get(p) : null; }
        boolean isOp(String v) { Tok k = peek(); return k != null && k.kind == 'o' && k.s.equals(v); }
        void expect(String v, String msg) throws ParseException { if (!isOp(v)) throw new ParseException(msg); p++; }
        boolean startsPrimary() {
            Tok k = peek();
            return k != null && (k.kind == 'n' || k.kind == 'i' || (k.kind == 'o' && (k.s.equals("(") || (k.s.equals("|") && absDepth == 0))));
        }
        Node expr() throws ParseException {
            Node a = term();
            while (isOp("+") || isOp("-")) { char o = t.get(p++).s.charAt(0); a = new Bin(o, a, term()); }
            return a;
        }
        Node term() throws ParseException {
            Node a = unary();
            for (;;) {
                if (isOp("*") || isOp("/")) { char o = t.get(p++).s.charAt(0); a = new Bin(o, a, unary()); }
                else if (startsPrimary()) a = new Bin('*', a, power());
                else break;
            }
            return a;
        }
        Node unary() throws ParseException {
            if (isOp("-")) { p++; return new Neg(unary()); }
            if (isOp("+")) { p++; return unary(); }
            return power();
        }
        Node power() throws ParseException {
            Node base = primary();
            if (isOp("^")) { p++; return new Bin('^', base, unary()); }
            return base;
        }
        Node primary() throws ParseException {
            Tok k = peek();
            if (k == null) throw new ParseException("The equation ends too early.");
            p++;
            if (k.kind == 'n') return new Num(k.num);
            if (k.kind == 'o' && k.s.equals("(")) { Node e = expr(); expect(")", "A bracket is missing its closing )."); return e; }
            if (k.kind == 'o' && k.s.equals("|")) {
                absDepth++; Node e = expr(); expect("|", "An absolute value is missing its closing |."); absDepth--;
                return new Call("abs", List.of(e));
            }
            if (k.kind == 'i') {
                if (k.s.equals("x") || k.s.equals("y") || k.s.equals("z")) return new Var(k.s.charAt(0));
                if (CONSTS.containsKey(k.s)) return new Num(CONSTS.get(k.s));
                Node expo = null;
                if (isOp("^")) { p++; expo = unary(); }
                List<Node> args = new ArrayList<>();
                if (isOp("(")) {
                    p++; args.add(expr());
                    while (isOp(",")) { p++; args.add(expr()); }
                    expect(")", k.s + "( is missing its closing ).");
                } else {
                    if (!startsPrimary()) throw new ParseException(k.s + " needs something to act on, like " + k.s + "(x).");
                    Node a = power();
                    while (startsPrimary() && !(peek().kind == 'i' && FUNCS.containsKey(peek().s))) a = new Bin('*', a, power());
                    args.add(a);
                }
                Func f = FUNCS.get(k.s);
                if (!f.variadic && args.size() != f.arity)
                    throw new ParseException(k.s + " takes " + f.arity + " input" + (f.arity > 1 ? "s" : "") + ", not " + args.size() + ".");
                Node n = new Call(k.s, args);
                return expo != null ? new Bin('^', n, expo) : n;
            }
            throw new ParseException("\"" + k.s + "\" is in an unexpected place.");
        }
    }

    private static boolean uses(Node n, char v) {
        return switch (n) {
            case Var w -> w.v == v;
            case Neg g -> uses(g.a, v);
            case Bin b -> uses(b.a, v) || uses(b.b, v);
            case Call c -> c.args.stream().anyMatch(a -> uses(a, v));
            case Num ignored -> false;
        };
    }

    private static Fn3 compile(Node n) {
        if (!(n instanceof Num) && !(n instanceof Var) && !uses(n, 'x') && !uses(n, 'y') && !uses(n, 'z')) {
            double v = raw(n).eval(0, 0, 0);
            return (x, y, z) -> v;
        }
        return raw(n);
    }

    private static Fn3 raw(Node n) {
        switch (n) {
            case Num k -> { double v = k.v; return (x, y, z) -> v; }
            case Var w -> { return w.v == 'x' ? (x, y, z) -> x : w.v == 'y' ? (x, y, z) -> y : (x, y, z) -> z; }
            case Neg g -> { Fn3 a = compile(g.a); return (x, y, z) -> -a.eval(x, y, z); }
            case Bin b -> {
                Fn3 a = compile(b.a), c = compile(b.b);
                return switch (b.op) {
                    case '+' -> (x, y, z) -> a.eval(x, y, z) + c.eval(x, y, z);
                    case '-' -> (x, y, z) -> a.eval(x, y, z) - c.eval(x, y, z);
                    case '*' -> (x, y, z) -> a.eval(x, y, z) * c.eval(x, y, z);
                    case '/' -> (x, y, z) -> a.eval(x, y, z) / c.eval(x, y, z);
                    default -> (x, y, z) -> pow(a.eval(x, y, z), c.eval(x, y, z));
                };
            }
            case Call c -> {
                Func f = FUNCS.get(c.f);
                Fn3[] as = c.args.stream().map(Expr::compile).toArray(Fn3[]::new);
                if (as.length == 1 && f.one != null) { Fn3 a = as[0]; F1 g = f.one; return (x, y, z) -> g.ap(a.eval(x, y, z)); }
                if (as.length == 1) { return as[0]; }                      // min(v) / max(v)
                var op = f.two;
                return (x, y, z) -> {
                    double acc = as[0].eval(x, y, z);
                    for (int i = 1; i < as.length; i++) acc = op.applyAsDouble(acc, as[i].eval(x, y, z));
                    return acc;
                };
            }
        }
    }

    /** The two sides of an equation and the relation between them, which is null for a bare expression. */
    private record Sides(Node lhs, Node rhs, String rel) {}

    private static Sides sides(String src, boolean z) throws ParseException {
        Parser ps = new Parser(tokenize(src, z));
        Node lhs = ps.expr();
        String rel = null; Node rhs = null;
        Tok k = ps.peek();
        if (k != null && k.kind == 'o' && Arrays.asList("=", "<", ">", "<=", ">=").contains(k.s)) {
            rel = k.s; ps.p++; rhs = ps.expr();
        }
        if (ps.p < ps.t.size()) throw new ParseException("\"" + tokStr(ps.t.get(ps.p)) + "\" is in an unexpected place.");
        return new Sides(lhs, rhs, rel);
    }

    /** Parses "y = f(x)", "x = g(y)", "F(x,y) = G(x,y)", an inequality, or a bare f(x) meaning y = f(x). */
    public static Equation parseEquation(String src) throws ParseException {
        if (src.isBlank()) throw new ParseException("Type an equation, for example y = sin(x).");
        Sides e = sides(src, false);
        if (e.rel == null) {
            if (uses(e.lhs, 'y')) throw new ParseException("Add an = sign, for example x^2 + y^2 = 9.");
            Fn3 f = compile(e.lhs);
            return new Equation((x, y) -> y - f.eval(x, y, 0), null);
        }
        Fn3 l = compile(e.lhs), r = compile(e.rhs);
        return new Equation((x, y) -> l.eval(x, y, 0) - r.eval(x, y, 0), e.rel);
    }

    /** Parses a 3D equation: anything in x, y and z with a relation, or a bare f(x, y) meaning z = f(x, y). */
    public static Equation3 parseEquation3(String src) throws ParseException {
        if (src.isBlank()) throw new ParseException("Type an equation, for example z = sin(x) cos(y).");
        Sides e = sides(src, true);
        if (e.rel == null) {
            if (uses(e.lhs, 'z')) throw new ParseException("Add an = sign, for example x^2 + y^2 + z^2 = 9.");
            Fn3 f = compile(e.lhs);
            return new Equation3((x, y, z) -> z - f.eval(x, y, z), null);
        }
        Fn3 l = compile(e.lhs), r = compile(e.rhs);
        return new Equation3((x, y, z) -> l.eval(x, y, z) - r.eval(x, y, z), e.rel);
    }

    /** Evaluates a constant expression such as "2pi" or "-3.5". */
    public static double constant(String src, String label) throws ParseException {
        Node n;
        try {
            Parser ps = new Parser(tokenize(src, false));
            n = ps.expr();
            if (ps.p < ps.t.size()) throw new ParseException("\"" + tokStr(ps.t.get(ps.p)) + "\" is in an unexpected place.");
        } catch (ParseException e) {
            throw new ParseException(label + ": " + e.getMessage());
        }
        if (uses(n, 'x') || uses(n, 'y')) throw new ParseException(label + " must be a number, like 5 or 2pi.");
        double v = compile(n).eval(0, 0, 0);
        if (!Double.isFinite(v)) throw new ParseException(label + " must be a finite number.");
        return v;
    }

    private static String tokStr(Tok t) { return t.kind == 'n' ? String.valueOf(t.num) : t.s; }
}
