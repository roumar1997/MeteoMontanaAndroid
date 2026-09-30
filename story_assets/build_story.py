import os

outdir = r'C:\Users\rouma\MeteoMontanaAndroid\.claude\worktrees\sweet-engelbart-4912dc\story_assets'

def b64(name):
    with open(os.path.join(outdir, name + '.b64'), 'r') as f:
        return f.read().strip()

topo = b64('topo')
mapa = b64('mapa')
mascot = b64('mascot')
logo = b64('logo')
jbmreg = b64('jbmreg')
jbmbold = b64('jbmbold')
ssbold = b64('ssbold')
sssemi = b64('sssemi')

html = f"""<style>
@font-face {{ font-family: 'Cumbre Mono'; src: url(data:font/ttf;base64,{jbmreg}) format('truetype'); font-weight: 400; }}
@font-face {{ font-family: 'Cumbre Mono'; src: url(data:font/ttf;base64,{jbmbold}) format('truetype'); font-weight: 700; }}
@font-face {{ font-family: 'Cumbre Serif'; src: url(data:font/ttf;base64,{sssemi}) format('truetype'); font-weight: 600; }}
@font-face {{ font-family: 'Cumbre Serif'; src: url(data:font/ttf;base64,{ssbold}) format('truetype'); font-weight: 700; }}
* {{ box-sizing: border-box; margin: 0; padding: 0; }}
html, body {{ margin: 0; padding: 0; background: #F2EFE8; }}
.story {{
  width: 1080px;
  height: 1920px;
  background: #F2EFE8;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}}
.brandrow {{ display: flex; align-items: center; gap: 14px; padding: 90px 64px 0; }}
.brandrow img {{ width: 46px; height: 46px; border-radius: 50%; }}
.brandrow span {{ font-family: 'Cumbre Serif', serif; font-weight: 600; font-size: 24px; color: #1E1B18; }}
.eyebrow {{
  font-family: 'Cumbre Mono', monospace;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 3px;
  text-transform: uppercase;
  color: #C1502E;
  margin: 60px 64px 10px;
}}
.headline {{
  font-family: 'Cumbre Serif', serif;
  font-weight: 700;
  font-size: 58px;
  line-height: 1.14;
  color: #1E1B18;
  margin: 0 64px 40px;
}}
.stats {{
  display: flex;
  gap: 20px;
  padding: 0 64px;
  margin-bottom: 44px;
}}
.stat {{
  flex: 1;
  background: #E9E3D6;
  border-radius: 20px;
  padding: 28px 20px;
  text-align: center;
}}
.stat .num {{
  font-family: 'Cumbre Serif', serif;
  font-weight: 700;
  font-size: 62px;
  color: #C1502E;
  line-height: 1;
}}
.stat .label {{
  font-family: 'Cumbre Mono', monospace;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 1px;
  text-transform: uppercase;
  color: #5B534C;
  margin-top: 10px;
}}
.fanstage {{ flex: 1; position: relative; margin: 0 50px; }}
.fanphone {{
  position: absolute;
  aspect-ratio: 560 / 1147;
  border-radius: 32px / 16px;
  overflow: hidden;
  border: 5px solid #1E1B18;
  background: #F2EFE8;
}}
.fanphone img {{ width: 100%; height: 100%; object-fit: cover; object-position: top center; display: block; }}
.fanmascot {{ position: absolute; z-index: 4; filter: drop-shadow(0 10px 18px rgba(0,0,0,0.2)); }}
.footer {{ display: flex; align-items: center; gap: 12px; padding: 0 64px 80px; }}
.footer img {{ width: 38px; height: 38px; border-radius: 50%; }}
.footer span {{ font-family: 'Cumbre Serif', serif; font-weight: 600; font-size: 22px; color: #1E1B18; }}
</style>
<div class="story">
  <div class="brandrow"><img src="data:image/png;base64,{logo}" alt=""><span>Cumbre</span></div>
  <p class="eyebrow">El catalogo crece</p>
  <h1 class="headline">Ya somos un mapa<br>de verdad.</h1>
  <div class="stats">
    <div class="stat"><div class="num">201</div><div class="label">Escuelas</div></div>
    <div class="stat"><div class="num">91</div><div class="label">Sectores</div></div>
    <div class="stat"><div class="num">261</div><div class="label">Vias</div></div>
  </div>
  <div class="fanstage">
    <div class="fanphone" style="left:4%; bottom:40px; z-index:1; transform:rotate(-6deg); width:46%;">
      <img src="data:image/jpeg;base64,{mapa}" alt="">
    </div>
    <div class="fanphone" style="right:2%; bottom:100px; z-index:2; transform:rotate(6deg); width:50%;">
      <img src="data:image/jpeg;base64,{topo}" alt="">
    </div>
    <img class="fanmascot" style="bottom:0; left:36%; height:280px;" src="data:image/png;base64,{mascot}" alt="">
  </div>
  <div class="footer"><img src="data:image/png;base64,{logo}" alt=""><span>Cumbre</span></div>
</div>
"""

with open(os.path.join(outdir, 'story.html'), 'w', encoding='utf-8') as f:
    f.write(html)
print('written', len(html))
