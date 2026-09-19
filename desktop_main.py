# main.py
import time
import threading
import os
from core import (
    call_simulator,
    speech_io,
    dialogue_manager,
    condition_detector,
    alert_manager,
    power_monitor,
    reporter,
    learner
)

# Thread-safe lock for speech
speak_lock = threading.Lock()

# Ensure folders exist
os.makedirs("data/messages", exist_ok=True)
os.makedirs("resources", exist_ok=True)

# Fallback responses file (auto-create if missing)
RESPONSES_FILE = "resources/responses.json"
if not os.path.exists(RESPONSES_FILE):
    import json
    fallback_responses = {
        "unknown": [
            "Hello! I’ll take a message for you.",
            "He’s not available right now, would you like to leave a message?",
            "I can record your message if you’d like."
        ],
        "away": [
            "Sir is away at the moment. Can I take a message?",
            "He stepped out for a bit. Would you like to leave a note?",
            "He’ll be back soon, I can note your message."
        ],
        "sleeping": [
            "Sir is sleeping right now. Would you like to leave a message?",
            "He’s resting at the moment. Can I take a message?",
            "He’s asleep, but I’ll inform him when he wakes up."
        ],
        "busy": [
            "Sir is busy at the moment. Can I take your message?",
            "He’s occupied right now. I’ll make sure he gets your message.",
            "He’s in the middle of something. Want to leave a note?"
        ]
    }
    with open(RESPONSES_FILE, "w", encoding="utf-8") as f:
        json.dump(fallback_responses, f, indent=4)

# -------------------------------------------------
# Startup sequence
# -------------------------------------------------
def startup_message():
    with speak_lock:
        speech_io.speak("Neo is now active and monitoring calls silently.")
    print("🚀 Neo started successfully! Waiting for calls...")

# -------------------------------------------------
# Call Handler Thread
# -------------------------------------------------
def call_handler():
    active_calls = {}  # track calls in progress

    while True:
        try:
            if call_simulator.check_incoming_call():
                caller = call_simulator.get_current_caller()
                caller_id = caller.get('phone')  # use phone as unique ID
                print(f"📞 Incoming call detected from {caller['name']} ({caller_id})")

                if caller_id in active_calls:
                    continue  # skip repeated handling

                active_calls[caller_id] = True
                condition = condition_detector.detect()
                print(f"🧠 Detected condition: {condition}")

                if condition in ["sleeping", "away", "unknown", "busy"]:
                    alert_manager.record_call(caller)
                    dialogue_manager.handle_incoming_call(caller, condition)
                else:
                    print("🟢 User seems active, Neo will stay silent.")

                # Remove from active_calls after 5 seconds
                threading.Timer(5, lambda: active_calls.pop(caller_id, None)).start()

        except Exception as e:
            print("Call handler error:", e)

        time.sleep(2)

# -------------------------------------------------
# Power & Alert Monitor Thread
# -------------------------------------------------
def system_monitor():
    while True:
        try:
            power_monitor.check_battery()
        except Exception as e:
            print("Power monitor error:", e)
        time.sleep(60)

# -------------------------------------------------
# Sir Interaction Thread
# -------------------------------------------------
def sir_interaction_listener():
    last_command = ""
    silence_count = 0

    while True:
        try:
            command = speech_io.listen(duration=5).lower().strip()
        except Exception as e:
            print("Listening error:", e)
            continue

        if not command:
            silence_count += 1
            if silence_count > 3:
                time.sleep(5)
                silence_count = 0
            continue

        silence_count = 0
        if command == last_command:
            continue
        last_command = command

        try:
            if "give me report" in command or "report" in command:
                print("📋 Sir requested report.")
                reporter.give_report()
                continue

            if "you have to say" in command:
                phrase = command.split("you have to say", 1)[1].strip()
                with speak_lock:
                    speech_io.speak("Okay Sir, I’ll remember that.")
                learner.learn_response("sir", "instruction", phrase)
                print(f"💾 Learned new phrase from Sir: {phrase}")
                continue

            if any(greet in command for greet in ["hello", "hi", "hey neo"]):
                with speak_lock:
                    speech_io.speak("Hello Sir, how may I assist you?")
                continue

            # fallback
            with speak_lock:
                speech_io.speak("I didn’t understand that, Sir. I’ll try to learn it next time.")
            time.sleep(3)

        except Exception as e:
            print("Sir interaction error:", e)

# -------------------------------------------------
# Main run
# -------------------------------------------------
def main():
    os.makedirs("data", exist_ok=True)
    call_simulator.simulate_incoming_calls()

    threading.Thread(target=call_handler, daemon=True).start()
    threading.Thread(target=system_monitor, daemon=True).start()
    threading.Thread(target=sir_interaction_listener, daemon=True).start()

    startup_message()

    while True:
        time.sleep(5)

if __name__ == "__main__":
    main()

