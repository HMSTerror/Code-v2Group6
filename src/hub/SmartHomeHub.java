package hub;

import devices.Device;
import devices.DeviceState;
import exceptions.ExecutionException;
import exceptions.ValidationException;
import users.Permission;
import users.Role;
import users.User;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central hub: manages devices, groups, scenes and enforces permissions.
 * Scene execution supports a simple rollback strategy when failure occurs.
 */
public class SmartHomeHub {

    private final Map<String, Device> devices = new ConcurrentHashMap<>();
    private final Map<String, DeviceGroup> groups = new ConcurrentHashMap<>();
    private final Map<String, Scene> scenes = new ConcurrentHashMap<>();

    public SmartHomeHub() {}

    // Device management (requires REGISTER_DEVICE / DEREGISTER_DEVICE)
    public void registerDevice(User user, Device device) {
        requirePermission(user, Permission.REGISTER_DEVICE);
        devices.put(device.getName(), device);
    }

    public void deregisterDevice(User user, String deviceName) {
        requirePermission(user, Permission.DEREGISTER_DEVICE);
        devices.remove(deviceName);
    }

    public Optional<Device> getDevice(String name) {
        return Optional.ofNullable(devices.get(name));
    }

    public Collection<Device> getAllDevices() { return devices.values(); }

    // Groups
    public void createGroup(User user, String groupName, Collection<String> deviceNames) {
        requirePermission(user, Permission.EDIT_GROUPS);
        DeviceGroup g = new DeviceGroup(groupName);
        deviceNames.forEach(g::addDevice);
        groups.put(groupName, g);
    }

    public void deleteGroup(User user, String groupName) {
        requirePermission(user, Permission.EDIT_GROUPS);
        groups.remove(groupName);
    }

    public Optional<DeviceGroup> getGroup(String name) {
        return Optional.ofNullable(groups.get(name));
    }

    // Scenes
    public void createScene(User user, Scene scene) {
        requirePermission(user, Permission.EDIT_SCENES);
        scenes.put(scene.getName(), scene);
    }

    public void deleteScene(User user, String sceneName) {
        requirePermission(user, Permission.EDIT_SCENES);
        scenes.remove(sceneName);
    }

    public Optional<Scene> getScene(String name) {
        return Optional.ofNullable(scenes.get(name));
    }

    /**
     * Execute scene: simple atomic-like behavior:
     *  - Save original states of involved devices
     *  - Apply each action in sequence
     *  - On ValidationException, rollback to saved original states and throw ExecutionException
     */
    public void executeScene(User user, String sceneName) throws ExecutionException {
        requirePermission(user, Permission.EXECUTE_SCENES);

        Scene scene = scenes.get(sceneName);
        if (scene == null) throw new ExecutionException("Scene not found: " + sceneName);

        // Collect devices involved
        List<SceneAction> actions = scene.getActions();
        Map<Device, DeviceState> originalStates = new LinkedHashMap<>();
        for (SceneAction a : actions) {
            Device d = devices.get(a.getDeviceName());
            if (d != null) {
                originalStates.put(d, d.getState());
            }
        }

        List<String> failed = new ArrayList<>();

        for (SceneAction a : actions) {
            Device d = devices.get(a.getDeviceName());
            if (d == null) {
                failed.add("Device not found: " + a.getDeviceName());
                // rollback and fail
                rollback(originalStates);
                throw new ExecutionException("Scene execution failed: device missing " + a.getDeviceName());
            }

            if (!user.getRole().canControlDevice(d.getType())) {
                String errorMsg = String.format("Permission denied: User %s cannot control device type %s.",
                        user.getUsername(), d.getType());
                rollback(originalStates);
                throw new ExecutionException(errorMsg);
            }

            try {
                d.applyState(a.getTargetState());
            } catch (ValidationException ve) {
                failed.add(d.getName() + ": " + ve.getMessage());
                // rollback on first failure to keep atomic semantics
                rollback(originalStates);
                throw new ExecutionException("Scene execution failed on device " + d.getName() + ": " + ve.getMessage(), ve);
            } catch (Exception ex) {
                failed.add(d.getName() + ": " + ex.getMessage());
                rollback(originalStates);
                throw new ExecutionException("Unexpected error executing scene for device " + d.getName(), ex);
            }
        }

        // success
        if (!failed.isEmpty()) {
            // shouldn't happen due to immediate rollback, but keep for completeness
            System.out.println("Scene executed with issues: " + failed);
        } else {
            System.out.println("Scene '" + sceneName + "' executed successfully.");
        }
    }

    // Apply action to group
    public void applyToGroup(User user, String groupName, DeviceState state) throws ExecutionException {
        // need CONTROL_ALL_DEVICES permission to apply arbitrary state to a group

        DeviceGroup g = groups.get(groupName);
        if (g == null) throw new ExecutionException("Group not found: " + groupName);

        // state tracking for rollback
        Map<Device, DeviceState> originalStates = new LinkedHashMap<>();
        List<String> failedDevices = new ArrayList<>();

        try {
            for (String deviceName : g.getDeviceNames()) {
                Device d = devices.get(deviceName);
                if (d == null) {
                    System.err.println("Warning: Device in group not found: " + deviceName + ". Skipping.");
                    continue;
                }

                // check permission for each device type
                if (!user.getRole().canControlDevice(d.getType())) {
                    System.err.println(String.format("Warning: User %s lacks permission to control %s (%s). Skipping.",
                            user.getUsername(), d.getName(), d.getType()));
                    continue;
                }

                // keep original state for rollback
                if (!originalStates.containsKey(d)) {
                    originalStates.put(d, d.getState());
                }

                // apply state
                d.applyState(state);

            }
            System.out.println("Group '" + groupName + "' applied state successfully.");

        } catch (ValidationException ve) {
            // check for validation errors
            System.err.println("Group operation failed on device: " + ve.getMessage());
            rollback(originalStates);
            throw new ExecutionException("Group operation failed and rolled back.", ve);
        } catch (Exception ex) {
            // other unexpected errors
            System.err.println("Unexpected error during group operation: " + ex.getMessage());
            rollback(originalStates);
            throw new ExecutionException("Unexpected error during group operation and rolled back.", ex);
        }
    }

    private void rollback(Map<Device, DeviceState> originalStates) {
        for (Map.Entry<Device, DeviceState> e : originalStates.entrySet()) {
            try {
                e.getKey().restoreState(e.getValue());
            } catch (Exception ex) {
                System.err.println("Rollback failed on device " + e.getKey().getName() + ": " + ex.getMessage());
            }
        }
    }


    private void requirePermission(User user, Permission p) {
        if (user == null) throw new SecurityException("User required");
        if (!user.hasPermission(p)) {
            throw new SecurityException("User " + user.getUsername() + " lacks permission: " + p);
        }
    }
}