package com.gameengine.net;

public final class Messages {
    private Messages() {}
    public static class Join { public final String name; public Join(String n){this.name=n;} }
    public static class JoinAck { public final boolean ok; public JoinAck(boolean ok){this.ok=ok;} }
    public static class Text { public final String text; public Text(String t){this.text=t;} }
}


