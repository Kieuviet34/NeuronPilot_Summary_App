package com.mediatek.neuropilot.jnidemo.aibox.ai

interface STTEngine {
    suspend fun transcribe(pcm16: ShortArray, sampleRate: Int = 16000): String
}
