package com.gameengine.net;

import java.util.concurrent.atomic.AtomicInteger;

public final class NetState {
    private static final AtomicInteger clientCount = new AtomicInteger(0);
    private static volatile float p2vx = 0f;
    private static volatile float p2vy = 0f;
    private NetState() {}
    public static void clientConnected() { clientCount.incrementAndGet(); }
    public static int getClientCount() { return clientCount.get(); }
    public static boolean hasClient() { return clientCount.get() > 0; }
    public static void setP2Velocity(float vx, float vy) { p2vx = vx; p2vy = vy; }
    public static float getP2Vx() { return p2vx; }
    public static float getP2Vy() { return p2vy; }
}


