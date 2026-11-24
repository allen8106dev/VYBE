package com.allen.vybe

/** Only a wake phrase at the start of an utterance opens a short command window. */
class WakeWordGate {
    private val wake=Regex("^(?:hey|okay|ok) music(?:[ ,]+|$)",RegexOption.IGNORE_CASE)
    private var until=0L
    private var cooldown=0L
    private var confirmed=false
    var listening=false
        private set
    fun arm(now: Long) {listening=true;confirmed=true;until=now+12_000;cooldown=0}
    fun partial(text: String,now: Long): Boolean {
        expire(now)
        if(now<cooldown||listening||!wake.containsMatchIn(text.trim()))return false
        listening=true;until=now+12_000
        return true
    }
    fun result(text: String,now: Long): String? {
        expire(now)
        if(now<cooldown)return null
        val clean=text.trim()
        val match=wake.find(clean)
        if(match!=null){listening=true;confirmed=true;until=now+12_000}
        else if(!confirmed){listening=false;until=0;return null}
        if(!listening)return null
        val command=if(match!=null)clean.substring(match.range.last+1).trim() else clean
        if(command.isBlank())return null
        listening=false;confirmed=false;until=0;cooldown=now+1500
        return command
    }
    fun expire(now: Long) {if(listening&&now>=until){listening=false;confirmed=false;until=0}}
}
