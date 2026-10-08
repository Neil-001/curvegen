package dev.curvegen.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The Desmos-style equation syntax. A bare expression means y = expression, so F(x, y) = y - f(x). */
class ParserTest {
    private static double f(String src, double x) throws Expr.ParseException {
        return -Expr.parseEquation(src).f().eval(x, 0);
    }

    @Test
    void desmosStyleSyntax() throws Exception {
        assertEquals(2 * Math.sin(1), f("2sin(x)", 1), 1e-12);            // implicit multiplication
        assertEquals(Math.sin(4), f("sin x^2", 2), 1e-12);                // function without brackets
        assertEquals(6, f("2|x|", -3), 1e-12);                            // |x| and implicit multiplication
        assertEquals(0.5, f("||x|-1|", -0.5), 1e-12);                     // nested absolute values
        assertEquals(-2, f("x^(1/3)", -8), 1e-12);                        // real odd roots of negatives
        assertEquals(4, f("x^(2/3)", -8), 1e-9);
        assertEquals(1, f("sin^2(x)+cos^2 x", 0.7), 1e-12);
        assertEquals(2, f("1/2x", 4), 1e-12);                             // (1/2)x, as in Desmos
        assertEquals(3, f("max(x,3,1)", 2), 1e-12);
        assertEquals(2, f("mod(-1,3)", 0), 1e-12);
        assertEquals(512, f("2^3^2", 0), 1e-12);                          // right-associative powers
        assertEquals(-9, f("-x^2", 3), 1e-12);
        assertEquals(12, f("3x²", 2), 1e-12);
        assertEquals(2 * Math.PI, f("2π", 0), 1e-12);
    }

    @Test
    void relations() throws Exception {
        assertNull(Expr.parseEquation("sin(x)").rel(), "a bare expression means y = …");
        assertEquals("=", Expr.parseEquation("x^2 + y^2 = 16").rel());
        assertEquals("<", Expr.parseEquation("y < 4 - x^2").rel());
        assertEquals(">=", Expr.parseEquation("y ≥ x").rel());
        // x^2 + y^2 = 16  ->  F = x^2 + y^2 - 16
        assertEquals(-16, Expr.parseEquation("x^2 + y^2 = 16").f().eval(0, 0), 1e-12);
    }

    @Test
    void helpfulErrors() {
        assertThrows(Expr.ParseException.class, () -> Expr.parseEquation("sin(x"));
        assertThrows(Expr.ParseException.class, () -> Expr.parseEquation("y+"));
        assertThrows(Expr.ParseException.class, () -> Expr.parseEquation("q"));
        assertThrows(Expr.ParseException.class, () -> Expr.parseEquation("x**2"));
        assertThrows(Expr.ParseException.class, () -> Expr.parseEquation("x^2 + y^2"));   // needs an = sign
        assertThrows(Expr.ParseException.class, () -> Expr.parseEquation(""));
        try {
            Expr.parseEquation("sin(x");
            fail("expected an error");
        } catch (Expr.ParseException e) {
            assertEquals("sin( is missing its closing ).", e.getMessage());
        }
    }

    @Test
    void threeDEquationsKnowZ() throws Exception {
        Expr.Equation3 e = Expr.parseEquation3("x^2 + y^2 + z^2 = 16");
        assertEquals("=", e.rel());
        assertEquals(1 + 4 + 9 - 16, e.f().eval(1, 2, 3), 1e-12);
        // A bare expression means z = expression, and may use x and y.
        e = Expr.parseEquation3("2sin(x) + y");
        assertNull(e.rel());
        assertEquals(5 - (2 * Math.sin(1) + 3), e.f().eval(1, 3, 5), 1e-12);
        assertEquals("<", Expr.parseEquation3("z < xy").rel());
        assertEquals(">=", Expr.parseEquation3("x ≥ z²").rel());
        // Letters run together still split into names, z among them.
        assertEquals(2 * 3 * 5 - 1, Expr.parseEquation3("xyz = 1").f().eval(2, 3, 5), 1e-12);
        assertEquals(Math.sin(12), -Expr.parseEquation3("0 = sin z y").f().eval(0, 3, 4), 1e-12);      // sin(zy), as sin x y is sin(xy)
        assertEquals(7, Expr.parseEquation3("z = 7").f().eval(0, 0, 14), 1e-12);
        try {
            Expr.parseEquation3("x + z");
            fail("expected an error");
        } catch (Expr.ParseException ex) {
            assertEquals("Add an = sign, for example x^2 + y^2 + z^2 = 9.", ex.getMessage());
        }
        try {
            Expr.parseEquation3("z = w");
            fail("expected an error");
        } catch (Expr.ParseException ex) {
            assertEquals("\"w\" isn't something I know. Use x, y, z, pi, e or a function name.", ex.getMessage());
        }
        assertThrows(Expr.ParseException.class, () -> Expr.parseEquation3(" "));
    }

    @Test
    void twoDEquationsStillDont() {
        try {
            Expr.parseEquation("y = z");
            fail("expected an error");
        } catch (Expr.ParseException ex) {
            assertEquals("\"z\" isn't something I know. Use x, y, pi, e or a function name.", ex.getMessage());
        }
        assertThrows(Expr.ParseException.class, () -> Expr.parseEquation("x^2 + z^2 = 4"));
        assertThrows(Expr.ParseException.class, () -> Expr.constant("z", "x to"));
    }

    @Test
    void constants() throws Exception {
        assertEquals(2 * Math.PI, Expr.constant("2pi", "x to"), 1e-12);
        assertEquals(-3.5, Expr.constant("-3.5", "y from"), 1e-12);
        assertThrows(Expr.ParseException.class, () -> Expr.constant("x", "x to"));
        assertThrows(Expr.ParseException.class, () -> Expr.constant("1/0", "x to"));
    }
}
