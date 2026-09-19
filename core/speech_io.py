# core/speech_io.py
import queue
import sounddevice as sd
import numpy as np
from vosk import Model, KaldiRecognizer
from piper import PiperVoice
import threading
import os
from core.config import settings

# ----------------------------
# Load models
# ----------------------------
VOSK_MODEL_PATH = str(settings.vosk_model_path)
PIPER_MODEL_PATH = str(settings.piper_model_path)

if not os.path.exists(VOSK_MODEL_PATH):
    raise FileNotFoundError("Vosk model not found. Download and place in models/vosk_model")

if not os.path.exists(PIPER_MODEL_PATH):
    raise FileNotFoundError("Piper TTS model not found. Download and place in models/piper_model")

# STT model
stt_model = Model(VOSK_MODEL_PATH)

# TTS model
tts_voice = PiperVoice.load(PIPER_MODEL_PATH)

# ----------------------------
# Audio Queue for STT
# ----------------------------
audio_q = queue.Queue()

def audio_callback(indata, frames, time, status):
    """Callback from sounddevice to feed Vosk recognizer"""
    if status:
        print(status)
    audio_q.put(bytes(indata))

# ----------------------------
# STT: Listen and return text
# ----------------------------
def listen(duration=5):
    """
    Listens from microphone for `duration` seconds and returns recognized text.
    """
    rec = KaldiRecognizer(stt_model, 16000)
    text = ""

    def _listen_thread():
        with sd.RawInputStream(samplerate=16000, blocksize=8000, dtype='int16',
                               channels=1, callback=audio_callback):
            nonlocal text
            rec.AcceptWaveform(b"")  # Reset
            for _ in range(int(duration * 16000 / 8000)):
                data = audio_q.get()
                if rec.AcceptWaveform(data):
                    result = rec.Result()
                    result_json = eval(result)  # dict format
                    text += result_json.get("text", "") + " "
            # Get final partial result
            final = rec.FinalResult()
            final_json = eval(final)
            text += final_json.get("text", "")

    t = threading.Thread(target=_listen_thread)
    t.start()
    t.join()

    return text.strip()

# ----------------------------
# TTS: Speak text
# ----------------------------
def speak(text):
    """
    Neo speaks the text using offline Piper TTS
    """
    if not text:
        return

    # Generate audio (AudioChunk generator)
    audio_gen = tts_voice.synthesize(text)

    # Convert to NumPy float32 array
    audio = np.concatenate([
        chunk.audio_float_array for chunk in audio_gen
    ])

    # Get sample rate directly from Piper config
    sample_rate = tts_voice.config.sample_rate

    # Play generated speech
    sd.play(audio, samplerate=sample_rate)
    sd.wait()

# ----------------------------
# Example usage
# ----------------------------
if __name__ == "__main__":
    print("Neo STT + TTS test")
    speak("Hello, I am Neo. I will help you manage your calls.")
    print("Listening for 5 seconds...")
    user_speech = listen(duration=5)
    print("You said:", user_speech)
