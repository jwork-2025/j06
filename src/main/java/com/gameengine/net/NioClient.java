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
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean join(String name) {
        if (channel == null) return false;
        try {
            ByteBuffer out = ByteBuffer.wrap(("JOIN:" + name + "\n").getBytes());
            while (out.hasRemaining()) channel.write(out);
            ByteBuffer in = ByteBuffer.allocate(1024);
            int n = channel.read(in);
            if (n <= 0) return false;
            String s = new String(in.array(), 0, n);
            return s.contains("JOIN-ACK");
        } catch (IOException e) {
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
}


