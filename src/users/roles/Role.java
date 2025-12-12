package users.roles;

import users.Permission;

import java.util.Collections;
import java.util.Set;

/**
 * Role is a simple holder of name and permission set.
 */
public class Role {
    private final String name;
    private final Set<Permission> permissions;

    Role(String name, Set<Permission> permissions) {
        this.name = name;
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public String getName() { return name; }

    public boolean hasPermission(Permission p) {
        return permissions.contains(p);
    }

    public boolean canControlDevice(String deviceType) {
        // If a user have Permission of CONTROL_ALL_DEVICES , they can control any device.
        if (permissions.contains(Permission.CONTROL_ALL_DEVICES)) {
            return true;
        }

        switch (deviceType.toUpperCase()) {
            case "LIGHT":
                return permissions.contains(Permission.CONTROL_LIGHTS);
            case "THERMOSTAT":
                return permissions.contains(Permission.CONTROL_THERMOSTATS);
            case "LOCK":
                return permissions.contains(Permission.CONTROL_LOCKS);
            default:
                // can't control unknown device types
                return false;
        }
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    @Override
    public String toString() {
        return "Role{" + name + ", perms=" + permissions + "}";
    }
}
