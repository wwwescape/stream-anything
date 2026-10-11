package com.wwwescape.streamanything.data.streams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsStringExpressionTest {

    @Test
    fun concatenatesLiterals() {
        assertEquals("abcdef", JsStringExpression.evaluate("'abc' + \"def\""))
    }

    @Test
    fun appliesChainedSubstrings() {
        assertEquals(
            "//streamtape.com/get_video?id=1",
            JsStringExpression.evaluate("'//streamta' + ('xcdpe.com/get_video?id=1').substring(1).substring(2)"),
        )
    }

    @Test
    fun handlesEmptyLiteralsAndMixedQuotes() {
        assertEquals(
            "/streamtapde.com/x",
            JsStringExpression.evaluate("\"/streamtap\" + ''+ ('xcdde.com/x').substring(1).substring(2)"),
        )
    }

    @Test
    fun substringFollowsJavaScriptSemantics() {
        assertEquals("bc", JsStringExpression.evaluate("'abcd'.substring(3, 1)"))
        assertEquals("", JsStringExpression.evaluate("'abcd'.substring(10)"))
        assertEquals("ab", JsStringExpression.evaluate("'abcd'.substring(-5, 2)"))
    }

    @Test
    fun rejectsAnythingOutsideTheGrammar() {
        assertNull(JsStringExpression.evaluate("atob('aGk=')"))
        assertNull(JsStringExpression.evaluate("'abc'.slice(1)"))
        assertNull(JsStringExpression.evaluate("'unterminated"))
        assertNull(JsStringExpression.evaluate("'a' + "))
    }
}
