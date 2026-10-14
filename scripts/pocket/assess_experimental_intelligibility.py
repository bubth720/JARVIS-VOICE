"""Offline ASR proxy for authorized public synthetic audio, never personal audio.

ASR WER is an observation, not a perceptual/speaker validation or relaxed numeric gate.
The public ASR model is a separate evaluation tool, not part of the FP32 TTS pack/APK.
"""
import argparse
import json
import re
import unicodedata
from pathlib import Path
from faster_whisper import WhisperModel
from scipy.io import wavfile
from scipy.signal import resample_poly
import numpy as np
import math


def words(text):
    text=''.join(c for c in unicodedata.normalize('NFD',text.lower()) if unicodedata.category(c)!='Mn')
    return re.findall(r'[a-z0-9]+',text)


def errors(reference,hypothesis):
    row=list(range(len(hypothesis)+1))
    for i,a in enumerate(reference,1):
        current=[i]
        for j,b in enumerate(hypothesis,1):current.append(min(current[-1]+1,row[j]+1,row[j-1]+int(a!=b)))
        row=current
    return row[-1]


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--evaluation',type=Path,required=True)
    parser.add_argument('--asr-model',type=Path,required=True)
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args()
    evaluation=json.loads(args.evaluation.read_text())
    model=WhisperModel(str(args.asr_model.resolve()),device='cpu',compute_type='int8',cpu_threads=1,num_workers=1,local_files_only=True)
    provenance=json.loads((args.asr_model/'provenance.json').read_text())
    report={'tool':provenance['repository'],'automaticProxyOnly':True,'humanIntelligibilityValidated':False,'speakerSimilarityValidated':False,'cases':[],
            'asrProvenance':provenance}
    for case in evaluation['cases']:
        rate, pcm = wavfile.read(args.evaluation.parent/case['audioFile'])
        if pcm.dtype.kind == 'i': pcm = pcm.astype(np.float32) / max(abs(np.iinfo(pcm.dtype).min), np.iinfo(pcm.dtype).max)
        divisor = math.gcd(rate, 16000)
        pcm = resample_poly(pcm, 16000 // divisor, rate // divisor).astype(np.float32)
        segments,_=model.transcribe(pcm,language='fr',beam_size=5,vad_filter=False,condition_on_previous_text=False)
        transcript=' '.join(s.text.strip() for s in segments)
        expected,actual=words(case['phrase']),words(transcript)
        count=errors(expected,actual)
        report['cases'].append({'reference':case['reference'],'phrase':case['phrase'],'seed':case['seed'],'transcript':transcript,'wordErrors':count,'referenceWords':len(expected),'wer':count/max(len(expected),1)})
        args.report.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
        print(f"ASR {case['reference']} seed={case['seed']} WER={count/max(len(expected),1):.3f}",flush=True)
    report['aggregateWer']=sum(c['wordErrors'] for c in report['cases'])/sum(c['referenceWords'] for c in report['cases'])
    args.report.write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n')
    print('Automatic observations complete; human/perceptive validation remains pending.')


if __name__=='__main__':main()
