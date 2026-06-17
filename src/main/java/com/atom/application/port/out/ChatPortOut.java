package com.atom.application.port.out;

import com.atom.domain.model.Chat;
import com.atom.domain.model.Message;

import java.util.List;
import java.util.Optional;

public interface ChatPortOut {
    Chat save(Chat chat);
    Optional<Chat> findById(String chatId);
    List<Chat> findByUserId(String userId);
    void delete(Chat chat);
    Message saveMessage(Message message);
    Optional<Message> findMessageById(String messageId);
    List<Message> findMessagesByChatId(String chatId);
    void deleteMessage(Message message);
}
