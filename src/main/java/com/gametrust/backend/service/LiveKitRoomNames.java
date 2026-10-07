package com.gametrust.backend.service;

import org.bson.Document;

final class LiveKitRoomNames {

    private LiveKitRoomNames() {
    }

    static String resolve(Document room) {
        String name = room.getString("livekitRoomName");
        if (name != null && !name.isBlank()) return name;
        String roomId = room.getString("id");
        return "voice_" + roomId.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
