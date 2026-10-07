package com.gametrust.backend.service;

import com.gametrust.backend.config.LiveKitProperties;
import com.gametrust.backend.exception.BadRequestException;
import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels.ParticipantInfo;
import livekit.LivekitModels.Room;
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
                properties.getHttpApiUrl(),
                properties.getApiKey(),
                properties.getApiSecret()
        ));
    }

    LiveKitRoomAdminService(RoomServiceClient roomServiceClient) {
        this.roomServiceClient = roomServiceClient;
    }

    public void ensureRoom(String roomName, int capacity) {
        try {
            Response<Room> response = roomServiceClient.createRoom(roomName, null, capacity).execute();
            requireBody(response, "LiveKit could not create or validate this room");
        } catch (IOException ex) {
            throw new BadRequestException("LiveKit is unavailable; voice room could not be joined");
        }
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
                    .removeParticipant(roomName, participantIdentity, System.currentTimeMillis())
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

}
