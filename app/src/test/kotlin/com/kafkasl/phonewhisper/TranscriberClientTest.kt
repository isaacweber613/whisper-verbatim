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
        assertNull(TranscriberClient.languageCode("mistralai/voxtral-mini-transcribe-3-0", "he"))
        assertEquals("fr", TranscriberClient.languageCode("mistralai/voxtral-mini-transcribe-3-0", "fr"))
        assertEquals("nb", TranscriberClient.languageCode("microsoft/mai-transcribe-2", "no"))
        assertEquals("fil", TranscriberClient.languageCode("elevenlabs/scribe-v2", "tl"))
        assertNull(TranscriberClient.languageCode("someone/custom-model", "fr"))
        assertTrue(TranscriberClient.languagesFor("openai/gpt-transcribe").size > 50)
    }

    @Test fun `tries another model only when that could help`() {
        for (code in listOf(404, 408, 429, 500, 503)) assertTrue(TranscriberClient.shouldTryAnotherModel(code))
        for (code in listOf(null, 400, 401, 402, 403, 413)) assertTrue(!TranscriberClient.shouldTryAnotherModel(code))
        // OpenRouter's answer for a model it doesn't have.
        assertTrue(TranscriberClient.shouldTryAnotherModel(400, "Model nope/nope does not exist"))
        assertTrue(!TranscriberClient.shouldTryAnotherModel(400, "Invalid audio format"))
    }

    @Test fun `falls back to GPT Transcribe from MAI, and to MAI from anything else`() {
        assertEquals("openai/gpt-transcribe", TranscriberClient.fallbackFor("microsoft/mai-transcribe-2"))
        assertEquals("microsoft/mai-transcribe-2", TranscriberClient.fallbackFor("openai/gpt-transcribe"))
        assertEquals("microsoft/mai-transcribe-2", TranscriberClient.fallbackFor("deepgram/nova-3"))
    }
}
