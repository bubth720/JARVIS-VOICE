# Optional CPU component comparison. Requires pocket-tts 3.3.0, Torch CPU, onnxruntime 1.27.0 and tokenizers.
# Uses only the public French model, fixed public text and synthetic latent tensors; no reference recording.
import argparse
import json
import importlib.metadata
from pathlib import Path
import numpy as np
import torch
import onnxruntime as ort
from pocket_tts import TTSModel
from pocket_tts.modules.stateful_module import init_states
from tokenizers import Tokenizer

parser = argparse.ArgumentParser()
parser.add_argument("--model-dir", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
assert importlib.metadata.version("pocket-tts") == "3.3.0"
p = args.model_dir
b = json.loads((p / "bundle.json").read_text())
model = TTSModel.load_model(language="french")
torch.set_num_threads(2)
opts = ort.SessionOptions()
opts.intra_op_num_threads = 2
opts.inter_op_num_threads = 1
opts.log_severity_level = 3
sessions = {
    n: ort.InferenceSession(
        str(p / (n + "_int8.onnx")),
        sess_options=opts,
        providers=["CPUExecutionProvider"],
    )
    for n in ["text_conditioner", "flow_lm_main", "flow_lm_flow", "mimi_decoder"]
}
tok = Tokenizer.from_file(str(p / "tokenizer.json"))
ids = np.array([tok.encode("Bonjour, je suis JARVIS.").ids], dtype=np.int64)


def initial(specs):
    return {
        s["input_name"]: np.full(
            s["shape"],
            int(s["fill"] == "ones"),
            dtype={"float32": np.float32, "int64": np.int64, "bool": np.bool_}[
                s["dtype"]
            ],
        )
        for s in specs
    }


def difference(a, bb):
    a = np.asarray(a)
    bb = np.asarray(bb)
    return {
        "maxAbs": float(np.max(np.abs(a - bb))),
        "relativeL2": float(np.linalg.norm(a - bb) / max(np.linalg.norm(a), 1e-12)),
    }


results = {}
with torch.no_grad():
    emb = model.flow_lm.conditioner(torch.from_numpy(ids)).numpy()
    onnxemb = sessions["text_conditioner"].run(None, {"token_ids": ids})[0]
    results["text"] = difference(emb, onnxemb)
    seq = np.full((1, 1, 32), np.nan, dtype=np.float32)
    ortmain = sessions["flow_lm_main"].run(
        None,
        dict(sequence=seq, text_embeddings=emb, **initial(b["flow_lm_state_manifest"])),
    )
    ts = torch.where(
        torch.isnan(torch.from_numpy(seq)), model.flow_lm.bos_emb, torch.from_numpy(seq)
    )
    state = init_states(model.flow_lm, batch_size=1, sequence_length=1024)
    cond = model.flow_lm.backbone(
        model.flow_lm.input_linear(ts), torch.from_numpy(emb), ts, state
    )[:, -1]
    results["main_conditioning"] = difference(cond.numpy(), ortmain[0])
    results["main_eos"] = difference(model.flow_lm.out_eos(cond).numpy(), ortmain[1])
    rng = np.random.default_rng(42)
    x = rng.standard_normal((1, 32)).astype(np.float32)
    s = np.zeros((1, 1), np.float32)
    t = np.ones((1, 1), np.float32)
    pyflow = model.flow_lm.flow_net(
        cond, torch.from_numpy(s), torch.from_numpy(t), torch.from_numpy(x)
    ).numpy()
    ortflow = sessions["flow_lm_flow"].run(
        None, {"c": cond.numpy(), "s": s, "t": t, "x": x}
    )[0]
    results["flow"] = difference(pyflow, ortflow)
    latent = rng.standard_normal((1, 2, 32)).astype(np.float32)
    pyst = init_states(model.mimi, batch_size=1, sequence_length=9216)
    pywav = model.mimi.decode_from_latent(
        torch.from_numpy(latent) * model.flow_lm.emb_std + model.flow_lm.emb_mean, pyst
    ).numpy()
    ortwav = sessions["mimi_decoder"].run(
        None, dict(latent=latent, **initial(b["mimi_state_manifest"]))
    )[0]
    results["decoder"] = difference(pywav, ortwav)
results["officialVoiceCloningAvailable"] = model.has_voice_cloning
print(json.dumps(results, indent=2))
args.output.write_text(json.dumps(results, indent=2) + "\n")
assert results["text"]["maxAbs"] == 0
assert all(
    v["relativeL2"] < 0.2
    for k, v in results.items()
    if isinstance(v, dict) and k != "main_eos"
)
