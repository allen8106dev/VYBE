package com.allen.vybe.browser

/** Event-only diagnostics. No polling timer, request interception or playback changes. */
object BrowserMediaHealth {
    const val SCRIPT = """(()=>{
      if(window.__vybeHealth)return;
      const states=new WeakMap();const events=[];
      const get=m=>{let s=states.get(m);if(!s){s={waiting:0,stalled:0,pauses:0,playing:0,seeks:0,errors:0,waitMs:0,maxWaitMs:0,waitAt:0};states.set(m,s)}return s};
      const finish=s=>{if(s.waitAt){const d=performance.now()-s.waitAt;s.waitMs+=d;s.maxWaitMs=Math.max(s.maxWaitMs,d);s.waitAt=0}};
      for(const type of ['waiting','stalled','pause','playing','seeking','error'])document.addEventListener(type,e=>{
        const m=e.target;if(!(m instanceof HTMLMediaElement))return;const s=get(m);
        events.push({type,clock:Math.round(performance.now()),time:m.currentTime,ready:m.readyState,paused:m.paused,muted:m.muted,buffered:[...Array(m.buffered.length)].map((_,i)=>[m.buffered.start(i),m.buffered.end(i)])});if(events.length>40)events.shift();
        if(type==='waiting'){s.waiting++;if(!s.waitAt)s.waitAt=performance.now()}
        if(type==='stalled')s.stalled++;
        if(type==='pause'){s.pauses++;finish(s)}
        if(type==='playing'){s.playing++;finish(s)}
        if(type==='seeking')s.seeks++;
        if(type==='error')s.errors++;
      },true);
      window.__vybeHealth={read:m=>{
        const s=get(m);let ahead=0;
        try{for(let i=0;i<m.buffered.length;i++)if(m.buffered.start(i)<=m.currentTime&&m.buffered.end(i)>=m.currentTime)ahead=m.buffered.end(i)-m.currentTime}catch(e){}
        const q=m.getVideoPlaybackQuality?.();const extra=s.waitAt?performance.now()-s.waitAt:0;
        return {...s,events,waitMs:Math.round(s.waitMs+extra),maxWaitMs:Math.round(Math.max(s.maxWaitMs,extra)),bufferAhead:Number(ahead.toFixed(2)),width:m.videoWidth||0,height:m.videoHeight||0,dropped:q?.droppedVideoFrames||0,frames:q?.totalVideoFrames||0,error:m.error?.code||0,rate:m.playbackRate,clock:Math.round(performance.now())};
      }};
    })()"""
}
