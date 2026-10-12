package dev.curvegen.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {
    @Test
    void argumentsAreCopiedAndCannotBeChanged() {
        List<Object> args = new ArrayList<>(List.of(3, "x"));
        Message text = new Message("curvegen.error.example", args);
        args.clear();
        assertEquals(List.of(3, "x"), text.args());
        assertThrows(UnsupportedOperationException.class, () -> text.args().clear());
        Object[] values = {5};
        Message fromArray = Message.of("curvegen.error.example", values);
        values[0] = 9;
        assertEquals(List.of(5), fromArray.args());
    }

    @Test
    void parserErrorsKeepTheRangeAndItsCauseSeparate() {
        Expr.ParseException error = assertThrows(Expr.ParseException.class,
                () -> Expr.constant("q", "curvegen.range.x_from"));
        assertEquals(Message.of("curvegen.error.range_parse", Message.of("curvegen.range.x_from"),
                Message.of("curvegen.error.unknown_name2", "q")), error.text());
        assertEquals(error.text().key(), error.getMessage());
    }

    @Test
    void singularAndPluralFunctionErrorsHaveCompleteTemplates() {
        Expr.ParseException one = assertThrows(Expr.ParseException.class, () -> Expr.parseEquation("sin(1,2)"));
        Expr.ParseException many = assertThrows(Expr.ParseException.class, () -> Expr.parseEquation("mod(1)"));
        assertEquals(Message.of("curvegen.error.function_arity_one", "sin", 1, 2), one.text());
        assertEquals(Message.of("curvegen.error.function_arity_many", "mod", 2, 1), many.text());
    }
}
