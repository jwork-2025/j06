package com.gameengine.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

public class NioServer implements Runnable {
    private final int port;
    private volatile boolean running = true;
    private Thread thread;

    public NioServer(int port) { this.port = port; }

    public void start() {
        if (thread != null) return;
        thread = new Thread(this, "nio-server");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() { running = false; if (thread!=null) thread.interrupt(); }

    @Override public void run() {
        try (Selector selector = Selector.open();
             ServerSocketChannel ssc = ServerSocketChannel.open()) {
            ssc.configureBlocking(false);
            ssc.bind(new InetSocketAddress(port));
            ssc.register(selector, SelectionKey.OP_ACCEPT);

            ByteBuffer buf = ByteBuffer.allocate(1024);
            while (running) {
                selector.select(250);
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next(); it.remove();
                    if (!key.isValid()) continue;
                    if (key.isAcceptable()) {
                        SocketChannel ch = ssc.accept();
                        if (ch != null) {
                            ch.configureBlocking(false);
                            ch.register(selector, SelectionKey.OP_READ);
                            NetState.clientConnected();
                        }
                    } else if (key.isReadable()) {
                        SocketChannel ch = (SocketChannel) key.channel();
                        buf.clear();
                        int n = ch.read(buf);
                        if (n <= 0) { key.cancel(); ch.close(); continue; }
                        buf.flip();
                        String s = new String(buf.array(), 0, buf.limit());
                        if (s.startsWith("JOIN:")) {
                            ByteBuffer out = ByteBuffer.wrap("JOIN-ACK\n".getBytes());
                            while (out.hasRemaining()) ch.write(out);
                        } else if (s.startsWith("INPUT:")) {
                            try {
                                String payload = s.substring(6).trim();
                                String[] kv = payload.split(",");
                                float vx = Float.parseFloat(kv[0]);
                                float vy = Float.parseFloat(kv[1]);
                                NetState.setP2Velocity(vx, vy);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }
}


