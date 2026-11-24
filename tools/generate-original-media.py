"""Create original Muse test compositions and geometric covers; no external assets."""
from pathlib import Path
import math, wave, struct, zlib
out=Path(__file__).resolve().parents[1]/"app/src/main/assets"
out.mkdir(parents=True,exist_ok=True)
palettes=[((53,178,140),(9,31,39)),((181,122,233),(34,19,55)),((239,148,104),(53,26,34))]
for n,(name,notes) in enumerate([("dawn",[261.63,329.63,392,493.88]),("orbit",[220,261.63,329.63,392]),("tide",[196,246.94,293.66,369.99])]):
    rate=22050; seconds=32
    with wave.open(str(out/(name+".wav")),"wb") as wav:
        wav.setnchannels(1); wav.setsampwidth(2); wav.setframerate(rate)
        data=bytearray()
        for i in range(rate*seconds):
            t=i/rate; beat=t%0.5; note=notes[int(t*2)%len(notes)]
            pluck=math.exp(-beat*8)*math.sin(2*math.pi*note*t)
            pad=sum(math.sin(2*math.pi*f*t) for f in notes)/len(notes)
            fade=min(1,t/2,(seconds-t)/3)
            v=(0.13*pluck+0.07*pad)*max(0,fade)
            data.extend(struct.pack("<h",int(v*32767)))
        wav.writeframes(data)
    size=512; light,dark=palettes[n]; raw=bytearray()
    for y in range(size):
        raw.append(0)
        for x in range(size):
            dx=(x-size*.55)/size; dy=(y-size*.42)/size
            radius=math.sqrt(dx*dx+dy*dy)
            ring=(math.sin(radius*65-n*1.5)+1)/2
            mix=max(0,min(1,0.2+0.65*(1-y/size)+0.18*ring))
            if radius<0.24 and radius>0.12: mix=min(1,mix+0.25)
            raw.extend(int(dark[c]+(light[c]-dark[c])*mix) for c in range(3))
    def chunk(kind,data): return struct.pack(">I",len(data))+kind+data+struct.pack(">I",zlib.crc32(kind+data)&0xffffffff)
    png=b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",size,size,8,2,0,0,0))+chunk(b"IDAT",zlib.compress(raw))+chunk(b"IEND",b"")
    (out/(name+".png")).write_bytes(png)
print("Generated 3 original 32-second PCM compositions and 3 covers.")
