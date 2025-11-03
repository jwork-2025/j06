package com.gameengine.graphics;

import com.gameengine.input.InputManager;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import java.util.HashMap;
import java.util.Map;

public class GPURenderer implements IRenderer {
    private int width;
    private int height;
    private String title;
    private InputManager inputManager;
    private boolean initialized;
    private long window;
    private Map<Character, Integer> charTextures;
    private Font font;
    private int fontSize;
    private boolean texturesPreloaded;
    private static final String PRELOAD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|;:,.<>?/~` ";

    public GPURenderer(int width, int height, String title) {
        this.width = width;
        this.height = height;
        this.title = title;
        this.inputManager = InputManager.getInstance();
        this.initialized = false;
        this.window = 0;
        this.charTextures = new HashMap<>();
        this.font = new Font(Font.MONOSPACED, Font.BOLD, 32);
        this.fontSize = 32;
        this.texturesPreloaded = false;

        initialize();
    }
    
    private void initialize() {
        try {
            System.setProperty("java.awt.headless", "true");
            GLFWErrorCallback.createPrint(System.err).set();
            
            if (!GLFW.glfwInit()) {
                throw new RuntimeException("无法初始化GLFW");
            }
            
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE);
            GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 2);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 1);
            
            window = GLFW.glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
            if (window == MemoryUtil.NULL) {
                throw new RuntimeException("无法创建GLFW窗口");
            }
            
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer pWidth = stack.mallocInt(1);
                IntBuffer pHeight = stack.mallocInt(1);
                
                GLFW.glfwGetWindowSize(window, pWidth, pHeight);
                
                org.lwjgl.glfw.GLFWVidMode vidmode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
                
                if (vidmode != null) {
                    GLFW.glfwSetWindowPos(
                        window,
                        (vidmode.width() - pWidth.get(0)) / 2,
                        (vidmode.height() - pHeight.get(0)) / 2
                    );
                }
            }
            
            setupInput();
            
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            GLFW.glfwSwapInterval(1);
            
            GLFW.glfwShowWindow(window);
            
            GL11.glViewport(0, 0, width, height);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            
            // 再次确保上下文有效后再查询版本
            GLFW.glfwMakeContextCurrent(window);
            String glVersion = GL11.glGetString(GL11.GL_VERSION);
            String glRenderer = GL11.glGetString(GL11.GL_RENDERER);
            
            
            
            initialized = true;
            
            int[] maxTex = new int[1];
            maxTex[0] = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);

            int testTex = createTestTexture();
            
            // 仅在上下文确认有效后再预加载纹理
            preloadTextures();
        } catch (Exception e) {
            throw new RuntimeException("GPU渲染器初始化失败: " + e.getMessage(), e);
        }
    }
    
    private void setupInput() {
        GLFW.glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (action == GLFW.GLFW_PRESS) {
                inputManager.onKeyPressed(key);
            } else if (action == GLFW.GLFW_RELEASE) {
                inputManager.onKeyReleased(key);
            }
        });
        
        GLFW.glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
            if (action == GLFW.GLFW_PRESS) {
                inputManager.onMousePressed(button);
            } else if (action == GLFW.GLFW_RELEASE) {
                inputManager.onMouseReleased(button);
            }
        });
        
        GLFW.glfwSetCursorPosCallback(window, (window, xpos, ypos) -> {
            inputManager.onMouseMoved((int)xpos, (int)ypos);
        });
    }
    
    @Override
    public void beginFrame() {
        if (!initialized) return;
        
        GLFW.glfwMakeContextCurrent(window);
        
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, width, height, 0, -1, 1);
        
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        
        GL11.glClearColor(0.2f, 0.2f, 0.2f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_COLOR_MATERIAL);
        
    }
    
    @Override
    public void endFrame() {
        if (!initialized) return;
        GLFW.glfwSwapBuffers(window);
    }
    
    @Override
    public void drawRect(float x, float y, float w, float h, float r, float g, float b, float a) {
        if (!initialized) return;
        
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
        
    }
    
    @Override
    public void drawCircle(float x, float y, float radius, int segments, float r, float g, float b, float a) {
        if (!initialized) return;
        
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(x, y);
        
        for (int i = 0; i <= segments; i++) {
            float angle = (float) (i * 2.0 * Math.PI / segments);
            float px = x + (float) (radius * Math.cos(angle));
            float py = y + (float) (radius * Math.sin(angle));
            GL11.glVertex2f(px, py);
        }
        GL11.glEnd();
    }
    
    @Override
    public void drawLine(float x1, float y1, float x2, float y2, float r, float g, float b, float a) {
        if (!initialized) return;
        
        GL11.glLineWidth(2.5f);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glEnd();
    }
    
    @Override
    public void drawText(float x, float y, String text, float r, float g, float b, float a) {
        if (!initialized || text == null || text.isEmpty()) return;
        
        if (!texturesPreloaded) {
            preloadTextures();
        }
        
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        
        float currentX = x;
        float charHeight = fontSize;
        float charWidth = fontSize * 0.6f;
        float spacing = 1.0f;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                currentX += charWidth * 0.5f;
                continue;
            }
            
            int textureId = getCharTexture(c);
            if (textureId > 0) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
                GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
                GL11.glColor4f(r, g, b, a);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glTexCoord2f(0.0f, 0.0f);
                GL11.glVertex2f(currentX, y);
                GL11.glTexCoord2f(1.0f, 0.0f);
                GL11.glVertex2f(currentX + charWidth, y);
                GL11.glTexCoord2f(1.0f, 1.0f);
                GL11.glVertex2f(currentX + charWidth, y + charHeight);
                GL11.glTexCoord2f(0.0f, 1.0f);
                GL11.glVertex2f(currentX, y + charHeight);
                GL11.glEnd();
            }
            
            currentX += charWidth + spacing;
        }
        
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }
    
    private void preloadTextures() {
        if (!initialized || texturesPreloaded) return;
        
        GLFW.glfwMakeContextCurrent(window);
        
        int loaded = 0;
        int failed = 0;
        
        for (int i = 0; i < PRELOAD_CHARS.length(); i++) {
            char c = PRELOAD_CHARS.charAt(i);
            if (c == ' ') continue;
            
            if (!charTextures.containsKey(c)) {
                int error = GL11.glGetError();
                while (error != GL11.GL_NO_ERROR) {
                    error = GL11.glGetError();
                }
                
                int textureId = createCharTexture(c);
                if (textureId > 0) {
                    charTextures.put(c, textureId);
                    loaded++;
                } else {
                    failed++;
                }
            }
        }
        
        texturesPreloaded = true;
    }
    
    private int getCharTexture(char c) {
        if (charTextures.containsKey(c)) {
            return charTextures.get(c);
        }
        
        if (!texturesPreloaded) {
            preloadTextures();
            if (charTextures.containsKey(c)) {
                return charTextures.get(c);
            }
        }
        
        if (!initialized) {
            return 0;
        }
        
        GLFW.glfwMakeContextCurrent(window);
        
        int error = GL11.glGetError();
        while (error != GL11.GL_NO_ERROR) {
            error = GL11.glGetError();
        }
        
        int textureId = createCharTexture(c);
        if (textureId > 0) {
            charTextures.put(c, textureId);
        }
        return textureId;
    }
    
    private int createCharTexture(char c) {
        try {
            BufferedImage img = new BufferedImage(fontSize, fontSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = img.createGraphics();
            
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setComposite(AlphaComposite.Clear);
            g2d.fillRect(0, 0, fontSize, fontSize);
            
            g2d.setComposite(AlphaComposite.SrcOver);
            g2d.setColor(new Color(255, 255, 255, 255));
            g2d.setFont(font);
            FontMetrics fm = g2d.getFontMetrics();
            int x = (fontSize - fm.charWidth(c)) / 2;
            int y = (fontSize - fm.getHeight()) / 2 + fm.getAscent();
            g2d.drawString(String.valueOf(c), x, y);
            g2d.dispose();
            
            int[] pixels = new int[fontSize * fontSize];
            img.getRGB(0, 0, fontSize, fontSize, pixels, 0, fontSize);
            
            ByteBuffer buffer = ByteBuffer.allocateDirect(fontSize * fontSize * 4);
            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                buffer.put((byte) ((pixel >> 16) & 0xFF));
                buffer.put((byte) ((pixel >> 8) & 0xFF));
                buffer.put((byte) (pixel & 0xFF));
                buffer.put((byte) ((pixel >> 24) & 0xFF));
            }
            buffer.flip();
            
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            
            int prevError = GL11.glGetError();
            while (prevError != GL11.GL_NO_ERROR) { prevError = GL11.glGetError(); }
            
            int textureId;
            try {
                IntBuffer ids = BufferUtils.createIntBuffer(1);
                GL11.glGenTextures(ids);
                textureId = ids.get(0);
            } catch (Throwable t) {
                textureId = GL11.glGenTextures();
            }
            
            if (textureId <= 0) { return 0; }
            
            int genError = GL11.glGetError();
            if (genError != GL11.GL_NO_ERROR) { GL11.glDeleteTextures(textureId); return 0; }
            
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            
            int bindError = GL11.glGetError();
            if (bindError != GL11.GL_NO_ERROR) { GL11.glDeleteTextures(textureId); return 0; }
            
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, fontSize, fontSize, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            
            int texError = GL11.glGetError();
            if (texError != GL11.GL_NO_ERROR) { GL11.glDeleteTextures(textureId); return 0; }
            
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            
            return textureId;
        } catch (Exception e) { return 0; }
    }

    private int createTestTexture() {
        try {
            ByteBuffer buf = BufferUtils.createByteBuffer(4 * 4);
            for (int i = 0; i < 4; i++) {
                buf.put((byte)255).put((byte)255).put((byte)255).put((byte)255);
            }
            buf.flip();
            
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            IntBuffer ids = BufferUtils.createIntBuffer(1);
            GL11.glGenTextures(ids);
            int id = ids.get(0);
            if (id <= 0) {
                id = GL11.glGenTextures();
            }
            if (id <= 0) {
                return 0;
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            return id;
        } catch (Throwable t) { return 0; }
    }
    
    @Override
    public boolean shouldClose() {
        if (!initialized) return false;
        return GLFW.glfwWindowShouldClose(window);
    }
    
    @Override
    public void pollEvents() {
        if (initialized && window != MemoryUtil.NULL) {
            GLFW.glfwPollEvents();
            if (GLFW.glfwWindowShouldClose(window)) {
                GLFW.glfwSetWindowShouldClose(window, true);
            }
        }
    }
    
    @Override
    public void cleanup() {
        for (Integer textureId : charTextures.values()) {
            if (textureId > 0) {
                GL11.glDeleteTextures(textureId);
            }
        }
        charTextures.clear();
        
        if (window != MemoryUtil.NULL) {
            GLFW.glfwDestroyWindow(window);
            window = MemoryUtil.NULL;
        }
        GLFW.glfwTerminate();
        org.lwjgl.glfw.GLFWErrorCallback prev = GLFW.glfwSetErrorCallback(null);
        if (prev != null) {
            prev.free();
        }
    }
    
    @Override
    public int getWidth() {
        return width;
    }
    
    @Override
    public int getHeight() {
        return height;
    }
    
    @Override
    public String getTitle() {
        return title;
    }
}

