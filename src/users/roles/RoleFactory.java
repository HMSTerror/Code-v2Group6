package users.roles;

import users.Permission;

import java.util.Set;

/**
 * Centralized factory for common roles.
 */
public class RoleFactory {

    public static Role createAdminRole() {
        return new Role("ADMIN", Set.of(
                Permission.CONTROL_ALL_DEVICES,
                Permission.EDIT_GROUPS,
                Permission.EDIT_SCENES,
                Permission.EXECUTE_SCENES,
                Permission.VIEW_STATUS,
                Permission.REGISTER_DEVICE,
                Permission.DEREGISTER_DEVICE
        ));
    }

    public static Role createParentRole() {
        return new Role("PARENT", Set.of(
                Permission.CONTROL_LIGHTS,
                Permission.CONTROL_THERMOSTATS,
                Permission.CONTROL_LOCKS,
                Permission.EXECUTE_SCENES,
                Permission.VIEW_STATUS,
                Permission.EDIT_SCENES
        ));
    }

    public static Role createChildRole() {
        return new Role("CHILD", Set.of(
                Permission.CONTROL_LIGHTS,
                Permission.VIEW_STATUS
        ));
    }
}