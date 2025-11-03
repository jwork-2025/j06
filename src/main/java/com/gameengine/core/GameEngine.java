package com.gameengine.core;

import com.gameengine.graphics.Renderer;
import com.gameengine.input.InputManager;
import com.gameengine.scene.Scene;
import javax.swing.Timer;

public class GameEngine {
    private Renderer renderer;
    private InputManager inputManager;
    private Scene currentScene;
    private boolean running;
    private float targetFPS;
    private float deltaTime;
    private long lastTime;
    private String title;
    private Timer gameTimer;
    
    public GameEngine(int width, int height, String title) {
        this.title = title;
        this.renderer = new Renderer(width, height, title);
        this.inputManager = InputManager.getInstance();
        this.running = false;
        this.targetFPS = 60.0f;
        this.deltaTime = 0.0f;
        this.lastTime = System.nanoTime();
    }
    
    public boolean initialize() {
        return true;
    }
    
    public void run() {
        if (!initialize()) {
            System.err.println("游戏引擎初始化失败");
            return;
        }
        
        running = true;
        
        if (currentScene != null) {
            currentScene.initialize();
        }
        
        gameTimer = new Timer((int) (1000 / targetFPS), e -> {
            if (running) {
                update();
                render();
            }
        });
        
        gameTimer.start();
    }
    
    private void update() {
        long currentTime = System.nanoTime();
        deltaTime = (currentTime - lastTime) / 1_000_000_000.0f;
        lastTime = currentTime;
        
        inputManager.update();
        
        if (currentScene != null) {
            currentScene.update(deltaTime);
        }
        
        renderer.pollEvents();
        
        if (inputManager.isKeyPressed(27)) {
            running = false;
            gameTimer.stop();
            renderer.cleanup();
        }
        
        if (renderer.shouldClose()) {
            running = false;
            gameTimer.stop();
        }
    }
    
    private void render() {
        renderer.beginFrame();
        
        if (currentScene != null) {
            currentScene.render();
        }
        
        renderer.endFrame();
    }
    
    public void setScene(Scene scene) {
        this.currentScene = scene;
        if (scene != null && running) {
            scene.initialize();
        }
    }
    
    public Scene getCurrentScene() {
        return currentScene;
    }
    
    public void stop() {
        running = false;
        if (gameTimer != null) {
            gameTimer.stop();
        }
    }
    
    private void cleanup() {
        if (currentScene != null) {
            currentScene.clear();
        }
        renderer.cleanup();
    }
    
    public Renderer getRenderer() {
        return renderer;
    }
    
    public InputManager getInputManager() {
        return inputManager;
    }
    
    public float getDeltaTime() {
        return deltaTime;
    }
    
    public void setTargetFPS(float fps) {
        this.targetFPS = fps;
        if (gameTimer != null) {
            gameTimer.setDelay((int) (1000 / fps));
        }
    }
    
    public float getTargetFPS() {
        return targetFPS;
    }
    
    public boolean isRunning() {
        return running;
    }
}
