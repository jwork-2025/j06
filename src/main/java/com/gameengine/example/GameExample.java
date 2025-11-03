package com.gameengine.example;

import com.gameengine.components.*;
import com.gameengine.core.GameObject;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameLogic;
import com.gameengine.graphics.Renderer;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

import java.util.Random;

public class GameExample {
    public static void main(String[] args) {
        System.out.println("启动游戏引擎...");
        
        try {
            GameEngine engine = new GameEngine(1920, 1080, "游戏引擎");
            
            Scene gameScene = new Scene("GameScene") {
                private Renderer renderer;
                private Random random;
                private float time;
                private GameLogic gameLogic;
                
                @Override
                public void initialize() {
                    super.initialize();
                    this.renderer = engine.getRenderer();
                    this.random = new Random();
                    this.time = 0;
                    this.gameLogic = new GameLogic(this);
                    this.gameLogic.setGameEngine(engine);
                    
                    createPlayer();
                    createAIPlayers();
                    createDecorations();
                }
                
                @Override
                public void update(float deltaTime) {
                    super.update(deltaTime);
                    time += deltaTime;
                    
                    gameLogic.handlePlayerInput(deltaTime);
                    gameLogic.handleAIPlayerMovement(deltaTime);
                    gameLogic.handleAIPlayerAvoidance(deltaTime);
                    gameLogic.updatePhysics();
                    gameLogic.checkCollisions();
                    
                    if (gameLogic.isGameOver()) {
                        return;
                    }
                    
                    if (time >= 2.0f) {
                        createAIPlayer();
                        time = 0;
                    }
                }
                
                @Override
                public void render() {
                    renderer.drawRect(0, 0, 1920, 1080, 0.1f, 0.1f, 0.2f, 1.0f);
                    
                    super.render();
                    
                    if (gameLogic.isGameOver()) {
                        renderer.drawRect(760, 490, 400, 100, 0.0f, 0.0f, 0.0f, 0.7f);
                        renderer.drawText(960, 540, "GAME OVER", 1.0f, 1.0f, 1.0f, 1.0f);
                    }
                }
                
                private void createPlayer() {
                    GameObject player = new GameObject("Player") {
                        private Vector2 basePosition;
                        
                        @Override
                        public void update(float deltaTime) {
                            super.update(deltaTime);
                            updateComponents(deltaTime);
                            updateBodyParts();
                        }
                        
                        @Override
                        public void render() {
                            renderBodyParts();
                        }
                        
                        private void updateBodyParts() {
                            TransformComponent transform = getComponent(TransformComponent.class);
                            if (transform != null) {
                                basePosition = transform.getPosition();
                            }
                        }
                        
                        private void renderBodyParts() {
                            if (basePosition == null) return;
                            
                            renderer.drawRect(
                                basePosition.x - 8, basePosition.y - 10, 16, 20,
                                1.0f, 0.0f, 0.0f, 1.0f
                            );
                            
                            renderer.drawRect(
                                basePosition.x - 6, basePosition.y - 22, 12, 12,
                                1.0f, 0.5f, 0.0f, 1.0f
                            );
                            
                            renderer.drawRect(
                                basePosition.x - 13, basePosition.y - 5, 6, 12,
                                1.0f, 0.8f, 0.0f, 1.0f
                            );
                            
                            renderer.drawRect(
                                basePosition.x + 7, basePosition.y - 5, 6, 12,
                                0.0f, 1.0f, 0.0f, 1.0f
                            );
                        }
                    };
                    
                    player.addComponent(new TransformComponent(new Vector2(960, 540)));
                    
                    PhysicsComponent physics = player.addComponent(new PhysicsComponent(1.0f));
                    physics.setFriction(0.95f);
                    
                    addGameObject(player);
                }
                
                private void createAIPlayers() {
                    for (int i = 0; i < 30; i++) {
                        createAIPlayer();
                    }
                }
                
                private void createAIPlayer() {
                    GameObject aiPlayer = new GameObject("AIPlayer") {
                        @Override
                        public void update(float deltaTime) {
                            super.update(deltaTime);
                            updateComponents(deltaTime);
                        }
                        
                        @Override
                        public void render() {
                            renderComponents();
                        }
                    };
                    
                    Vector2 position;
                    do {
                        position = new Vector2(
                            random.nextFloat() * 1920,
                            random.nextFloat() * 1080
                        );
                    } while (position.distance(new Vector2(960, 540)) < 100);
                    
                    aiPlayer.addComponent(new TransformComponent(position));
                    
                    RenderComponent render = aiPlayer.addComponent(new RenderComponent(
                        RenderComponent.RenderType.RECTANGLE,
                        new Vector2(20, 20),
                        new RenderComponent.Color(0.0f, 0.8f, 1.0f, 1.0f)
                    ));
                    render.setRenderer(renderer);
                    
                    PhysicsComponent physics = aiPlayer.addComponent(new PhysicsComponent(0.5f));
                    physics.setVelocity(new Vector2(
                        (random.nextFloat() - 0.5f) * 150,
                        (random.nextFloat() - 0.5f) * 150
                    ));
                    physics.setFriction(0.98f);
                    
                    addGameObject(aiPlayer);
                }
                
                private void createDecorations() {
                    for (int i = 0; i < 5; i++) {
                        createDecoration();
                    }
                }
                
                private void createDecoration() {
                    GameObject decoration = new GameObject("Decoration") {
                        @Override
                        public void update(float deltaTime) {
                            super.update(deltaTime);
                            updateComponents(deltaTime);
                        }
                        
                        @Override
                        public void render() {
                            renderComponents();
                        }
                    };
                    
                    Vector2 position = new Vector2(
                        random.nextFloat() * 1920,
                        random.nextFloat() * 1080
                    );
                    
                    decoration.addComponent(new TransformComponent(position));
                    
                    RenderComponent render = decoration.addComponent(new RenderComponent(
                        RenderComponent.RenderType.CIRCLE,
                        new Vector2(5, 5),
                        new RenderComponent.Color(0.5f, 0.5f, 1.0f, 0.8f)
                    ));
                    render.setRenderer(renderer);
                    
                    addGameObject(decoration);
                }
            };
            
            engine.setScene(gameScene);
            engine.run();
            
        } catch (Exception e) {
            System.err.println("游戏运行出错: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("游戏结束");
    }
}
