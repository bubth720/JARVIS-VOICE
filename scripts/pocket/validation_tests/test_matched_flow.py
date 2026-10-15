"""Regression for masked padding and dynamic offsets; no gated weights/network."""
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
import numpy as np
import torch
import onnxruntime as ort
from torch import nn
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from export_matched_flow import Main
from pocket_tts.models.flow_lm import FlowLMModel
from pocket_tts.modules.transformer import StreamingTransformer
from pocket_tts.modules.stateful_module import StatefulModule, init_states, increment_steps


class MatchedFlowTests(unittest.TestCase):
    def test_onnx_padded_prompt_and_continuation_match_official_attention(self):
        torch.set_num_threads(1)
        torch.manual_seed(42)
        lm = FlowLMModel(nn.Identity(), nn.Identity(), StreamingTransformer(16, 2, 2, None, 32, None), dim=16, ldim=4, dtype=torch.float32).eval()
        for name, module in lm.named_modules():
            if isinstance(module, StatefulModule): module._module_absolute_name = name
        wrapper = Main(SimpleNamespace(flow_lm=lm)).eval()
        tensors = []
        for _ in range(2): tensors.extend([torch.zeros(1, dtype=torch.int64), torch.zeros(1, dtype=torch.int64), torch.zeros(2, 1, 32, 2, 8)])
        with tempfile.TemporaryDirectory() as root, torch.no_grad():
            path = Path(root) / 'test.onnx'
            torch.onnx.export(wrapper, (torch.full((1, 1, 4), torch.nan), torch.empty(1, 0, 16), *tensors), str(path), opset_version=17, dynamo=False,
                              input_names=['sequence', 'text_embeddings'] + [f'state_{i}' for i in range(6)],
                              output_names=['conditioning', 'eos_logit'] + [f'next_state_{i}' for i in range(6)],
                              dynamic_axes={'sequence': {1: 'sequence_length'}, 'text_embeddings': {1: 'prompt_length'}})
            opts = ort.SessionOptions(); opts.intra_op_num_threads = opts.inter_op_num_threads = 1
            session = ort.InferenceSession(str(path), sess_options=opts, providers=['CPUExecutionProvider'])
            for pad in [0, 3]:
                reference = init_states(lm, 1, 32)
                values = {f'state_{i}': value.numpy().copy() for i, value in enumerate(tensors)}
                for layer in range(2):
                    reference[f'transformer.layers.{layer}.self_attn']['pad'].fill_(pad)
                    values[f'state_{layer*3+1}'].fill(pad)
                for step in range(3):
                    seq = torch.empty(1, 0, 4) if step == 0 else torch.full((1, 1, 4), torch.nan) if step == 1 else torch.randn(1, 1, 4)
                    text = torch.randn(1, 8, 16) if step == 0 else torch.empty(1, 0, 16)
                    actual = session.run(None, dict(sequence=seq.numpy(), text_embeddings=text.numpy(), **values))
                    seq = torch.where(torch.isnan(seq), lm.bos_emb, seq)
                    expected = lm.backbone(lm.input_linear(seq), text, seq, reference)[:, -1]
                    increment_steps(lm, reference, seq.shape[1] + text.shape[1])
                    self.assertTrue(np.isfinite(actual[0]).all())
                    np.testing.assert_allclose(actual[0], expected.numpy(), atol=2e-4, rtol=2e-4)
                    for layer in range(2):
                        state = reference[f'transformer.layers.{layer}.self_attn']
                        np.testing.assert_array_equal(actual[2+layer*3], state['offset'].numpy())
                        np.testing.assert_array_equal(actual[3+layer*3], state['pad'].numpy())
                        count = int(state['offset'][0])
                        np.testing.assert_allclose(actual[4+layer*3][:, :, :count], state['cache'].numpy()[:, :, :count], atol=2e-4, rtol=2e-4)
                    values = {f'state_{i}': v for i, v in enumerate(actual[2:])}


if __name__ == '__main__': unittest.main()
