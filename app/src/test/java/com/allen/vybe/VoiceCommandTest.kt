package com.allen.vybe

import org.junit.Assert.*
import org.junit.Test

class VoiceCommandTest {
    @Test fun controlsAndNavigation() {
        assertEquals(VoiceCommand.Transport("pause"),parseVoiceCommand("Please pause the music!"))
        assertEquals(VoiceCommand.Transport("play"),parseVoiceCommand("resume"))
        assertEquals(VoiceCommand.Transport("next"),parseVoiceCommand("next song"))
        assertEquals(VoiceCommand.Open("home"),parseVoiceCommand("go home"))
        assertEquals(VoiceCommand.Shuffle(false),parseVoiceCommand("turn off shuffle"))
        assertEquals(VoiceCommand.Repeat(2),parseVoiceCommand("repeat this song"))
        assertEquals(VoiceCommand.SeekBy(-15000),parseVoiceCommand("back 15 seconds"))
        assertEquals(VoiceCommand.Timer(30),parseVoiceCommand("sleep timer for 30 minutes"))
        assertEquals(VoiceCommand.Timer(30),parseVoiceCommand("sleep timer for thirty minutes"))
        assertEquals(VoiceCommand.SeekBy(15000),parseVoiceCommand("forward fifteen seconds"))
        assertEquals(VoiceCommand.DisableWake,parseVoiceCommand("stop listening"))
        assertEquals(VoiceCommand.Like(true),parseVoiceCommand("like this song"))
        assertEquals(VoiceCommand.Like(false),parseVoiceCommand("remove from liked songs"))
    }
    @Test fun songRequestsKeepTitleAndArtist() {
        assertEquals(VoiceCommand.PlaySong("Believer by Imagine Dragons"),parseVoiceCommand("Please play Believer by Imagine Dragons"))
        assertEquals(VoiceCommand.Search("Coldplay"),parseVoiceCommand("search for Coldplay"))
        assertEquals(VoiceCommand.PlaySong("Don't Stop Me Now"),parseVoiceCommand("Don't Stop Me Now"))
        assertEquals(VoiceCommand.PlaySong("Next To Me"),parseVoiceCommand("play Next To Me"))
    }
    @Test fun wakeIsRequiredAndCommandRunsOnce() {
        val gate=WakeWordGate()
        assertNull(gate.result("pause the music",1000))
        assertNull(gate.result("a song called hey music",1100))
        assertTrue(gate.partial("hey music",2000))
        assertFalse(gate.partial("hey music pause",2100))
        assertEquals("pause",gate.result("hey music pause",2300))
        assertNull(gate.result("hey music pause",2400))
        assertNull(gate.result("resume",5000))
    }
    @Test fun wakeAndCommandCanBeSeparateUtterancesButExpire() {
        val gate=WakeWordGate()
        assertNull(gate.result("hey music",1000))
        assertEquals("play believer",gate.result("play believer",4000))
        assertNull(gate.result("hey music",6000))
        assertNull(gate.result("next song",19000))
        assertFalse(gate.listening)
    }
    @Test fun correctedFalsePartialDoesNotExecuteSpeech() {
        val gate=WakeWordGate()
        gate.partial("hey music",1000)
        assertNull(gate.result("a fine morning",1500))
        assertNull(gate.result("pause",2000))
    }
}
