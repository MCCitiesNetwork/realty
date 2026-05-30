package io.github.md5sha256.realty.tax;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TaxFormulaTest {

    private static double eval(String formula, double plots) {
        return TaxFormula.compile(formula).evaluate(plots);
    }

    @Nested
    @DisplayName("arithmetic & precedence")
    class Arithmetic {
        @Test
        void constants() {
            Assertions.assertEquals(25.0, eval("25", 7));
            Assertions.assertEquals(2.5, eval("2.5", 0));
        }

        @Test
        void plotsVariableAndAlias() {
            Assertions.assertEquals(5.0, eval("<plots>", 5));
            Assertions.assertEquals(5.0, eval("plots", 5));
            Assertions.assertEquals(60.0, eval("12 * <plots>", 5));
        }

        @Test
        void multiplicationBindsTighterThanAddition() {
            Assertions.assertEquals(14.0, eval("2 + 3 * 4", 0));
        }

        @Test
        void powerBindsTighterThanMultiplication() {
            // 2 * plots^2 - 4 * plots, plots=5 -> 2*25 - 20 = 30
            Assertions.assertEquals(30.0, eval("2 * <plots>^2 - 4 * <plots>", 5));
        }

        @Test
        void powerIsRightAssociative() {
            // 2^3^2 = 2^(3^2) = 2^9 = 512
            Assertions.assertEquals(512.0, eval("2^3^2", 0));
        }

        @Test
        void parenthesesOverridePrecedence() {
            Assertions.assertEquals(20.0, eval("(2 + 3) * 4", 0));
        }

        @Test
        void unaryMinusAndDivision() {
            Assertions.assertEquals(-3.0, eval("-6 / 2", 0));
            Assertions.assertEquals(7.0, eval("10 - -(-3)", 0));
        }

        @Test
        void whitespaceIsIgnored() {
            Assertions.assertEquals(32.5, eval("  2.5 *<plots>^ 2  -6* <plots> ", 5));
        }

        @Test
        void constantBaseRaisedToTheVariable() {
            // The Act's exponential term: 1.16^<plots> = Math.pow(1.16, plots).
            Assertions.assertEquals(Math.pow(1.16, 8), eval("1.16^<plots>", 8), 1e-9);
            // ^ binds tighter than *: 0.25 * 1.16^plots = 0.25 * pow(1.16, plots).
            Assertions.assertEquals(0.25 * Math.pow(1.16, 8), eval("0.25 * 1.16^<plots>", 8), 1e-9);
        }

        @Test
        void actDefaultFormula() {
            // 0.25*1.16^x + 0.3*x^2 + 2.5*x - 25 — evaluated as a whole.
            String f = "0.25 * 1.16^<plots> + 0.3 * <plots>^2 + 2.5 * <plots> - 25";
            for (int x = 8; x <= 50; x++) {
                double expected = 0.25 * Math.pow(1.16, x) + 0.3 * x * x + 2.5 * x - 25;
                Assertions.assertEquals(expected, eval(f, x), 1e-9);
            }
        }
    }

    @Nested
    @DisplayName("errors")
    class Errors {
        @Test
        void rejectsBlankAndNull() {
            Assertions.assertThrows(TaxFormulaException.class, () -> TaxFormula.compile(""));
            Assertions.assertThrows(TaxFormulaException.class, () -> TaxFormula.compile("   "));
            Assertions.assertThrows(TaxFormulaException.class, () -> TaxFormula.compile(null));
        }

        @Test
        void rejectsUnknownIdentifier() {
            Assertions.assertThrows(TaxFormulaException.class, () -> TaxFormula.compile("3 * acres"));
        }

        @Test
        void rejectsUnbalancedParensAndTrailingInput() {
            Assertions.assertThrows(TaxFormulaException.class, () -> TaxFormula.compile("2 * (3 + 4"));
            Assertions.assertThrows(TaxFormulaException.class, () -> TaxFormula.compile("2 3"));
            Assertions.assertThrows(TaxFormulaException.class, () -> TaxFormula.compile("2 +"));
        }
    }
}
