# GameTrust voice chat setup

## Architecture

```text
React/Next.js -- REST + JWT --> Spring Boot -- signed 5-minute token --> React
React/Next.js ---------------- WebRTC audio ------------------------> LiveKit SFU
LiveKit ---------------- signed participant webhooks -------------> Spring Boot --> MongoDB
```

Spring Boot never receives audio. It validates the logged-in user and room rules, then signs a short-lived LiveKit token. The token only grants room join, subscribe and microphone publish. Camera, screen share and data publishing are disabled.

MongoDB stores room configuration and verified session history. LiveKit remains the realtime source of truth for participants, speaking state, track mute state and connection quality.

## 1. Install LiveKit Server

On Windows, download `livekit-server.exe` from the official LiveKit releases page and add its folder to `PATH`.

For a quick audio-only test without webhook synchronization:

```powershell
livekit-server --dev
```

Development credentials are `devkey` / `secret`, and the browser connects to `ws://localhost:7880`.

## 2. Enable verified presence webhooks

Copy `livekit.local.yaml.example` to an ignored local file, for example `livekit.local.yaml`, then run:

```powershell
livekit-server --dev --config .\livekit.local.yaml
```

The webhook URL is `POST http://127.0.0.1:5000/api/livekit/webhook`. Spring validates the LiveKit signature before updating `voice_room_members` and `voice_session_audit`. Browser-reported join events are not trusted for sidebar presence.

Moderator mute and kick actions are also sent to LiveKit's server API before MongoDB is updated. A moderator may force-mute a microphone, but may not remotely unmute it; the participant must choose to unmute their own device.

## 3. Configure and run Spring Boot

The local defaults already match LiveKit development mode:

```powershell
$env:LIVEKIT_SERVER_URL='ws://localhost:7880'
$env:LIVEKIT_API_KEY='devkey'
$env:LIVEKIT_API_SECRET='secret'
$env:LIVEKIT_TOKEN_TTL_SECONDS='300'
mvn.cmd spring-boot:run
```

Do not expose `LIVEKIT_API_SECRET` through a `NEXT_PUBLIC_*` variable or commit a production secret.

The authenticated token endpoint is:

```http
POST /api/community/rooms/{roomId}/voice-token
Authorization: Bearer <GameTrust access token>
```

It rejects unknown/non-voice rooms, locked rooms without owner/moderator permission, and full rooms. The participant identity always comes from the GameTrust JWT, never from request JSON.

## 4. Run the frontend

```powershell
cd ..\EXE201-FE
npm.cmd install
npm.cmd run dev
```

Open `http://localhost:3000/voice-lounges`, log in, and select a backend room. The page requests microphone permission and connects directly to LiveKit. It supports:

- microphone mute/unmute using the LiveKit local track;
- deafen by muting remote audio rendering;
- microphone device selection;
- participant, active-speaker and connection-quality state from LiveKit;
- reconnect/disconnect state and explicit error display;
- text chat through the existing Spring REST API.

## 5. Two-user local test

Use two different accounts in separate browser profiles or an incognito window. Do not reuse the same account in the same LiveKit room because participant identity is unique and the newer connection replaces the older one.

1. Log in as `demo` in browser A.
2. Log in as a second account in browser B.
3. Join the same room from both browsers.
4. Confirm both participants appear.
5. Speak from A and confirm B hears audio and sees the speaking indicator.
6. Toggle mute, deafen and microphone device selection.
7. Close A without pressing leave and confirm the signed webhook removes A from MongoDB/sidebar.

Use headphones during this test to avoid acoustic echo.

## 6. Production checklist

- Use `wss://rtc.your-domain` and HTTPS for the web app; microphone capture is only available in secure contexts or localhost.
- Replace the development key and secret with strong deployment secrets.
- Terminate TLS with a trusted certificate.
- Open the LiveKit signaling/media ports required by the chosen deployment configuration.
- Enable TURN/TLS or use LiveKit Cloud so restrictive school/VPN networks have a relay fallback.
- Point the LiveKit webhook to the public HTTPS backend URL.
- Test UDP, TCP and TURN fallback across two different networks before release.

Official references:

- https://docs.livekit.io/transport/self-hosting/local/
- https://docs.livekit.io/home/server/generating-tokens/
- https://docs.livekit.io/intro/basics/rooms-participants-tracks/webhooks-events/
- https://docs.livekit.io/transport/self-hosting/ports-firewall/
