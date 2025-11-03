package com.gameengine.example;

import com.gameengine.core.GameEngine;
import com.gameengine.graphics.IRenderer;
import com.gameengine.input.InputManager;
import com.gameengine.scene.Scene;
import com.gameengine.net.NioClient;

public class NetworkConnectScene extends Scene {
    private final GameEngine engine;
    private IRenderer renderer;
    private InputManager input;
    private StringBuilder ip = new StringBuilder("127.0.0.1");
    private String status = "";

    public NetworkConnectScene(GameEngine engine) {
        super("NetworkConnect");
        this.engine = engine;
    }

    @Override public void initialize() {
        super.initialize();
        this.renderer = engine.getRenderer();
        this.input = engine.getInputManager();
        status = "INPUT SERVER IP, ENTER TO CONNECT";
    }

    @Override public void update(float dt) {
        super.update(dt);
        if (input.isKeyJustPressed(27)) { engine.setScene(new MenuScene(engine, "MainMenu")); return; }
        for (int k = 32; k <= 90; k++) {
            if (input.isKeyJustPressed(k)) {
                char c = (char)k;
                if ((c >= '0' && c <= '9') || c == '.' ) ip.append(c);
            }
        }
        if (input.isKeyJustPressed(8) && ip.length() > 0) { ip.setLength(ip.length() - 1); }
        if (input.isKeyJustPressed(10) || input.isKeyJustPressed(257)) {
            String host = ip.toString();
            NioClient client = new NioClient();
            if (client.connect(host, 7777) && client.join("Player2")) {
                status = "CONNECTED: " + host;
                client.startInputLoop(engine.getInputManager());
                client.startReceiveLoop();
                engine.setScene(new GameScene(engine, GameScene.Mode.CLIENT));
                return;
            } else {
                status = "CONNECT FAILED";
            }
        }
    }

    @Override public void render() {
        renderer.drawRect(0,0, renderer.getWidth(), renderer.getHeight(), 0.1f,0.1f,0.15f,1);
        String title = "NETWORK CONNECT";
        renderer.drawText(60, 80, title, 1,1,1,1);
        renderer.drawText(60, 140, "IP: " + ip, 0.9f,0.9f,0.9f,1);
        renderer.drawText(60, 180, status, 0.9f,0.8f,0.2f,1);
        renderer.drawText(60, 220, "ENTER CONNECT, ESC BACK", 0.7f,0.7f,0.7f,1);
    }
}


