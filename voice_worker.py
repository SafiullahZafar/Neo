"""Run only inside the dedicated Chatterbox environment; receives paths via stdin."""
import json
import sys


def main():
    import torch
    import soundfile as sf
    from chatterbox.tts import ChatterboxTTS
    payload = json.load(sys.stdin)
    device = payload.get("device", "cpu")
    if device not in ("cpu", "cuda"):
        raise ValueError("NEO_VOICE_DEVICE must be cpu or cuda")
    model = ChatterboxTTS.from_pretrained(device=device)
    audio = model.generate(payload["text"], audio_prompt_path=payload["reference"])
    sf.write(payload["output"], audio.squeeze(0).detach().cpu().numpy(), model.sr, subtype="PCM_16")


if __name__ == "__main__":
    main()
