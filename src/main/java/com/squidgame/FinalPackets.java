package com.squidgame;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Paquetes de la final: estado, acciones de cargar y imagen del ganador. */
public class FinalPackets {

    /** Servidor -> clientes: vidas, nombres, jugadores tirados, modelos y quién carga a quién. */
    public static class State {
        public final boolean active;
        public final String n1, n2;
        public final int l1, l2;
        public final List<UUID> downed, models, carriers, carried;

        public State(boolean active, String n1, String n2, int l1, int l2,
                     List<UUID> downed, List<UUID> models, List<UUID> carriers, List<UUID> carried) {
            this.active = active;
            this.n1 = n1;
            this.n2 = n2;
            this.l1 = l1;
            this.l2 = l2;
            this.downed = downed;
            this.models = models;
            this.carriers = carriers;
            this.carried = carried;
        }

        private static void writeList(FriendlyByteBuf b, List<UUID> l) {
            b.writeVarInt(l.size());
            for (UUID u : l) b.writeUUID(u);
        }

        private static List<UUID> readList(FriendlyByteBuf b) {
            int n = Math.min(b.readVarInt(), 512);
            List<UUID> l = new ArrayList<>();
            for (int i = 0; i < n; i++) l.add(b.readUUID());
            return l;
        }

        public static void encode(State m, FriendlyByteBuf b) {
            b.writeBoolean(m.active);
            b.writeUtf(m.n1, 64);
            b.writeUtf(m.n2, 64);
            b.writeVarInt(m.l1);
            b.writeVarInt(m.l2);
            writeList(b, m.downed);
            writeList(b, m.models);
            writeList(b, m.carriers);
            writeList(b, m.carried);
        }

        public static State decode(FriendlyByteBuf b) {
            return new State(b.readBoolean(), b.readUtf(64), b.readUtf(64), b.readVarInt(), b.readVarInt(),
                    readList(b), readList(b), readList(b), readList(b));
        }

        public static void handle(State m, Supplier<NetworkEvent.Context> ctxs) {
            NetworkEvent.Context ctx = ctxs.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.squidgame.client.ClientFinal.onState(m)));
            ctx.setPacketHandled(true);
        }
    }

    /** Cliente -> servidor: 0 = empezar a cargar, 1 = lanzar, 2 = soltar. */
    public static class Action {
        public static final int CARRY = 0, THROW = 1, DROP = 2;
        public final int action;
        public final UUID target;

        public Action(int action, UUID target) {
            this.action = action;
            this.target = target;
        }

        public static void encode(Action m, FriendlyByteBuf b) {
            b.writeVarInt(m.action);
            b.writeUUID(m.target);
        }

        public static Action decode(FriendlyByteBuf b) {
            return new Action(b.readVarInt(), b.readUUID());
        }

        public static void handle(Action m, Supplier<NetworkEvent.Context> ctxs) {
            NetworkEvent.Context ctx = ctxs.get();
            ServerPlayer sender = ctx.getSender();
            if (sender != null) ctx.enqueueWork(() -> FinalGame.onAction(sender, m));
            ctx.setPacketHandled(true);
        }
    }

    /** Servidor -> cliente que ejecutó "ganador show": que suba la imagen. */
    public static class ImgRequest {
        public final String id;

        public ImgRequest(String id) {
            this.id = id;
        }

        public static void encode(ImgRequest m, FriendlyByteBuf b) {
            b.writeUtf(m.id, 64);
        }

        public static ImgRequest decode(FriendlyByteBuf b) {
            return new ImgRequest(b.readUtf(64));
        }

        public static void handle(ImgRequest m, Supplier<NetworkEvent.Context> ctxs) {
            NetworkEvent.Context ctx = ctxs.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.squidgame.client.ClientImage.onRequest(m.id)));
            ctx.setPacketHandled(true);
        }
    }

    /** Inicio de una imagen (cliente -> servidor -> todos los clientes). */
    public static class ImgBegin {
        public final UUID tid;
        public final String ext;
        public final int total, chunks, seconds;

        public ImgBegin(UUID tid, String ext, int total, int chunks, int seconds) {
            this.tid = tid;
            this.ext = ext.length() > 8 ? "png" : ext;
            this.total = total;
            this.chunks = chunks;
            this.seconds = seconds;
        }

        public static void encode(ImgBegin m, FriendlyByteBuf b) {
            b.writeUUID(m.tid);
            b.writeUtf(m.ext, 8);
            b.writeInt(m.total);
            b.writeInt(m.chunks);
            b.writeInt(m.seconds);
        }

        public static ImgBegin decode(FriendlyByteBuf b) {
            return new ImgBegin(b.readUUID(), b.readUtf(8), b.readInt(), b.readInt(), b.readInt());
        }

        public static void handle(ImgBegin m, Supplier<NetworkEvent.Context> ctxs) {
            NetworkEvent.Context ctx = ctxs.get();
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                ctx.enqueueWork(() -> ImageRelay.onBegin(sender, m));
            } else {
                ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> com.squidgame.client.ClientImage.onBegin(m)));
            }
            ctx.setPacketHandled(true);
        }
    }

    public static class ImgChunk {
        public final UUID tid;
        public final int index;
        public final byte[] data;

        public ImgChunk(UUID tid, int index, byte[] data) {
            this.tid = tid;
            this.index = index;
            this.data = data;
        }

        public static void encode(ImgChunk m, FriendlyByteBuf b) {
            b.writeUUID(m.tid);
            b.writeInt(m.index);
            b.writeByteArray(m.data);
        }

        public static ImgChunk decode(FriendlyByteBuf b) {
            return new ImgChunk(b.readUUID(), b.readInt(), b.readByteArray(32000));
        }

        public static void handle(ImgChunk m, Supplier<NetworkEvent.Context> ctxs) {
            NetworkEvent.Context ctx = ctxs.get();
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                ctx.enqueueWork(() -> ImageRelay.onChunk(sender, m));
            } else {
                ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> com.squidgame.client.ClientImage.onChunk(m)));
            }
            ctx.setPacketHandled(true);
        }
    }

    /** Servidor -> clientes: quitar la imagen del ganador. */
    public static class ImgHide {
        public static void encode(ImgHide m, FriendlyByteBuf b) {
        }

        public static ImgHide decode(FriendlyByteBuf b) {
            return new ImgHide();
        }

        public static void handle(ImgHide m, Supplier<NetworkEvent.Context> ctxs) {
            NetworkEvent.Context ctx = ctxs.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.squidgame.client.ClientImage.hide()));
            ctx.setPacketHandled(true);
        }
    }
}
