package com.gameengine.example;

import com.gameengine.components.PhysicsComponent;
import com.gameengine.components.RenderComponent;
import com.gameengine.components.TransformComponent;
import com.gameengine.core.GameEngine;
import com.gameengine.core.GameLogic;
import com.gameengine.core.GameObject;
import com.gameengine.core.ParticleSystem;
import com.gameengine.graphics.IRenderer;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

import java.util.*;

public class GameScene extends Scene {
    public enum Mode { SERVER, CLIENT }
    private final GameEngine engine;
    private final Mode mode;
    private IRenderer renderer;
    private Random random;
    private float time;
    private GameLogic gameLogic;
    private ParticleSystem playerParticles;
    private List<ParticleSystem> collisionParticles;
    private Map<GameObject, ParticleSystem> aiPlayerParticles;
    private Map<GameObject, ParticleSystem> mirrorParticles;
    private boolean waitingReturn;
    private float waitInputTimer;
    private float freezeTimer;
    private final float inputCooldown = 0.25f;
    private final float freezeDelay = 0.20f;
    private boolean networkPlayerSpawned = false;

    public GameScene(GameEngine engine) { this(engine, Mode.SERVER); }

    public GameScene(GameEngine engine, Mode mode) {
        super("GameScene");
        this.engine = engine;
        this.mode = mode;
    }

    @Override
    public void initialize() {
        super.initialize();
        this.renderer = engine.getRenderer();
        this.random = new Random();
        this.time = 0;
        this.gameLogic = new GameLogic(this);
        this.gameLogic.setGameEngine(engine);
        this.waitingReturn = false;
        this.waitInputTimer = 0f;
        this.freezeTimer = 0f;

        if (mode == Mode.SERVER) {
            createPlayer();
            createAIPlayers();
            createDecorations();
        } else {
            // CLIENT 模式：对象由网络同步创建；此处暂不本地生成
        }

        // 若已有网络客户端连接，生成第二玩家占位
        if (com.gameengine.net.NetState.hasClient()) {
            createNetworkPlayer();
            networkPlayerSpawned = true;
        }

        collisionParticles = new ArrayList<>();
        aiPlayerParticles = new HashMap<>();
        mirrorParticles = new HashMap<>();

        playerParticles = new ParticleSystem(renderer, new Vector2(renderer.getWidth() / 2.0f, renderer.getHeight() / 2.0f));
        playerParticles.setActive(true);
        
    }

    @Override
    public void update(float deltaTime) {
        time += deltaTime;

        // CLIENT 优先应用镜像，再统一调用组件更新，确保可视化读取到本帧位置
        if (mode == Mode.CLIENT) {
            java.util.Map<String, float[]> snap = com.gameengine.net.NetworkBuffer.sample();
            for (java.util.Map.Entry<String, float[]> e : snap.entrySet()) {
                String id = e.getKey();
                float[] xy = e.getValue();
                GameObject obj = findOrCreateMirror(id);
                TransformComponent tc = obj.getComponent(TransformComponent.class);
                if (tc != null) tc.setPosition(new Vector2(xy[0], xy[1]));

                // 为镜像对象维护轻量粒子
                ParticleSystem ps = mirrorParticles.get(obj);
                if (ps == null) {
                    ps = new ParticleSystem(renderer, tc != null ? tc.getPosition() : new Vector2(0,0), ParticleSystem.Config.light());
                    ps.setActive(true);
                    mirrorParticles.put(obj, ps);
                } else {
                    if (tc != null) ps.setPosition(tc.getPosition());
                }
                ps.update(deltaTime);
            }
        }

        super.update(deltaTime);

        if (mode == Mode.SERVER) {
            gameLogic.handlePlayerInput(deltaTime);
            gameLogic.handleAIPlayerMovement(deltaTime);
            gameLogic.handleAIPlayerAvoidance(deltaTime);
        }

        boolean wasGameOver = gameLogic.isGameOver();
        if (mode == Mode.SERVER) {
            gameLogic.checkCollisions();
        }

        if (gameLogic.isGameOver() && !wasGameOver) {
            GameObject player = gameLogic.getUserPlayer();
            if (player != null) {
                TransformComponent transform = player.getComponent(TransformComponent.class);
                if (transform != null) {
                    ParticleSystem.Config cfg = new ParticleSystem.Config();
                    cfg.initialCount = 0;
                    cfg.spawnRate = 9999f;
                    cfg.opacityMultiplier = 1.0f;
                    cfg.minRenderSize = 3.0f;
                    cfg.burstSpeedMin = 250f;
                    cfg.burstSpeedMax = 520f;
                    cfg.burstLifeMin = 0.5f;
                    cfg.burstLifeMax = 1.2f;
                    cfg.burstSizeMin = 18f;
                    cfg.burstSizeMax = 42f;
                    cfg.burstR = 1.0f;
                    cfg.burstGMin = 0.0f;
                    cfg.burstGMax = 0.05f;
                    cfg.burstB = 0.0f;
                    ParticleSystem explosion = new ParticleSystem(renderer, transform.getPosition(), cfg);
                    explosion.burst(180);
                    collisionParticles.add(explosion);
                    waitingReturn = true;
                    waitInputTimer = 0f;
                    freezeTimer = 0f;
                }
            }
        }

        updateParticles(deltaTime);

        if (waitingReturn) {
            waitInputTimer += deltaTime;
            freezeTimer += deltaTime;
        }

        if (waitingReturn && waitInputTimer >= inputCooldown && (engine.getInputManager().isAnyKeyJustPressed() || engine.getInputManager().isMouseButtonJustPressed(0))) {
            MenuScene menu = new MenuScene(engine, "MainMenu");
            engine.setScene(menu);
            return;
        }

        if (mode == Mode.SERVER && !gameLogic.isGameOver() && time >= 1.0f) {
            if (!networkPlayerSpawned && com.gameengine.net.NetState.hasClient()) {
                createNetworkPlayer();
                networkPlayerSpawned = true;
            }
            createAIPlayer();
            time = 0;
        }

        // SERVER 广播 JSON 关键帧
        if (mode == Mode.SERVER) {
            StringBuilder js = new StringBuilder();
            js.append('{').append("\"type\":\"kf\",")
              .append("\"t\":").append(System.currentTimeMillis()/1000.0).append(',')
              .append("\"entities\":[");
            boolean first = true; int idx = 0;
            for (GameObject obj : getGameObjects()) {
                TransformComponent tc = obj.getComponent(TransformComponent.class);
                if (tc == null) continue;
                Vector2 p = tc.getPosition();
                if (!first) js.append(',');
                String id = obj.getName();
                if (!id.contains("#")) id = id + "#" + (idx++);
                js.append('{')
                  .append("\"id\":\"").append(id).append("\",")
                  .append("\"x\":").append((int)p.x).append(',')
                  .append("\"y\":").append((int)p.y)
                  .append('}');
                first = false;
            }
            js.append(']').append('}');
            com.gameengine.net.NetState.setLastKeyframeJson(js.toString());
        }

        
    }

    private void updateParticles(float deltaTime) {
        boolean freeze = waitingReturn && freezeTimer >= freezeDelay;

        if (playerParticles != null && !freeze) {
            GameObject player = gameLogic.getUserPlayer();
            if (player != null) {
                TransformComponent transform = player.getComponent(TransformComponent.class);
                if (transform != null) {
                    Vector2 playerPos = transform.getPosition();
                    playerParticles.setPosition(playerPos);
                }
            }
            playerParticles.update(deltaTime);
        }

        List<GameObject> aiPlayers = gameLogic.getAIPlayers();
        if (!freeze) {
            for (GameObject aiPlayer : aiPlayers) {
                if (aiPlayer != null && aiPlayer.isActive()) {
                    ParticleSystem particles = aiPlayerParticles.get(aiPlayer);
                    if (particles == null) {
                        TransformComponent transform = aiPlayer.getComponent(TransformComponent.class);
                        if (transform != null) {
                            particles = new ParticleSystem(renderer, transform.getPosition(), ParticleSystem.Config.light());
                            particles.setActive(true);
                            aiPlayerParticles.put(aiPlayer, particles);
                        }
                    }
                    if (particles != null) {
                        TransformComponent transform = aiPlayer.getComponent(TransformComponent.class);
                        if (transform != null) {
                            particles.setPosition(transform.getPosition());
                        }
                        particles.update(deltaTime);
                    }
                }
            }
        }

        List<GameObject> toRemove = new ArrayList<>();
        for (Map.Entry<GameObject, ParticleSystem> entry : aiPlayerParticles.entrySet()) {
            if (!entry.getKey().isActive() || !aiPlayers.contains(entry.getKey())) {
                toRemove.add(entry.getKey());
            }
        }
        for (GameObject removed : toRemove) {
            aiPlayerParticles.remove(removed);
        }

        for (int i = collisionParticles.size() - 1; i >= 0; i--) {
            ParticleSystem ps = collisionParticles.get(i);
            if (ps != null) {
                if (!freeze) {
                    ps.update(deltaTime);
                }
            }
        }
    }

    @Override
    public void render() {
        renderer.drawRect(0, 0, renderer.getWidth(), renderer.getHeight(), 0.1f, 0.1f, 0.2f, 1.0f);

        super.render();

        renderParticles();

        if (gameLogic.isGameOver()) {
            float cx = renderer.getWidth() / 2.0f;
            float cy = renderer.getHeight() / 2.0f;
            renderer.drawRect(0, 0, renderer.getWidth(), renderer.getHeight(), 0.0f, 0.0f, 0.0f, 0.35f);
            renderer.drawRect(cx - 200, cy - 60, 400, 120, 0.0f, 0.0f, 0.0f, 0.7f);
            renderer.drawText(cx - 100, cy - 10, "GAME OVER", 1.0f, 1.0f, 1.0f, 1.0f);
            renderer.drawText(cx - 180, cy + 30, "PRESS ANY KEY TO RETURN", 0.8f, 0.8f, 0.8f, 1.0f);
        }
    }

    private void renderParticles() {
        if (playerParticles != null) {
            int count = playerParticles.getParticleCount();
            if (count > 0) {
                playerParticles.render();
            }
        }

        for (ParticleSystem ps : aiPlayerParticles.values()) {
            if (ps != null && ps.getParticleCount() > 0) {
                ps.render();
            }
        }

        for (ParticleSystem ps : collisionParticles) {
            if (ps != null && ps.getParticleCount() > 0) {
                ps.render();
            }
        }

        // CLIENT: 渲染镜像对象的粒子
        if (mirrorParticles != null && !mirrorParticles.isEmpty()) {
            for (ParticleSystem ps : mirrorParticles.values()) {
                if (ps != null && ps.getParticleCount() > 0) {
                    ps.render();
                }
            }
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

        float px = random.nextFloat() * renderer.getWidth();
        float py = random.nextFloat() * renderer.getHeight();
        // 留出20像素安全边距
        px = Math.max(20, Math.min(renderer.getWidth() - 20, px));
        py = Math.max(20, Math.min(renderer.getHeight() - 20, py));
        player.addComponent(new TransformComponent(new Vector2(px, py)));

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
                random.nextFloat() * renderer.getWidth(),
                random.nextFloat() * renderer.getHeight()
            );
        } while (position.distance(new Vector2(renderer.getWidth() / 2.0f, renderer.getHeight() / 2.0f)) < 100);

        aiPlayer.addComponent(new TransformComponent(position));
        // 使用工厂统一外观
        RenderComponent rc = aiPlayer.addComponent(new RenderComponent(
            RenderComponent.RenderType.RECTANGLE,
            new Vector2(20, 20),
            new RenderComponent.Color(0.0f, 0.8f, 1.0f, 1.0f)
        ));
        rc.setRenderer(renderer);

        PhysicsComponent physics = aiPlayer.addComponent(new PhysicsComponent(0.5f));
        physics.setVelocity(new Vector2(
            (random.nextFloat() - 0.5f) * 150,
            (random.nextFloat() - 0.5f) * 150
        ));
        physics.setFriction(0.98f);

        addGameObject(aiPlayer);
    }

    private void createNetworkPlayer() {
        GameObject p2 = new GameObject("Player2") {
            @Override
            public void update(float deltaTime) {
                super.update(deltaTime);
                updateComponents(deltaTime);
                PhysicsComponent pc = getComponent(PhysicsComponent.class);
                if (pc != null) {
                    float vx = com.gameengine.net.NetState.getP2Vx();
                    float vy = com.gameengine.net.NetState.getP2Vy();
                    pc.setVelocity(new Vector2(vx, vy));
                }
            }

            @Override
            public void render() {
                renderComponents();
            }
        };
        p2.addComponent(new TransformComponent(new Vector2(renderer.getWidth() / 2.0f + 40, renderer.getHeight() / 2.0f)));
        RenderComponent rc = p2.addComponent(new RenderComponent(
            RenderComponent.RenderType.RECTANGLE,
            new Vector2(20,20),
            new RenderComponent.Color(0.2f, 1.0f, 0.2f, 1.0f)
        ));
        rc.setRenderer(renderer);
        PhysicsComponent physics = p2.addComponent(new PhysicsComponent(1.0f));
        physics.setFriction(0.95f);
        addGameObject(p2);
    }

    // CLIENT: 按 id 查找或创建镜像对象
    private GameObject findOrCreateMirror(String id) {
        for (GameObject o : getGameObjects()) {
            if (id.equals(o.getName())) return o;
        }
        String base = id;
        int hash = id.indexOf('#');
        if (hash >= 0) base = id.substring(0, hash);
        GameObject obj;
        if (base.equalsIgnoreCase("AIPlayer")) {
            obj = EntityFactory.createAIVisual(renderer, 20, 20, 0.0f, 0.8f, 1.0f, 1.0f);
        } else if (base.equalsIgnoreCase("Player") || base.toLowerCase().startsWith("player")) {
            obj = EntityFactory.createPlayerVisual(renderer);
            // 确保有 Transform 以便定位
            if (obj.getComponent(TransformComponent.class) == null) {
                obj.addComponent(new TransformComponent(new Vector2(0, 0)));
            }
        } else {
            // 其他：默认小方块占位
            obj = new GameObject(id) {
                @Override public void update(float dt) { super.update(dt); updateComponents(dt); }
                @Override public void render() { renderComponents(); }
            };
            obj.addComponent(new TransformComponent(new Vector2(0,0)));
            RenderComponent rc = obj.addComponent(new RenderComponent(
                RenderComponent.RenderType.RECTANGLE,
                new Vector2(10,10),
                new RenderComponent.Color(0.9f,0.9f,0.2f,1f)
            ));
            rc.setRenderer(renderer);
        }
        obj.setName(id);
        addGameObject(obj);
        return obj;
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
            random.nextFloat() * renderer.getWidth(),
            random.nextFloat() * renderer.getHeight()
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

    @Override
    public void clear() {
        if (gameLogic != null) {
            gameLogic.cleanup();
        }
        if (playerParticles != null) {
            playerParticles.clear();
        }
        if (collisionParticles != null) {
            for (ParticleSystem ps : collisionParticles) {
                if (ps != null) ps.clear();
            }
            collisionParticles.clear();
        }
        super.clear();
    }
}


