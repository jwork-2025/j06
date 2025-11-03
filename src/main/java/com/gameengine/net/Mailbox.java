package com.gameengine.net;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Mailbox {
    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    public void post(Object msg) { queue.offer(msg); }
    public Object take() throws InterruptedException { return queue.take(); }
}


