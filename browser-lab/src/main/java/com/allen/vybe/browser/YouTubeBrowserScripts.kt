package com.allen.vybe.browser

/** Original browser-only experiment, not Brave's scriptlet catalogue.
 * No media URLs, credentials or response bodies leave the page.
 */
object YouTubeBrowserScripts {
    // Capture the first media play synchronously, rather than waiting for the Android poll.
    // The short window also handles YouTube restoring its own muted preference later.
    const val STARTUP_AUDIO = """(()=>{
      const starts=new WeakMap();
      const unmute=m=>{if(m.muted||m.volume===0){m.defaultMuted=false;m.removeAttribute('muted');m.muted=false;m.volume=1}};
      document.addEventListener('play',e=>{const m=e.target;if(!(m instanceof HTMLMediaElement))return;if(!starts.has(m))starts.set(m,performance.now()+15000);if(performance.now()<starts.get(m)){unmute(m);queueMicrotask(()=>unmute(m))}},true);
      document.addEventListener('volumechange',e=>{const m=e.target;if(m instanceof HTMLMediaElement&&starts.has(m)&&performance.now()<starts.get(m))unmute(m)},true);
    })()"""
    const val RESPONSE_FILTER = """(()=>{
      if(window.__vybeResponseFilter)return;window.__vybeResponseFilter=true;
      window.__vybeAdsRemoved=0;
      const parse=JSON.parse;
      const clean=o=>{
        if(!o||typeof o!=='object')return o;
        if(o.videoDetails&&o.playabilityStatus){
          for(const k of ['adPlacements','playerAds','adSlots']){
            if(Object.prototype.hasOwnProperty.call(o,k)){delete o[k];window.__vybeAdsRemoved++}
          }
        }
        return o;
      };
      JSON.parse=function(...args){return clean(Reflect.apply(parse,this,args))};
      let initial=clean(window.ytInitialPlayerResponse);
      try{Object.defineProperty(window,'ytInitialPlayerResponse',{configurable:true,get:()=>initial,set:v=>{initial=clean(v)}})}catch(e){}
      const fetch=window.fetch;
      if(fetch)window.fetch=async function(...args){
        const r=await Reflect.apply(fetch,this,args);
        if(/^https:\/\/(m\.|www\.)?youtube\.com\/youtubei\/v1\/player(?:\?|$)/.test(r.url)){
          const json=r.json.bind(r),text=r.text.bind(r);
          r.json=async()=>clean(await json());
          r.text=async()=>{const t=await text();try{return JSON.stringify(clean(parse(t)))}catch(e){return t}};
        }
        return r;
      };
    })()"""
}
