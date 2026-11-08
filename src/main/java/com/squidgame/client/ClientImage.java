package com.squidgame.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.squidgame.FinalPackets;
import com.squidgame.Net;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/** Carpeta squidgame_assets y la imagen del ganador. */
public class ClientImage {
    public static final String FOLDER = "squidgame_assets";
    private static final ResourceLocation TEX = new ResourceLocation("squidgame", "dynamic/winner");
    private static final long FADE_MS = 400L;

    private static final class Incoming {
        FinalPackets.ImgBegin begin;
        byte[][] chunks;
        int got;
    }

    private static final Map<UUID, Incoming> INCOMING = new HashMap<>();
    private static DynamicTexture dyn;
    private static int imgW, imgH;
    private static long shownAt = 0L;
    private static long showUntil = 0L; // 0 = oculta, Long.MAX_VALUE = hasta hide

    public static Path folder() {
        return FMLPaths.GAMEDIR.get().resolve(FOLDER);
    }

    public static void ensureFolder() {
        try {
            Files.createDirectories(folder());
        } catch (IOException ignored) {
        }
    }

    // ------------------------------------------------------------ subir (quien ejecuta el comando)

    public static void onRequest(String id) {
        Thread t = new Thread(() -> upload(id), "SquidGame-ImageUpload");
        t.setDaemon(true);
        t.start();
    }

    private static Path find(String id) {
        ensureFolder();
        try (Stream<Path> s = Files.list(folder())) {
            return s.filter(Files::isRegularFile).filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                int dot = n.lastIndexOf('.');
                if (dot < 0) return false;
                String base = n.substring(0, dot);
                String ext = n.substring(dot + 1);
                return base.equals(id.toLowerCase(Locale.ROOT))
                        && (ext.equals("png") || ext.equals("jpg") || ext.equals("jpeg"));
            }).findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static void upload(String id) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Path f = find(id);
            if (f == null) {
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.literal(
                                "No encontré la imagen '" + id + "' (.png o .jpg) en la carpeta " + FOLDER), false);
                    }
                });
                return;
            }
            byte[] data = Files.readAllBytes(f);
            if (data.length > 4 * 1024 * 1024) {
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.literal("La imagen pesa más de 4 MB."), false);
                    }
                });
                return;
            }
            String name = f.getFileName().toString().toLowerCase(Locale.ROOT);
            String ext = name.substring(name.lastIndexOf('.') + 1);
            UUID tid = UUID.randomUUID();
            int size = 28000;
            int chunks = (data.length + size - 1) / size;
            Net.CHANNEL.sendToServer(new FinalPackets.ImgBegin(tid, ext, data.length, chunks, 0));
            for (int i = 0; i < chunks; i++) {
                int from = i * size;
                int to = Math.min(data.length, from + size);
                Net.CHANNEL.sendToServer(new FinalPackets.ImgChunk(tid, i, Arrays.copyOfRange(data, from, to)));
                if (i % 10 == 9) Thread.sleep(2);
            }
        } catch (Throwable e) {
            LogUtils.getLogger().warn("No se pudo enviar la imagen del ganador", e);
        }
    }

    // ------------------------------------------------------------ recibir (todos)

    public static void onBegin(FinalPackets.ImgBegin m) {
        if (m.chunks <= 0 || m.chunks > 400) return;
        INCOMING.clear();
        Incoming in = new Incoming();
        in.begin = m;
        in.chunks = new byte[m.chunks][];
        INCOMING.put(m.tid, in);
    }

    public static void onChunk(FinalPackets.ImgChunk m) {
        Incoming in = INCOMING.get(m.tid);
        if (in == null || m.index < 0 || m.index >= in.chunks.length) return;
        if (in.chunks[m.index] == null) {
            in.chunks[m.index] = m.data;
            in.got++;
        }
        if (in.got < in.chunks.length) return;
        INCOMING.remove(m.tid);
        int total = 0;
        for (byte[] c : in.chunks) total += c.length;
        byte[] all = new byte[total];
        int pos = 0;
        for (byte[] c : in.chunks) {
            System.arraycopy(c, 0, all, pos, c.length);
            pos += c.length;
        }
        show(all, in.begin.ext, in.begin.seconds);
    }

    private static void show(byte[] data, String ext, int seconds) {
        try {
            byte[] png = data;
            if (!ext.equals("png")) {
                BufferedImage bi = ImageIO.read(new ByteArrayInputStream(data));
                if (bi == null) return;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(bi, "png", out);
                png = out.toByteArray();
            }
            NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
            Minecraft mc = Minecraft.getInstance();
            final int w = img.getWidth();
            final int h = img.getHeight();
            mc.execute(() -> {
                if (dyn != null) mc.getTextureManager().release(TEX);
                dyn = new DynamicTexture(img);
                mc.getTextureManager().register(TEX, dyn);
                imgW = w;
                imgH = h;
                shownAt = System.currentTimeMillis();
                showUntil = seconds <= 0 ? Long.MAX_VALUE : shownAt + seconds * 1000L;
            });
        } catch (Throwable e) {
            LogUtils.getLogger().warn("No se pudo mostrar la imagen del ganador", e);
        }
    }

    public static void hide() {
        showUntil = 0L;
    }

    // ------------------------------------------------------------ dibujo

    public static void render(GuiGraphics g, int width, int height) {
        if (showUntil == 0L || dyn == null) return;
        long now = System.currentTimeMillis();
        if (now >= showUntil) {
            showUntil = 0L;
            return;
        }
        float alpha = 1.0F;
        long since = now - shownAt;
        if (since < FADE_MS) alpha = since / (float) FADE_MS;
        if (showUntil != Long.MAX_VALUE && showUntil - now < FADE_MS) alpha = Math.min(alpha, (showUntil - now) / (float) FADE_MS);

        double scale = Math.min(width * 0.9D / imgW, height * 0.9D / imgH);
        int dw = Math.max(1, (int) (imgW * scale));
        int dh = Math.max(1, (int) (imgH * scale));
        int x = (width - dw) / 2;
        int y = (height - dh) / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(TEX, x, y, dw, dh, 0.0F, 0.0F, imgW, imgH, imgW, imgH);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
