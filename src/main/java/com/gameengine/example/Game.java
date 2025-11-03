package com.gameengine.example;

import com.gameengine.core.GameEngine;
import com.gameengine.graphics.RenderBackend;
import com.gameengine.net.NioServer;

public class Game {
    public static void main(String[] args) {
        NioServer server = new NioServer(7777);
        server.start();
        GameEngine engine = null;
        try {
            engine = new GameEngine(1024, 768, "游戏引擎", RenderBackend.GPU);
            // Server 直接进入 GameScene
            GameScene gameScene = new GameScene(engine);
            engine.setScene(gameScene);
            engine.run();
        } catch (Exception e) {
        } finally {
            if (engine != null) {
                engine.cleanup();
            }
        }
    }
}


