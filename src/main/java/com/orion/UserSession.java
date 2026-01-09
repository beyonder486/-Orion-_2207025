package com.orion;

public class UserSession {
    private static UserSession instance;
    private User currentUser;

    private UserSession() {
    }

    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public void logout() {
        this.currentUser = null;
    }

    public String getUsername() {
        return currentUser != null ? currentUser.getUsername() : null;
    }

    public int getUserId() {
        return currentUser != null ? currentUser.getId() : -1;
    }
}
