package com.kafkasl.phonewhisper

import org.junit.Assert.*
import org.junit.Test

class TranscriberClientTest {

    @Test fun `parses success response`() {
        val r = TranscriberClient.parseResponse("""{"text": "Hello world"}""")
        assertEquals("Hello world", r.text)
        assertNull(r.error)
    }

    @Test fun `parses error response`() {
        val r = TranscriberClient.parseResponse("""{"error":{"message":"Invalid key","type":"auth"}}""")
        assertNull(r.text)
        assertEquals("Invalid key", r.error)
    }

    @Test fun `handles unknown format`() {
        val r = TranscriberClient.parseResponse("""{"foo":"bar"}""")
        assertNull(r.text)
        assertNotNull(r.error)
    }

    @Test fun `handles malformed json`() {
        val r = TranscriberClient.parseResponse("not json")
        assertNull(r.text)
        assertNotNull(r.error)
    }

    @Test fun `language codes per model`() {
        assertEquals("yi", TranscriberClient.languageCode("elevenlabs/scribe-v2", "yi"))
        assertEquals("he", TranscriberClient.languageCode("openai/gpt-transcribe", "yi"))
        assertEquals("he", TranscriberClient.languageCode("microsoft/mai-transcribe-2", "he"))
        assertNull(TranscriberClient.languageCode("microsoft/mai-transcribe-2", "yi"))
        assertNull(TranscriberClient.languageCode("elevenlabs/scribe-v2", "auto"))
        assertTrue(TranscriberClient.languagesFor("mistralai/voxtral-mini-transcribe-3-0").isEmpty())
    }
}
