package com.gameengine.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class NioClient {
    private SocketChannel channel;
    private volatile boolean loopStarted = false;

    public boolean connect(String host, int port) {
        try {
            channel = SocketChannel.open();
            channel.configureBlocking(true);
            channel.connect(new InetSocketAddress(host, port));
            System.out.println("[Client] Connected to " + host + ":" + port);
            return true;
        } catch (IOException e) {
            System.err.println("[Client] Failed to connect to " + host + ":" + port + " - " + e.getMessage());
            return false;
        }
    }

    public boolean join(String name) {
        if (channel == null) return false;
        try {
            ByteBuffer out = ByteBuffer.wrap(("JOIN:" + name + "\n").getBytes());
            while (out.hasRemaining()) channel.write(out);
            
            // 读取响应，可能需要多次读取才能得到 JOIN-ACK
            ByteBuffer in = ByteBuffer.allocate(4096);
            StringBuilder response = new StringBuilder();
            long startTime = System.currentTimeMillis();
            
            while (System.currentTimeMillis() - startTime < 3000) { // 3秒超时
                in.clear();
                int n = channel.read(in);
                if (n > 0) {
                    response.append(new String(in.array(), 0, n));
                    String s = response.toString();
                    if (s.contains("JOIN-ACK")) {
                        System.out.println("[Client] Join successful as " + name);
                        return true;
                    }
                }
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
            
            System.err.println("[Client] Join timeout, no JOIN-ACK received");
            System.err.println("[Client] Received: " + response.toString().substring(0, Math.min(200, response.length())));
            return false;
        } catch (IOException e) {
            System.err.println("[Client] Join error: " + e.getMessage());
            return false;
        }
    }

    public void startInputLoop(final com.gameengine.input.InputManager input) {
        if (channel == null || loopStarted) return;
        loopStarted = true;
        Thread t = new Thread(() -> {
            try {
                while (channel.isOpen()) {
                    float dx = 0, dy = 0;
                    if (input.isKeyPressed(87) || input.isKeyPressed(38) || input.isKeyPressed(265)) dy -= 1; // W/Up
                    if (input.isKeyPressed(83) || input.isKeyPressed(40) || input.isKeyPressed(264)) dy += 1; // S/Down
                    if (input.isKeyPressed(65) || input.isKeyPressed(37) || input.isKeyPressed(263)) dx -= 1; // A/Left
                    if (input.isKeyPressed(68) || input.isKeyPressed(39) || input.isKeyPressed(262)) dx += 1; // D/Right
                    float speed = 200f;
                    float vx = dx == 0 && dy == 0 ? 0 : (float)(dx / Math.max(1e-6, Math.hypot(dx, dy)) * speed);
                    float vy = dx == 0 && dy == 0 ? 0 : (float)(dy / Math.max(1e-6, Math.hypot(dx, dy)) * speed);
                    String line = "INPUT:" + vx + "," + vy + "\n";
                    ByteBuffer out = ByteBuffer.wrap(line.getBytes());
                    while (out.hasRemaining()) channel.write(out);
                    try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                }
            } catch (Exception ignored) {
            }
        }, "client-input-loop");
        t.setDaemon(true);
        t.start();
    }

    public void startReceiveLoop() {
        if (channel == null) return;
        System.out.println("[Client] Starting receive loop...");
        Thread t = new Thread(() -> {
            ByteBuffer in = ByteBuffer.allocate(4096);
            StringBuilder sb = new StringBuilder();
            int frameCount = 0;
            try {
                while (channel.isOpen()) {
                    in.clear();
                    int n = channel.read(in);
                    if (n <= 0) { try { Thread.sleep(50);} catch (InterruptedException ignored) {} continue; }
                    sb.append(new String(in.array(), 0, n));
                    int idx;
                    while ((idx = sb.indexOf("\n")) >= 0) {
                        String line = sb.substring(0, idx).trim();
                        sb.delete(0, idx + 1);
                        if (!line.isEmpty()) {
                            NetworkBuffer.Keyframe kf = NetworkBuffer.parseJsonLine(line);
                            if (kf != null) {
                                NetworkBuffer.push(kf);
                                frameCount++;
                                if (frameCount % 50 == 0) {
                                    System.out.println("[Client] Received " + frameCount + " keyframes, entities in last: " + kf.entities.size());
                                }
                            } else {
                                com.gameengine.net.NetState.updateMirrorFromState(line); // 兼容旧文本
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[Client] Receive loop error: " + e.getMessage());
            }
        }, "client-recv-loop");
        t.setDaemon(true);
        t.start();
    }
}


