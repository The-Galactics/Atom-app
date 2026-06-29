package com.atom.application.port.in;

import com.atom.domain.model.Chat;
import com.atom.domain.model.Message;

import java.util.List;

public interface ChatPortIn {
    Chat createChat(String userId, String title);
    Chat getChat(String chatId);
    Chat updateChatTitle(String chatId, String newTitle);
    void archiveChat(String chatId);
    void deleteChat(String chatId);
    List<Chat> getUserChats(String userId);
    void addMessage(String chatId, Message message);
    Message getLastMessage(String chatId);
}
