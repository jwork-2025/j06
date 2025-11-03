package com.gameengine.net;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class NetState {
    private static final AtomicInteger clientCount = new AtomicInteger(0);
    private static volatile float p2vx = 0f;
    private static volatile float p2vy = 0f;
    private static volatile String lastState = null; // server构建的状态文本
    private static final ConcurrentHashMap<String, float[]> clientMirror = new ConcurrentHashMap<>();
    private NetState() {}
    public static void clientConnected() { clientCount.incrementAndGet(); }
    public static int getClientCount() { return clientCount.get(); }
    public static boolean hasClient() { return clientCount.get() > 0; }
    public static void setP2Velocity(float vx, float vy) { p2vx = vx; p2vy = vy; }
    public static float getP2Vx() { return p2vx; }
    public static float getP2Vy() { return p2vy; }

    // server 侧设置当前状态（文本行，形如 STATE:id,x,y;id2,x,y）
    public static void setLastState(String s) { lastState = s; }
    public static String getLastState() { return lastState; }

    // client 侧接收状态并更新镜像
    public static void updateMirrorFromState(String line) {
        if (line == null) return;
        if (!line.startsWith("STATE:")) return;
        String payload = line.substring(6).trim();
        if (payload.isEmpty()) return;
        String[] ents = payload.split(";");
        for (String e : ents) {
            String[] parts = e.split(",");
            if (parts.length < 3) continue;
            String id = parts[0];
            try {
                float x = Float.parseFloat(parts[1]);
                float y = Float.parseFloat(parts[2]);
                clientMirror.put(id, new float[]{x,y});
            } catch (Exception ignored) {}
        }
    }

    public static java.util.Map<String, float[]> getMirrorSnapshot() {
        return new java.util.HashMap<>(clientMirror);
    }
}


