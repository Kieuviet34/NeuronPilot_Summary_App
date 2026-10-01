package com.mediatek.neuropilot.jnidemo.aibox.ai

class WhisperServerSTTEngine(
    private val client: WhisperServerClient = WhisperServerClient()
) : STTEngine {
    override suspend fun transcribe(pcm16: ShortArray, sampleRate: Int): String {
        val wavBytes = WavWriter.toWavBytes(pcm16, sampleRate)
        return client.transcribeWav(wavBytes)
    }
}
