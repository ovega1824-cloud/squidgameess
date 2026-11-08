package com.squidgame;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Servidor: recibe la imagen del ganador del jugador que ejecutó el comando y la reparte a todos. */
public class ImageRelay {
    private static final int MAX_BYTES = 4 * 1024 * 1024;

    private static final Map<UUID, Boolean> PENDING = new HashMap<>();
    private static final Map<UUID, UUID> ACTIVE = new HashMap<>(); // tid -> sender
    private static final Map<UUID, Integer> LEFT = new HashMap<>();

    public static void expect(ServerPlayer p) {
        PENDING.put(p.getUUID(), Boolean.TRUE);
    }

    public static void onBegin(ServerPlayer sender, FinalPackets.ImgBegin m) {
        if (PENDING.remove(sender.getUUID()) == null) return; // nadie la pidió
        if (m.total <= 0 || m.total > MAX_BYTES || m.chunks <= 0 || m.chunks > 400) return;
        ACTIVE.put(m.tid, sender.getUUID());
        LEFT.put(m.tid, m.chunks);
        FinalPackets.ImgBegin out = new FinalPackets.ImgBegin(m.tid, m.ext, m.total, m.chunks,
                ServerConfig.winnerSeconds());
        Net.CHANNEL.send(PacketDistributor.ALL.noArg(), out);
    }

    public static void onChunk(ServerPlayer sender, FinalPackets.ImgChunk m) {
        UUID owner = ACTIVE.get(m.tid);
        if (owner == null || !owner.equals(sender.getUUID())) return;
        Net.CHANNEL.send(PacketDistributor.ALL.noArg(), m);
        int left = LEFT.merge(m.tid, -1, Integer::sum);
        if (left <= 0) {
            ACTIVE.remove(m.tid);
            LEFT.remove(m.tid);
        }
    }
}
