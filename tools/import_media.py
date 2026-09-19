from pathlib import Path
import subprocess,json,imageio_ffmpeg
f=imageio_ffmpeg.get_ffmpeg_exe();root=Path('src/main/resources/assets/sasuke_epicfight');out=root/'sounds';out.mkdir(exist_ok=True)
m={'1a':'attack_1','2a':'attack_2','3a':'attack_3','4a1':'attack_41','4a2':'attack_42','雷球爆炸':'lightning_burst','普通收刀':'basic_sheathe','旋转斩收刀':'spin_sheathe','组合技命中':'combo_hit'}
for i in range(1,4):
 m[f'4a3{i}']=f'attack_43_{i}';m[f'黑炎1{i}']=f'flame_1_{i}';m[f'黑炎2{i}']=f'flame_2_{i}'
for i in range(1,3):m[f'须佐{i}']=f'susanoo_{i}';m[f'旋转斩{i}']=f'spin_{i}'
for src,dst in m.items():subprocess.run([f,'-v','error','-y','-i',str(Path('../audios')/(src+'.mp3')),'-ac','1','-af','volume=2,alimiter=limit=0.85:level=false','-c:a','libvorbis','-q:a','5',str(out/(dst+'.ogg'))],check=True)
v=Path('../videos/组合技命中播放视频.mp4');frames=root/'textures/cg';frames.mkdir(parents=True,exist_ok=True)
subprocess.run([f,'-v','error','-y','-i',str(v),'-vf','scale=1280:-2','-start_number','0',str(frames/'combo_%03d.png')],check=True)
sounds={n:{'sounds':[{'name':'sasuke_epicfight:'+n,'stream':True}]} for n in m.values()};(root/'sounds.json').write_text(json.dumps(sounds,indent=2))
print('sounds',len(sounds),'frames',len(list(frames.glob('*.png'))))
