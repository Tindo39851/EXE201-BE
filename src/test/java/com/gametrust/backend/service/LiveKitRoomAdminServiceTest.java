package com.gametrust.backend.service;

import com.gametrust.backend.exception.BadRequestException;
import io.livekit.server.RoomServiceClient;
import livekit.LivekitModels.ParticipantInfo;
import livekit.LivekitModels.TrackInfo;
import livekit.LivekitModels.TrackSource;
import livekit.LivekitModels.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class LiveKitRoomAdminServiceTest {

    @Mock
    private RoomServiceClient client;
    @Mock
    private Call<ParticipantInfo> participantCall;
    @Mock
    private Call<TrackInfo> muteCall;
    @Mock
    private Call<Void> removeCall;
    @Mock
    private Call<Room> roomCall;

    private LiveKitRoomAdminService service;

    @BeforeEach
    void setUp() {
        service = new LiveKitRoomAdminService(client);
    }

    @Test
    void mutesOnlyThePublishedMicrophoneTrack() throws IOException {
        TrackInfo microphone = TrackInfo.newBuilder()
                .setSid("track-mic")
                .setSource(TrackSource.MICROPHONE)
                .build();
        ParticipantInfo participant = ParticipantInfo.newBuilder()
                .setIdentity("user-1")
                .addTracks(microphone)
                .addTracks(TrackInfo.newBuilder().setSid("track-screen").setSource(TrackSource.SCREEN_SHARE))
                .build();
        when(client.getParticipant("room-1", "user-1")).thenReturn(participantCall);
        when(participantCall.execute()).thenReturn(Response.success(participant));
        when(client.mutePublishedTrack("room-1", "user-1", "track-mic", true)).thenReturn(muteCall);
        when(muteCall.execute()).thenReturn(Response.success(microphone));

        service.muteMicrophone("room-1", "user-1");

        verify(client).mutePublishedTrack("room-1", "user-1", "track-mic", true);
    }

    @Test
    void rejectsMuteWhenNoMicrophoneTrackExists() throws IOException {
        ParticipantInfo participant = ParticipantInfo.newBuilder()
                .setIdentity("user-1")
                .addTracks(TrackInfo.newBuilder().setSid("track-screen").setSource(TrackSource.SCREEN_SHARE))
                .build();
        when(client.getParticipant("room-1", "user-1")).thenReturn(participantCall);
        when(participantCall.execute()).thenReturn(Response.success(participant));

        assertThrows(BadRequestException.class, () -> service.muteMicrophone("room-1", "user-1"));
        verify(client, never()).mutePublishedTrack("room-1", "user-1", "track-screen", true);
    }

    @Test
    void leavesDatabaseStateUntouchedWhenLiveKitIsUnavailable() throws IOException {
        when(client.getParticipant("room-1", "user-1")).thenReturn(participantCall);
        when(participantCall.execute()).thenThrow(new IOException("offline"));

        assertThrows(BadRequestException.class, () -> service.muteMicrophone("room-1", "user-1"));
    }

    @Test
    void removesParticipantFromLiveKit() throws IOException {
        when(client.removeParticipant(eq("room-1"), eq("user-1"), anyLong())).thenReturn(removeCall);
        when(removeCall.execute()).thenReturn(Response.success(null));

        service.removeParticipant("room-1", "user-1");

        verify(client).removeParticipant(eq("room-1"), eq("user-1"), anyLong());
    }

    @Test
    void reportsRemoveFailureWhenLiveKitIsUnavailable() throws IOException {
        when(client.removeParticipant(eq("room-1"), eq("user-1"), anyLong())).thenReturn(removeCall);
        when(removeCall.execute()).thenThrow(new IOException("offline"));

        assertThrows(BadRequestException.class, () -> service.removeParticipant("room-1", "user-1"));
    }

    @Test
    void createsRoomWithLiveKitEnforcedCapacity() throws IOException {
        Room room = Room.newBuilder().setName("room-1").setMaxParticipants(5).build();
        when(client.createRoom("room-1", null, 5)).thenReturn(roomCall);
        when(roomCall.execute()).thenReturn(Response.success(room));

        service.ensureRoom("room-1", 5);

        verify(client).createRoom("room-1", null, 5);
    }
}
