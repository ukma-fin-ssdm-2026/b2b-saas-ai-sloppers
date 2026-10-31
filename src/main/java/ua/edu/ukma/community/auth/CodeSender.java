package ua.edu.ukma.community.auth;


public interface CodeSender {

    void send(String email, String code);
}
