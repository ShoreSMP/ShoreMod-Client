"""Validate the actual exported H.264 stream and its playback timing."""
import json,subprocess
from pathlib import Path
for fps in (24,60):
 p=Path('fabric/build/mp4-test')/f'clip-{fps}.mp4'
 probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-select_streams','v:0','-show_streams','-of','json',str(p)]))['streams'][0]
 assert probe['codec_name']=='h264',probe
 assert probe['pix_fmt']=='yuv420p',probe
 assert probe['width']==64 and probe['height']==48,probe
 assert probe['r_frame_rate']==f'{fps}/1',probe
 assert int(probe['nb_frames'])==fps,probe
 assert abs(float(probe['duration'])-1)<0.02,probe
 subprocess.run(['ffmpeg','-v','error','-i',str(p),'-f','null','-'],check=True)
 print(f'PASS decoded H.264 MP4: {fps} FPS, {fps} frames, one second')
