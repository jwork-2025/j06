package com.gameengine.example;

import com.gameengine.core.GameEngine;
import com.gameengine.graphics.RenderBackend;
import com.gameengine.net.NioClient;

public class ClientLauncher {
    public static void main(String[] args) {
        String host = args != null && args.length > 0 ? args[0] : "127.0.0.1";
        GameEngine engine = new GameEngine(1024, 768, "Client", RenderBackend.GPU);
        NioClient client = new NioClient();
        if (client.connect(host, 7777) && client.join("Player2")) {
            client.startInputLoop(engine.getInputManager());
            client.startReceiveLoop();
        }
        engine.setScene(new GameScene(engine, GameScene.Mode.CLIENT));
        engine.run();
        engine.cleanup();
    }
}


