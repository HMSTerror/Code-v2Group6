package users;

import users.roles.Role;

import java.util.Objects;

/**
 * Simple User model with role-based permission checks.
 */
public class User {
    private final String username;
    private final Role role;

    public User(String username, Role role) {
        this.username = Objects.requireNonNull(username);
        this.role = Objects.requireNonNull(role);
    }

    public String getUsername() { return username; }
    public Role getRole() { return role; }

    public boolean hasPermission(Permission p) {
        return role.hasPermission(p);
    }

    @Override
    public String toString() {
        return "User{" + username + ", " + role.getName() + "}";
    }
}