package com.jarvis.voice.bridgepilot.ipc;
interface IVoicePilotFixture {
    byte[] hello(in byte[] payload, IBinder clientLiveness);
    byte[] request(in byte[] payload);
    byte[] status(in byte[] key);
    byte[] cancel(in byte[] key);
}
