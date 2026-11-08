package com.squidgame.client;

import com.squidgame.FinalPackets;
import com.squidgame.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Estado de la final en el cliente (lo manda el servidor). */
public class ClientFinal {
    public static final int HOLD_TICKS = 160; // 8 segundos

    public static boolean active = false;
    public static String n1 = "", n2 = "";
    public static int l1 = 3, l2 = 3;
    public static final Set<UUID> downed = new HashSet<>();
    public static final Set<UUID> models = new HashSet<>();
    public static final Map<UUID, UUID> carried = new HashMap<>(); // tirado -> quien lo carga

    private static final Map<UUID, Float> progress = new HashMap<>();
    private static final Map<UUID, Float> progressPrev = new HashMap<>();

    public static int holdTicks = 0;
    private static UUID holdTarget = null;

    public static void onState(FinalPackets.State m) {
        active = m.active;
        n1 = m.n1;
        n2 = m.n2;
        l1 = m.l1;
        l2 = m.l2;
        downed.clear();
        downed.addAll(m.downed);
        models.clear();
        models.addAll(m.models);
        carried.clear();
        for (int i = 0; i < Math.min(m.carried.size(), m.carriers.size()); i++) {
            carried.put(m.carried.get(i), m.carriers.get(i));
        }
        if (!active) holdReset();
    }

    public static void reset() {
        active = false;
        downed.clear();
        models.clear();
        carried.clear();
        progress.clear();
        progressPrev.clear();
        holdReset();
    }

    public static boolean hasModel(UUID id) {
        return models.contains(id);
    }

    public static boolean isDowned(UUID id) {
        return downed.contains(id);
    }

    public static boolean isCarrying(UUID id) {
        return carried.containsValue(id);
    }

    /** 0 = de pie, 1 = tirado; suavizado para que la caída no sea de golpe. */
    public static float downAmount(UUID id, float partialTick) {
        float a = progressPrev.getOrDefault(id, 0.0F);
        float b = progress.getOrDefault(id, 0.0F);
        return a + (b - a) * partialTick;
    }

    private static void holdReset() {
        holdTicks = 0;
        holdTarget = null;
    }

    /** Se llama una vez por tick del cliente. */
    public static void tick(Minecraft mc) {
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null) {
            reset();
            return;
        }
        // animación de caída / levantarse
        for (Player p : mc.level.players()) {
            UUID id = p.getUUID();
            float cur = progress.getOrDefault(id, 0.0F);
            progressPrev.put(id, cur);
            float target = downed.contains(id) && models.contains(id) ? 1.0F : 0.0F;
            if (cur < target) cur = Math.min(target, cur + 0.1F);
            else if (cur > target) cur = Math.max(target, cur - 0.1F);
            progress.put(id, cur);
        }

        // mantener espacio 8 s junto a un jugador tirado para cargarlo
        if (!active || mc.screen != null || downed.contains(me.getUUID()) || isCarrying(me.getUUID())
                || me.getVehicle() != null) {
            holdReset();
            return;
        }
        Player best = null;
        double bestD = 3.0D * 3.0D;
        for (Player o : mc.level.players()) {
            if (o == me || !downed.contains(o.getUUID()) || carried.containsKey(o.getUUID())) continue;
            double d = o.distanceToSqr(me);
            if (d < bestD) {
                bestD = d;
                best = o;
            }
        }
        if (best != null && mc.options.keyJump.isDown()) {
            if (!best.getUUID().equals(holdTarget)) {
                holdTarget = best.getUUID();
                holdTicks = 0;
            }
            holdTicks++;
            if (holdTicks >= HOLD_TICKS) {
                Net.CHANNEL.sendToServer(new FinalPackets.Action(FinalPackets.Action.CARRY, holdTarget));
                holdReset();
            }
        } else {
            holdReset();
        }
    }
}
