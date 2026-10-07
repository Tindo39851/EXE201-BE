package com.gametrust.backend.service;

import com.gametrust.backend.config.LiveKitProperties;
import com.gametrust.backend.exception.BadRequestException;
import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels.ParticipantInfo;
import livekit.LivekitModels.TrackInfo;
import livekit.LivekitModels.TrackSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;

@Service
public class LiveKitRoomAdminService {

    private final RoomServiceClient roomServiceClient;

    @Autowired
    public LiveKitRoomAdminService(LiveKitProperties properties) {
        this(RoomServiceClient.createClient(
                toHttpUrl(properties.getServerUrl()),
                properties.getApiKey(),
                properties.getApiSecret()
        ));
    }

    LiveKitRoomAdminService(RoomServiceClient roomServiceClient) {
        this.roomServiceClient = roomServiceClient;
    }

    public void muteMicrophone(String roomName, String participantIdentity) {
        try {
            Response<ParticipantInfo> participantResponse = roomServiceClient
                    .getParticipant(roomName, participantIdentity)
                    .execute();
            ParticipantInfo participant = requireBody(participantResponse, "Voice participant not found in LiveKit");
            TrackInfo microphone = participant.getTracksList().stream()
                    .filter(track -> track.getSource() == TrackSource.MICROPHONE)
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Participant has no published microphone track"));

            Response<TrackInfo> muteResponse = roomServiceClient
                    .mutePublishedTrack(roomName, participantIdentity, microphone.getSid(), true)
                    .execute();
            requireBody(muteResponse, "LiveKit could not mute this participant");
        } catch (IOException ex) {
            throw new BadRequestException("LiveKit is unavailable; participant was not muted");
        }
    }

    public void removeParticipant(String roomName, String participantIdentity) {
        try {
            Response<Void> response = roomServiceClient
                    .removeParticipant(roomName, participantIdentity)
                    .execute();
            if (!response.isSuccessful()) {
                throw new BadRequestException("LiveKit could not remove this participant");
            }
        } catch (IOException ex) {
            throw new BadRequestException("LiveKit is unavailable; participant was not removed");
        }
    }

    private static <T> T requireBody(Response<T> response, String message) {
        if (!response.isSuccessful() || response.body() == null) {
            throw new BadRequestException(message);
        }
        return response.body();
    }

    private static String toHttpUrl(String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            throw new IllegalArgumentException("livekit.server-url is required");
        }
        if (serverUrl.startsWith("wss://")) return "https://" + serverUrl.substring(6);
        if (serverUrl.startsWith("ws://")) return "http://" + serverUrl.substring(5);
        return serverUrl;
    }
}
