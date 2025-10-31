package com.example.weatherforecast;

public class ChatMessage {
    private String text;
    private MessageType type;

    public enum MessageType {
        SENT, RECEIVED
    }

    public ChatMessage(String text, MessageType type) {
        this.text = text;
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public MessageType getType() {
        return type;
    }
}
